"""Solve the High Road turnaround as a balloon loop: arc -> straight -> fillet merge.

The mapped route reverses 159.6 degrees at the dead end, which no bus can drive.
The loop leaves the inbound leg tangentially, turns through a bus-radius arc, runs
straight, and merges into the return leg through a fillet.
"""
import math
import sys

sys.path.insert(0, "tools")
import analyze_mdr_connector as A  # noqa: E402

R_TURN = 10.5
# A bus that cannot reach R_TURN would need reversing; keep the loop above it.
R_MIN = 10.4


def unit(a, b):
    dx, dz = b[0] - a[0], b[2] - a[2]
    length = math.hypot(dx, dz)
    return (dx / length, dz / length)


def heading(a, b):
    return math.degrees(math.atan2(b[2] - a[2], b[0] - a[0]))


def arc_point(origin, u, n, sign, radius, sweep_deg):
    """origin/u/n are 2D (x, z) pairs."""
    t = math.radians(sweep_deg)
    return (origin[0] + radius * (math.sin(t) * u[0] + (1 - math.cos(t)) * n[0] * sign),
            origin[1] + radius * (math.sin(t) * u[1] + (1 - math.cos(t)) * n[1] * sign))


def ray_hit(origin, direction, segments, max_distance):
    best = None
    for seg in segments:
        (ax, az), (bx, bz) = seg
        sx, sz = bx - ax, bz - az
        denominator = direction[0] * sz - direction[1] * sx
        if abs(denominator) < 1e-9:
            continue
        qx, qz = ax - origin[0], az - origin[1]
        t = (qx * sz - qz * sx) / denominator
        w = (qx * direction[1] - qz * direction[0]) / denominator
        if t < 0.5 or t > max_distance or w < 0.0 or w > 1.0:
            continue
        if best is None or t < best["distance"]:
            best = dict(distance=t, point=(origin[0] + direction[0] * t, origin[1] + direction[1] * t),
                        heading=math.degrees(math.atan2(sz, sx)))
    return best


def segments_of(points):
    return [((points[i - 1][0], points[i - 1][2]), (points[i][0], points[i][2]))
            for i in range(1, len(points))]


def distance_to_line(point, segments):
    best = float("inf")
    for (ax, az), (bx, bz) in segments:
        dx, dz = bx - ax, bz - az
        sq = dx * dx + dz * dz
        t = 0.0 if sq < 1e-9 else max(0.0, min(1.0, ((point[0] - ax) * dx + (point[1] - az) * dz) / sq))
        best = min(best, math.hypot(point[0] - (ax + dx * t), point[1] - (az + dz * t)))
    return best


def fillet(corner, incoming_dir, outgoing_dir, radius):
    """Arc tangent to both rays; returns (start, end, arc points)."""
    between = math.atan2(incoming_dir[1], incoming_dir[0]) - math.atan2(outgoing_dir[1], outgoing_dir[0])
    between = (between + math.pi) % (2 * math.pi) - math.pi
    turn = abs(between)
    if turn < 1e-3 or turn > math.radians(80):
        return None
    tangent = radius * math.tan(turn / 2)
    start = (corner[0] - incoming_dir[0] * tangent, corner[1] - incoming_dir[1] * tangent)
    end = (corner[0] + outgoing_dir[0] * tangent, corner[1] + outgoing_dir[1] * tangent)
    sign = 1.0 if between < 0 else -1.0
    points = [start]
    for i in range(1, 25):
        fraction = i / 24.0
        # Quadratic Bezier approximation of the fillet reaches tangency smoothly.
        points.append((start[0] * (1 - fraction) ** 2 + 2 * corner[0] * fraction * (1 - fraction)
                       + end[0] * fraction ** 2,
                       start[1] * (1 - fraction) ** 2 + 2 * corner[1] * fraction * (1 - fraction)
                       + end[1] * fraction ** 2))
    points[-1] = end
    return dict(start=start, end=end, points=points, tangent=tangent)


def curvature_min(points, stride=5):
    minimum = float("inf")
    for i in range(stride, len(points) - stride, stride):
        aa, bb, cc = points[i - stride], points[i], points[i + stride]
        ab = (bb[0] - aa[0], bb[1] - aa[1])
        bc = (cc[0] - bb[0], cc[1] - bb[1])
        ac = (cc[0] - aa[0], cc[1] - aa[1])
        area = abs(ab[0] * ac[1] - ab[1] * ac[0])
        if area < 1e-6:
            continue
        minimum = min(minimum, math.hypot(*ab) * math.hypot(*bc) * math.hypot(*ac) / (2 * area))
    return minimum


def main():
    data = A.load()
    proj = A.Projection(data)
    connector = A.build_connector(data, proj)
    fitted, _ = A.fit_connector(connector, data, proj, verbose=False)

    tip_index = max(range(1, len(fitted) - 1),
                    key=lambda i: abs((heading(fitted[i], fitted[i + 1])
                                       - heading(fitted[i - 1], fitted[i]) + 180) % 360 - 180))
    tip = fitted[tip_index]
    u = unit(fitted[tip_index - 1], tip)
    n = (-u[1], u[0])
    return_segments = segments_of(fitted[tip_index + 1:])
    inbound_segments = segments_of(fitted[0:tip_index])
    inbound_heading = math.degrees(math.atan2(u[1], u[0]))

    print(f"tip station {tip_index} at ({tip[0]:.2f}, {tip[2]:.2f}); inbound heading {inbound_heading:.1f} deg")

    candidates = []
    for sign in (1.0, -1.0):
        for a_tenths in range(20, 500):
            a = a_tenths / 10.0
            origin = (tip[0] - u[0] * a, tip[2] - u[1] * a)
            for sweep in range(90, 178, 2):
                exit_heading = inbound_heading + sign * sweep
                direction = (math.cos(math.radians(exit_heading)), math.sin(math.radians(exit_heading)))
                end = arc_point(origin, u, n, sign, R_TURN, sweep)
                hit = ray_hit(end, direction, return_segments, 80.0)
                if hit is None:
                    continue
                kink = abs((hit["heading"] - exit_heading + 180) % 360 - 180)
                if kink > 30 or kink < 2:
                    continue
                merge = fillet(hit["point"], direction,
                               (math.cos(math.radians(hit["heading"])), math.sin(math.radians(hit["heading"]))),
                               R_TURN)
                if merge is None:
                    continue
                if hit["distance"] - merge["tangent"] < 2.0:
                    continue
                # Loop must not cut back across the inbound leg.
                clearance = min(distance_to_line(arc_point(origin, u, n, sign, R_TURN, s), inbound_segments)
                                for s in range(20, sweep - 5, 8))
                if clearance < 5.0:
                    continue
                score = a * 0.5 + kink * 2.0 + (hit["distance"]) * 0.05
                candidates.append(dict(sign=sign, a=a, sweep=sweep, origin=origin, end=end,
                                       kink=kink, run=hit["distance"], merge=merge, hit=hit,
                                       clearance=clearance, score=score))

    candidates.sort(key=lambda c: c["score"])
    print(f"{len(candidates)} feasible loops; best five:")
    for c in candidates[:5]:
        print(f"  a={c['a']:5.1f} m sweep={c['sweep']:5.1f} deg sign={c['sign']:+.0f} "
              f"kink={c['kink']:4.1f} deg run={c['run']:5.1f} m clearance={c['clearance']:5.1f} m "
              f"footprint {c['a']:.0f}+{c['sweep']/360*2*math.pi*R_TURN:.0f}+{c['run']:.0f} m")

    if not candidates:
        return 1

    best = candidates[0]
    sign, sweep, origin = best["sign"], best["sweep"], best["origin"]
    arc = [arc_point(origin, u, n, sign, R_TURN, s) for s in range(0, sweep + 1, 2)]
    merge = best["merge"]
    straight = [arc[-1], merge["start"]]
    loop = arc + straight[1:] + merge["points"][1:]

    print(f"\nchosen: turn {'left' if sign > 0 else 'right'}, start {best['a']:.1f} m back, "
          f"sweep {sweep:.0f} deg, {best['run']:.1f} m run, kink {best['kink']:.1f} deg")
    print(f"  merge arc lands on the return leg at ({merge['end'][0]:.2f}, {merge['end'][1]:.2f})")
    print(f"  loop plan length {sum(math.hypot(loop[i][0]-loop[i-1][0], loop[i][1]-loop[i-1][1]) for i in range(1, len(loop))):.1f} m")
    print(f"  curvature: arc {R_TURN:.2f} m, whole loop min {curvature_min(loop):.2f} m")
    xs = [p[0] for p in loop]
    zs = [p[1] for p in loop]
    print(f"  centreline extent x=[{min(xs):.1f},{max(xs):.1f}] z=[{min(zs):.1f},{max(zs):.1f}] "
          f"(pavement extends 4.35 m further)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
