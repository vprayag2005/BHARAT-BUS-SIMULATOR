"""Offline reproduction of MannuthyOllurRouteBuilder's MDR connector centreline.

Mirrors the C# pipeline exactly (ToWorld projection, centripetal Catmull-Rom
densification, AppendConnected and FitOllurConnectorBends) so the generated
geometry can be inspected numerically without launching Unity.
"""
import json
import math
import sys

ROUTE_FILE = "data/processed/mannuthy_ollur_corridor_utm.json"
ORIGIN_UTM_X = 632883.2
ORIGIN_UTM_Y = 1163650.18
ORIGIN_ELEVATION = 12.0
CORRIDOR_SCALE = 0.20
ALONG_ROUTE_SCALE = 0.20
STALL = (666.56, -1115.08)


def load():
    with open(ROUTE_FILE) as handle:
        return json.load(handle)


class Projection:
    def __init__(self, data):
        start = data["route_start"]
        fixed = data["fixed_ollur"]
        self.start = (start["x"], start["y"])
        dx = fixed["x"] - start["x"]
        dy = fixed["y"] - start["y"]
        length = math.hypot(dx, dy)
        self.axis = (dx / length, dy / length)
        self.side = (-self.axis[1], self.axis[0])
        self.anchor = ((start["x"] - ORIGIN_UTM_X) * CORRIDOR_SCALE,
                       (start["y"] - ORIGIN_UTM_Y) * CORRIDOR_SCALE)
        self.total = sum(step["distance_m"] for step in data["steps"])

    def to_world(self, point, elevation):
        dx = point["x"] - self.start[0]
        dy = point["y"] - self.start[1]
        along = (dx * self.axis[0] + dy * self.axis[1]) * ALONG_ROUTE_SCALE
        lateral = dx * self.side[0] + dy * self.side[1]
        x = self.anchor[0] + self.axis[0] * along + self.side[0] * lateral
        z = self.anchor[1] + self.axis[1] * along + self.side[1] * lateral
        y = max(0.05, (elevation - ORIGIN_ELEVATION) * 0.35 + 0.015)
        return (x, y, z)

    def elevation_at(self, point, data):
        best = float("inf")
        station = 0.0
        cumulative = 0.0
        for step in data["steps"]:
            pts = step.get("points") or []
            if not pts:
                cumulative += step["distance_m"]
                continue
            for i, p in enumerate(pts):
                dist = (p["x"] - point["x"]) ** 2 + (p["y"] - point["y"]) ** 2
                if dist < best:
                    best = dist
                    station = cumulative + step["distance_m"] * i / max(1, len(pts) - 1)
            cumulative += step["distance_m"]
        return 16.0 + (14.5 - 16.0) * (station / max(1.0, self.total))


def dist(a, b):
    return math.sqrt((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2)


def centripetal_catmull_rom(p0, p1, p2, p3, u):
    t0 = 0.0
    t1 = t0 + max(0.0001, math.sqrt(dist(p0, p1)))
    t2 = t1 + max(0.0001, math.sqrt(dist(p1, p2)))
    t3 = t2 + max(0.0001, math.sqrt(dist(p2, p3)))
    t = t1 + (t2 - t1) * min(1.0, max(0.0, u))

    def lerp_unclamped(a, b, f):
        return tuple(a[i] + (b[i] - a[i]) * f for i in range(3))

    a1 = lerp_unclamped(p0, p1, (t - t0) / (t1 - t0))
    a2 = lerp_unclamped(p1, p2, (t - t1) / (t2 - t1))
    a3 = lerp_unclamped(p2, p3, (t - t2) / (t3 - t2))
    b1 = lerp_unclamped(a1, a2, (t - t0) / (t2 - t0))
    b2 = lerp_unclamped(a2, a3, (t - t1) / (t3 - t1))
    return lerp_unclamped(b1, b2, (t - t1) / (t2 - t1))


def smooth_waypoints(source, subdivisions=4):
    if not source:
        return []
    if len(source) < 3 or subdivisions < 1:
        return list(source)
    subdivisions = max(2, min(8, subdivisions))
    result = []
    for i in range(len(source) - 1):
        p0 = source[max(0, i - 1)]
        p1 = source[i]
        p2 = source[i + 1]
        p3 = source[min(len(source) - 1, i + 2)]
        for j in range(subdivisions):
            point = centripetal_catmull_rom(p0, p1, p2, p3, j / float(subdivisions))
            if not result or dist(result[-1], point) > 0.01:
                result.append(point)
    result.append(source[-1])
    return result


def remove_ollur_transition_kink(points, data):
    result = [dict(p) for p in points]
    transition = next((s for s in data["steps"] if s["index"] == 2), None)
    if not transition or len(transition.get("points") or []) != 3 or len(result) < 3:
        return result
    kink = transition["points"][1]
    for i, point in enumerate(result):
        if abs(point["x"] - kink["x"]) < 0.05 and abs(point["y"] - kink["y"]) < 0.05:
            if 0 < i < len(result) - 1:
                result.pop(i)
            break
    return result


def convert_line(points, station_range, projection):
    result = []
    if not points:
        return result
    length = max(0.01, station_range[1] - station_range[0])
    for i, point in enumerate(points):
        station = station_range[0] + length * i / max(1, len(points) - 1)
        elevation = 16.0 + (14.5 - 16.0) * (station / projection.total)
        world = projection.to_world(point, elevation)
        if not result or dist(result[-1], world) > 0.05:
            result.append(world)
    return smooth_waypoints(result)


def append_connected(target, line):
    for point in line:
        if not target or dist(target[-1], point) > 0.05:
            target.append(point)


def plan_radius(a, b, c):
    ab = (b[0] - a[0], b[2] - a[2])
    bc = (c[0] - b[0], c[2] - b[2])
    ac = (c[0] - a[0], c[2] - a[2])
    double_area = abs(ab[0] * ac[1] - ab[1] * ac[0])
    if double_area < 0.0001:
        return float("inf")
    mag_ab = math.hypot(*ab)
    mag_bc = math.hypot(*bc)
    mag_ac = math.hypot(*ac)
    return mag_ab * mag_bc * mag_ac / (2.0 * double_area)


def nearest_index(points, target, start_index=0):
    if not points:
        return 0
    result = max(0, min(start_index, len(points) - 1))
    best = float("inf")
    for i in range(result, len(points)):
        d = (points[i][0] - target[0]) ** 2 + (points[i][2] - target[2]) ** 2
        if d < best:
            best = d
            result = i
    return result


def distance_to_polyline(point, line):
    best = float("inf")
    for i in range(1, len(line)):
        a = (line[i - 1][0], line[i - 1][2])
        b = (line[i][0], line[i][2])
        p = (point[0], point[2])
        span = (b[0] - a[0], b[1] - a[1])
        sq = span[0] ** 2 + span[1] ** 2
        t = 0.0 if sq < 0.0001 else max(0.0, min(1.0, ((p[0] - a[0]) * span[0] + (p[1] - a[1]) * span[1]) / sq))
        best = min(best, math.dist(p, (a[0] + span[0] * t, a[1] + span[1] * t)))
    return best


def build_connector(data, projection):
    steps = data["steps"]
    cumulative = 0.0
    ranges = {}
    for step in steps:
        ranges[step["index"]] = (cumulative, cumulative + step["distance_m"])
        cumulative += step["distance_m"]
    connector = []
    for step in steps:
        if step["kind"] == "highway" and step["distance_m"] > 100:
            continue
        points = remove_ollur_transition_kink(step["points"], data) if step["index"] == 2 else step["points"]
        line = convert_line(points, ranges[step["index"]], projection)
        if len(line) < 2:
            continue
        if step["index"] == 0:
            continue
        append_connected(connector, line)
    return connector


def fit_connector(connector, data, projection, verbose=True):
    bend_step = next((s for s in data["steps"] if s["index"] == 3), None)
    next_step = next((s for s in data["steps"] if s["index"] == 4), None)
    if not connector or len(connector) < 4 or not bend_step or len(bend_step["points"]) < 3 \
            or not next_step or len(next_step["points"]) < 2:
        return connector, None
    entry = projection.to_world(bend_step["points"][2],
                                projection.elevation_at(bend_step["points"][2], data))
    exit_ = projection.to_world(next_step["points"][1],
                                projection.elevation_at(next_step["points"][1], data))
    entry_index = nearest_index(connector, entry)
    exit_index = nearest_index(connector, exit_, entry_index + 1)
    entry_error = distance_to_polyline(entry, [connector[entry_index]])
    if verbose:
        print(f"entry anchor -> connector index {entry_index} "
              f"(plan error {math.hypot(connector[entry_index][0]-entry[0], connector[entry_index][2]-entry[2]):.2f} m)")
        print(f"exit  anchor -> connector index {exit_index} "
              f"(plan error {math.hypot(connector[exit_index][0]-exit_[0], connector[exit_index][2]-exit_[2]):.2f} m)")
        print(f"span replaced: {exit_index - entry_index + 1} of {len(connector)} stations, "
              f"entry-exit plan distance "
              f"{math.hypot(connector[entry_index][0]-connector[exit_index][0], connector[entry_index][2]-connector[exit_index][2]):.2f} m")

    controls = [connector[entry_index - 1], connector[entry_index], connector[exit_index], connector[exit_index + 1]]
    smoothed = smooth_waypoints(controls, 8)
    original_smooth = list(smoothed)
    for _ in range(2500):
        changed = False
        for i in range(1, len(smoothed) - 1):
            if plan_radius(smoothed[i - 1], smoothed[i], smoothed[i + 1]) >= 11.0:
                continue
            mid = tuple((smoothed[i - 1][k] + smoothed[i + 1][k]) * 0.5 for k in range(3))
            smoothed[i] = tuple(smoothed[i][k] + (mid[k] - smoothed[i][k]) * 0.16 for k in range(3))
            changed = True
        if not changed:
            break
    fitted = list(connector[:entry_index])
    append_connected(fitted, smoothed)
    for i in range(exit_index + 1, len(connector)):
        append_connected(fitted, [connector[i]])

    max_dev = 0.0
    for i in range(entry_index, exit_index + 1):
        max_dev = max(max_dev, distance_to_polyline(connector[i], smoothed))
    min_radius = min(plan_radius(smoothed[i - 1], smoothed[i], smoothed[i + 1])
                     for i in range(1, len(smoothed) - 1))
    curve_offset = max(dist(original_smooth[i], smoothed[i]) for i in range(len(smoothed)))
    info = dict(entry_index=entry_index, exit_index=exit_index, smoothed=smoothed,
                max_deviation=max_dev + curve_offset, polyline_deviation=max_dev,
                curve_offset=curve_offset, min_radius=min_radius)
    if verbose:
        print(f"smoothed span: {len(smoothed)} stations, plan length "
              f"{sum(dist(smoothed[i-1], smoothed[i]) for i in range(1, len(smoothed))):.2f} m")
        print(f"minimum radius {min_radius:.2f} m; polyline deviation {max_dev:.1f} m; "
              f"curve offset {curve_offset:.1f} m; builder metric {max_dev + curve_offset:.1f} m")
    return fitted, info


def report_span(label, points, indices):
    print(f"\n--- {label} ---")
    print(f"stations {len(points)}, plan length "
          f"{sum(math.dist((points[i-1][0], points[i-1][2]), (points[i][0], points[i][2])) for i in range(1, len(points))):.1f} m")
    radii = [(i, plan_radius(points[i - 1], points[i], points[i + 1])) for i in range(1, len(points) - 1)]
    worst = sorted(radii, key=lambda r: r[1])[:5]
    print("tightest radii: " + ", ".join(f"#{i}={r:.2f}m" for i, r in worst))
    headings = []
    for i in range(1, len(points)):
        dx = points[i][0] - points[i - 1][0]
        dz = points[i][2] - points[i - 1][2]
        headings.append(math.degrees(math.atan2(dz, dx)))
    turns = []
    for i in range(1, len(headings)):
        delta = (headings[i] - headings[i - 1] + 180.0) % 360.0 - 180.0
        turns.append(abs(delta))
    print(f"max per-station heading change {max(turns) if turns else 0:.1f} deg")
    total_turn = 0.0
    for i in range(1, len(headings)):
        total_turn += (headings[i] - headings[i - 1] + 180.0) % 360.0 - 180.0
    print(f"net heading change {total_turn:.1f} deg")
    crossings = []
    for i in range(1, len(points)):
        for j in range(i + 2, len(points)):
            a, b = points[i - 1], points[i]
            c, d = points[j - 1], points[j]
            if segments_intersect((a[0], a[2]), (b[0], b[2]), (c[0], c[2]), (d[0], d[2])):
                crossings.append((i, j))
    print(f"self-intersections: {len(crossings)}" + (f" e.g. {crossings[:6]}" if crossings else ""))
    print(f"distance from stall point {STALL}: {distance_to_polyline((STALL[0], 0, STALL[1]), points):.2f} m")


def cross2(o, a, b):
    return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])


def segments_intersect(p1, p2, p3, p4):
    d1 = cross2(p3, p4, p1)
    d2 = cross2(p3, p4, p2)
    d3 = cross2(p1, p2, p3)
    d4 = cross2(p1, p2, p4)
    if ((d1 > 0 and d2 < 0) or (d1 < 0 and d2 > 0)) and ((d3 > 0 and d4 < 0) or (d3 < 0 and d4 > 0)):
        return True
    return False


def enforce_mannuthy_turning_radius(points):
    minimum_radius = 11.0
    first, last = 126, 138
    if not points or len(points) <= last + 1:
        return points
    for _ in range(250):
        changed = False
        for i in range(first, last + 1):
            if plan_radius(points[i - 1], points[i], points[i + 1]) >= minimum_radius:
                continue
            mid = tuple((points[i - 1][k] + points[i + 1][k]) * 0.5 for k in range(3))
            points[i] = tuple(points[i][k] + (mid[k] - points[i][k]) * 0.2 for k in range(3))
            changed = True
        if not changed:
            break
    return points


def range_for_kind(data, kind):
    start = 0.0
    for step in data["steps"]:
        if step["kind"] == kind:
            return (start, start + step["distance_m"])
        start += step["distance_m"]
    return (0.0, 0.0)


def build_drive_path(data, projection, connector):
    """Mirrors ValidateContinuousBusDrive's path assembly (fillet inserts omitted)."""
    path = []
    marks = {}
    highway_added = False
    for step in data["steps"]:
        if step["kind"] == "highway":
            if not highway_added:
                marks["highway_start"] = len(path)
                line = convert_line(remove_ollur_transition_kink(data["nh544_primary_centerline"], data),
                                    range_for_kind(data, "highway"), projection)
                highway_added = True
            else:
                continue
        else:
            if step["index"] != 0:
                continue
            marks["approach_start"] = len(path)
            line = enforce_mannuthy_turning_radius(
                convert_line(step["points"], (0.0, step["distance_m"]), projection))
        append_connected(path, line)
    marks["connector_start"] = len(path)
    connector_step = next(s for s in data["steps"] if s["index"] == 3)
    start_point = projection.to_world(connector_step["points"][0],
                                      projection.elevation_at(connector_step["points"][0], data))
    append_connected(path, connector[nearest_index(connector, start_point):])
    marks["spur_start"] = len(path)
    spur = [projection.to_world(data["inferred_spur"]["from"],
                                projection.elevation_at(data["inferred_spur"]["from"], data)),
            projection.to_world(data["inferred_spur"]["to"], 14.5)]
    append_connected(path, spur)
    marks["end"] = len(path) - 1
    return path, marks


def report_radius_profile(label, points, threshold=10.0):
    print(f"\n--- {label}: stations tighter than {threshold:.1f} m ---")
    hits = []
    for i in range(1, len(points) - 1):
        radius = plan_radius(points[i - 1], points[i], points[i + 1])
        if radius < threshold:
            hits.append((i, radius, points[i]))
    for i, radius, point in hits:
        print(f"  #{i:3d} radius {radius:5.2f} m at ({point[0]:8.2f}, {point[2]:9.2f})")
    if not hits:
        print("  none")
    return hits


def main():
    data = load()
    projection = Projection(data)
    connector = build_connector(data, projection)
    print(f"mapped connector stations: {len(connector)}")
    print(f"plan length: {sum(math.dist((connector[i-1][0], connector[i-1][2]), (connector[i][0], connector[i][2])) for i in range(1, len(connector))):.1f} m")
    fitted, info = fit_connector(connector, data, projection)
    if info:
        lo = max(0, info["entry_index"] - 4)
        hi = min(len(connector), info["exit_index"] + 5)
        print("\noriginal mapped stations around the compressed zigzag:")
        for i in range(lo, hi):
            mark = " <-- replaced" if info["entry_index"] <= i <= info["exit_index"] else ""
            print(f"  #{i:3d} ({connector[i][0]:8.2f}, {connector[i][2]:9.2f}){mark}")
        report_span("fitted connector (what the mesh and MeshCollider use)", fitted, None)
        report_span("smoothed replacement span only", info["smoothed"], None)
        outside = [(i, distance_to_polyline(fitted[i], info["smoothed"])) for i in range(len(fitted))]
        print(f"\nfitted connector station count {len(fitted)} vs original {len(connector)}")
    report_span("original mapped connector (pre-fit)", connector, None)
    report_radius_profile("fitted connector", fitted)

    path, marks = build_drive_path(data, projection, fitted)
    lengths = [0.0]
    for i in range(1, len(path)):
        lengths.append(lengths[-1] + math.dist((path[i - 1][0], path[i - 1][2]), (path[i][0], path[i][2])))
    total_len = lengths[-1]
    print("\n--- drive path (real Unity run reached 659/765 = 86.1%%) ---")
    print(f"path stations {len(path)}, plan length {total_len:.1f} m")
    for name, index in sorted(marks.items(), key=lambda kv: kv[1]):
        print(f"  {name:16s} index {index:4d}  arc {lengths[index]:9.1f} m  ({lengths[index] / total_len:5.1%})")
    stall_index = nearest_index(path, (STALL[0], 0.0, STALL[1]))
    print(f"stall XY {STALL} -> nearest path index {stall_index} ({stall_index / (len(path) - 1):5.1%}), "
          f"plan distance {math.hypot(path[stall_index][0]-STALL[0], path[stall_index][2]-STALL[1]):.2f} m")
    conn_index = stall_index - marks["connector_start"]
    print(f"that is connector station {conn_index} of {len(fitted) - 1}")
    for i in range(max(0, conn_index - 3), min(len(fitted), conn_index + 4)):
        radius = plan_radius(fitted[i - 1], fitted[i], fitted[i + 1]) if 0 < i < len(fitted) - 1 else float("inf")
        print(f"  connector #{i:3d} ({fitted[i][0]:8.2f}, {fitted[i][2]:9.2f}) radius {radius:6.2f} m")

    print("\n--- connector width check at the stall ---")
    half_width = 3.5 + 0.85
    print(f"ribbon half-width (asphalt 3.5 + shoulder 0.85) = {half_width:.2f} m")
    for i in range(max(1, conn_index - 6), min(len(fitted) - 1, conn_index + 7)):
        radius = plan_radius(fitted[i - 1], fitted[i], fitted[i + 1])
        flag = "FOLD" if radius < half_width else ""
        print(f"  #{i:3d} radius {radius:6.2f} m {flag}")

    report_radius_profile("drive path", path, 10.0)


if __name__ == "__main__":
    sys.exit(main())
