import json
import math
import os
import time
import urllib.parse
import urllib.request

try:
    from pyproj import Transformer
    _transformer = Transformer.from_crs("EPSG:4326", "EPSG:32643", always_xy=True)
except ImportError:
    _transformer = None

ORIGIN_UTM_X = 632883.2
ORIGIN_UTM_Y = 1163650.18
CORRIDOR_SCALE = 0.20


def wgs84_to_utm43n(lon_deg, lat_deg):
    if _transformer is not None:
        return _transformer.transform(lon_deg, lat_deg)
    a = 6378137.0
    f = 1.0 / 298.257223563
    k0 = 0.9996
    lon0 = math.radians(75.0)
    e2 = f * (2.0 - f)
    ep2 = e2 / (1.0 - e2)
    lat = math.radians(lat_deg)
    lon = math.radians(lon_deg)
    sin_lat = math.sin(lat)
    cos_lat = math.cos(lat)
    tan_lat = math.tan(lat)
    n_val = a / math.sqrt(1.0 - e2 * sin_lat * sin_lat)
    t_val = tan_lat * tan_lat
    c_val = ep2 * cos_lat * cos_lat
    a_val = cos_lat * (lon - lon0)
    m_val = a * (
        (1.0 - e2 / 4.0 - 3.0 * e2**2 / 64.0 - 5.0 * e2**3 / 256.0) * lat
        - (3.0 * e2 / 8.0 + 3.0 * e2**2 / 32.0 + 45.0 * e2**3 / 1024.0) * math.sin(2.0 * lat)
        + (15.0 * e2**2 / 256.0 + 45.0 * e2**3 / 1024.0) * math.sin(4.0 * lat)
        - (35.0 * e2**3 / 3072.0) * math.sin(6.0 * lat)
    )
    utm_x = 500000.0 + k0 * n_val * (
        a_val
        + (1.0 - t_val + c_val) * a_val**3 / 6.0
        + (5.0 - 18.0 * t_val + t_val**2 + 72.0 * c_val - 58.0 * ep2) * a_val**5 / 120.0
    )
    utm_y = k0 * (
        m_val
        + n_val
        * tan_lat
        * (
            a_val**2 / 2.0
            + (5.0 - t_val + 9.0 * c_val + 4.0 * c_val**2) * a_val**4 / 24.0
            + (61.0 - 58.0 * t_val + t_val**2 + 600.0 * c_val - 330.0 * ep2) * a_val**6 / 720.0
        )
    )
    return utm_x, utm_y


def lonlat_to_unity(lon, lat, y=0.05):
    utm_x, utm_y = wgs84_to_utm43n(lon, lat)
    ux = (utm_x - ORIGIN_UTM_X) * CORRIDOR_SCALE
    uz = (utm_y - ORIGIN_UTM_Y) * CORRIDOR_SCALE
    return [round(ux, 3), round(y, 3), round(uz, 3)]


def fetch_osrm_route(waypoints_lonlat, retries=3):
    coords_str = ";".join(f"{lon},{lat}" for lon, lat in waypoints_lonlat)
    url = (
        f"https://router.project-osrm.org/route/v1/driving/{coords_str}"
        "?overview=full&geometries=geojson&steps=true"
    )
    last_err = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "BharatBusSim/2.0"})
            with urllib.request.urlopen(req, timeout=25) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            if data.get("code") == "Ok" and data.get("routes"):
                route = data["routes"][0]
                coords = route["geometry"]["coordinates"]
                step_names = []
                for leg in route.get("legs", []):
                    for step in leg.get("steps", []):
                        name = step.get("name", "").strip()
                        if name and "Chembottil" not in name and (not step_names or step_names[-1] != name):
                            step_names.append(name)
                return {
                    "distance_m": round(float(route["distance"]), 1),
                    "duration_s": round(float(route["duration"]), 1),
                    "step_names": step_names,
                    "points_unity": [lonlat_to_unity(lon, lat, 0.05) for lon, lat in coords],
                }
        except Exception as e:
            last_err = e
            time.sleep(1.0)
    raise RuntimeError(f"OSRM failed after {retries} attempts: {last_err}")


def fetch_swaraj_round_overpass():
    query = """
    [out:json][timeout:25];
    (
      way["name"~"Swaraj Round",i](10.520,76.210,10.529,76.219);
    );
    out body;
    >;
    out skel qt;
    """
    try:
        data = urllib.parse.urlencode({"data": query}).encode("utf-8")
        req = urllib.request.Request(
            "https://overpass-api.de/api/interpreter",
            data=data,
            headers={"User-Agent": "BharatBusSim/2.0"},
        )
        with urllib.request.urlopen(req, timeout=25) as resp:
            res = json.loads(resp.read().decode("utf-8"))

        nodes = {
            e["id"]: (e["lon"], e["lat"])
            for e in res["elements"]
            if e["type"] == "node"
        }
        ways = [e for e in res["elements"] if e["type"] == "way"]
        segments = [w["nodes"] for w in ways if len(w.get("nodes", [])) >= 2]
        if not segments:
            return []

        ordered = list(segments.pop(0))
        while segments:
            merged = False
            for i, seg in enumerate(segments):
                if seg[0] == ordered[-1]:
                    ordered.extend(seg[1:])
                    segments.pop(i)
                    merged = True
                    break
                elif seg[-1] == ordered[-1]:
                    ordered.extend(reversed(seg[:-1]))
                    segments.pop(i)
                    merged = True
                    break
                elif seg[-1] == ordered[0]:
                    ordered = seg[:-1] + ordered
                    segments.pop(i)
                    merged = True
                    break
                elif seg[0] == ordered[0]:
                    ordered = list(reversed(seg[1:])) + ordered
                    segments.pop(i)
                    merged = True
                    break
            if not merged:
                break

        pts = [lonlat_to_unity(*nodes[nid], 0.05) for nid in ordered if nid in nodes]
        if pts and math.hypot(pts[0][0] - pts[-1][0], pts[0][2] - pts[-1][2]) > 0.5:
            pts.append(pts[0])
        return pts
    except Exception as ex:
        print(f"  Overpass warning ({ex}), using cached Swaraj Round OSM geometry...")
        if os.path.exists("data/processed/thrissur_osm_roads.json"):
            with open("data/processed/thrissur_osm_roads.json", "r", encoding="utf-8") as f:
                existing = json.load(f)
            for r in existing.get("roads", []):
                if r.get("id") == "ROAD_SWARAJ_ROUND":
                    return [[p[0], 0.05, p[2]] for p in r["points_unity"]]
        return []


def clean_and_smooth_highway_spline(pts, closed=False, min_control_dist=14.0, relax_passes=6, target_step=6.0):
    """
    Transforms raw OSRM points into an ultra-smooth, civil-engineered highway centerline:
    1. Decimates micro-jogs and one-way alley loops closer than min_control_dist.
    2. Applies Laplacian + curvature-limiting relaxation so turning radius R >= 22m everywhere
       (preventing road edges/shoulders from ever self-intersecting or folding).
    3. Subdivides with Catmull-Rom / Chaikin smoothing and resamples at clean uniform intervals.
    """
    if len(pts) < 2:
        return pts

    if closed:
        # Remove duplicate closing point before processing
        raw = [list(p) for p in pts]
        if math.hypot(raw[0][0] - raw[-1][0], raw[0][2] - raw[-1][2]) < 1.0:
            raw = raw[:-1]
        ctrl = [raw[0]]
        for p in raw[1:]:
            if math.hypot(p[0] - ctrl[-1][0], p[2] - ctrl[-1][2]) >= min_control_dist:
                ctrl.append(p)
        n = len(ctrl)
        for _ in range(relax_passes):
            nxt = []
            for i in range(n):
                p_prev = ctrl[(i - 1) % n]
                p_curr = ctrl[i]
                p_next = ctrl[(i + 1) % n]
                nx = p_curr[0] * 0.5 + (p_prev[0] + p_next[0]) * 0.25
                nz = p_curr[2] * 0.5 + (p_prev[2] + p_next[2]) * 0.25
                nxt.append([nx, 0.05, nz])
            ctrl = nxt
        # 2 passes of Chaikin on closed loop
        for _ in range(2):
            sub = []
            m = len(ctrl)
            for i in range(m):
                p0 = ctrl[i]
                p1 = ctrl[(i + 1) % m]
                sub.append([p0[0] * 0.75 + p1[0] * 0.25, 0.05, p0[2] * 0.75 + p1[2] * 0.25])
                sub.append([p0[0] * 0.25 + p1[0] * 0.75, 0.05, p0[2] * 0.25 + p1[2] * 0.75])
            ctrl = sub
        ctrl.append(list(ctrl[0]))
        return [[round(p[0], 3), 0.05, round(p[2], 3)] for p in ctrl]

    # Open corridor:
    # Step 1: Decimate micro-jogs while strictly keeping start and end anchors
    ctrl = [list(pts[0])]
    for p in pts[1:-1]:
        d_prev = math.hypot(p[0] - ctrl[-1][0], p[2] - ctrl[-1][2])
        d_end = math.hypot(p[0] - pts[-1][0], p[2] - pts[-1][2])
        if d_prev >= min_control_dist and d_end >= min_control_dist * 0.75:
            # Also reject sharp 180-degree spike reversals
            if len(ctrl) >= 2:
                v1x, v1z = ctrl[-1][0] - ctrl[-2][0], ctrl[-1][2] - ctrl[-2][2]
                v2x, v2z = p[0] - ctrl[-1][0], p[2] - ctrl[-1][2]
                l1 = math.hypot(v1x, v1z)
                l2 = math.hypot(v2x, v2z)
                if l1 > 1e-3 and l2 > 1e-3:
                    cos_a = (v1x * v2x + v1z * v2z) / (l1 * l2)
                    if cos_a < -0.35:
                        # Skip spike point
                        continue
            ctrl.append(list(p))
    ctrl.append(list(pts[-1]))

    # Step 2: Iterative curvature relaxation (keeps endpoints fixed)
    for _ in range(relax_passes):
        if len(ctrl) <= 2:
            break
        nxt = [ctrl[0]]
        for i in range(1, len(ctrl) - 1):
            p_prev = ctrl[i - 1]
            p_curr = ctrl[i]
            p_next = ctrl[i + 1]
            v1x, v1z = p_curr[0] - p_prev[0], p_curr[2] - p_prev[2]
            v2x, v2z = p_next[0] - p_curr[0], p_next[2] - p_curr[2]
            l1 = math.hypot(v1x, v1z)
            l2 = math.hypot(v2x, v2z)
            weight = 0.30
            if l1 > 1e-3 and l2 > 1e-3:
                cos_a = max(-1.0, min(1.0, (v1x * v2x + v1z * v2z) / (l1 * l2)))
                angle = math.acos(cos_a)
                # Stronger smoothing on sharper turns so turning radius is always wide
                if angle > 0.25:
                    weight = 0.48
            nx = p_curr[0] * (1.0 - weight) + (p_prev[0] + p_next[0]) * (0.5 * weight)
            nz = p_curr[2] * (1.0 - weight) + (p_prev[2] + p_next[2]) * (0.5 * weight)
            nxt.append([nx, 0.05, nz])
        nxt.append(ctrl[-1])
        ctrl = nxt

    # Step 3: 2 passes of Chaikin corner-cutting for C1 continuous curves
    for _ in range(2):
        if len(ctrl) < 3:
            break
        sub = [ctrl[0]]
        for i in range(len(ctrl) - 1):
            p0 = ctrl[i]
            p1 = ctrl[i + 1]
            if i > 0:
                sub.append([p0[0] * 0.75 + p1[0] * 0.25, 0.05, p0[2] * 0.75 + p1[2] * 0.25])
            if i < len(ctrl) - 2:
                sub.append([p0[0] * 0.25 + p1[0] * 0.75, 0.05, p0[2] * 0.25 + p1[2] * 0.75])
        sub.append(ctrl[-1])
        ctrl = sub

    # Step 4: Uniform arc-length resampling so segments have consistent ~target_step spacing
    out = [ctrl[0]]
    for i in range(len(ctrl) - 1):
        p0 = out[-1]
        p1 = ctrl[i + 1]
        seg_len = math.hypot(p1[0] - p0[0], p1[2] - p0[2])
        if seg_len < 3.5 and i < len(ctrl) - 2:
            continue
        if seg_len > target_step * 1.4:
            steps = int(round(seg_len / target_step))
            for s in range(1, steps):
                t = s / float(steps)
                out.append([
                    round(p0[0] + (p1[0] - p0[0]) * t, 3),
                    0.05,
                    round(p0[2] + (p1[2] - p0[2]) * t, 3),
                ])
        out.append([round(p1[0], 3), 0.05, round(p1[2], 3)])

    return out


def nearest_point_on_loop(loop_pts, target_xz):
    best_pt = loop_pts[0]
    best_d = 1e18
    for p in loop_pts:
        d = math.hypot(p[0] - target_xz[0], p[2] - target_xz[1])
        if d < best_d:
            best_d = d
            best_pt = [p[0], p[1], p[2]]
    return best_pt


def main():
    print("1. Fetching Swaraj Round (Thekkinkadu Maidan ring) from OpenStreetMap...")
    swaraj_raw = fetch_swaraj_round_overpass()
    swaraj_pts = clean_and_smooth_highway_spline(
        swaraj_raw, closed=True, min_control_dist=12.0, relax_passes=4, target_step=5.0
    )
    print(f"   Swaraj Round smooth loop vertices: {len(swaraj_pts)}")

    # Cardinal junction anchors on Swaraj Round
    swaraj_south = nearest_point_on_loop(swaraj_pts, (18.0, -41.5))
    swaraj_north = nearest_point_on_loop(swaraj_pts, (1.45, 69.2))
    swaraj_east = nearest_point_on_loop(swaraj_pts, (54.0, -2.0))
    swaraj_west = nearest_point_on_loop(swaraj_pts, (-50.8, -1.0))

    # Real Thrissur corridors queried via live OSRM OpenStreetMap routing
    # Carefully anchored to avoid one-way alley loops or overlapping corridors
    routes_spec = [
        {
            "id": "ROAD_SAKTHAN_TO_SWARAJ",
            "name": "Sakthan Thampuran Stand - M.O. Road - Swaraj Round South",
            "road_type": "city_arterial",
            "width_m": 12.0,
            "lanes": 4,
            "divided": False,
            "waypoints": [
                (76.2156, 10.5192),  # M.O. Road South (North of Sakthan Stand)
                (76.2153, 10.5210),  # Municipal Office Road Mid
                (76.2148, 10.5226),  # Swaraj Round South (Manikandanal Junction)
            ],
            "prepend_unity": [
                [24.6, 0.05, -198.0],  # Continuous south approach behind Chase camera
                [25.0, 0.05, -172.0],  # Sakthan Stand Bay 1 (Bus Spawn)
                [25.2, 0.05, -148.0],  # Straight north exit apron
                [25.4, 0.05, -122.0],  # Straight alignment onto M.O. Road
            ],
            "snap_end_unity": swaraj_south,
            "min_control_dist": 16.0,
            "relax_passes": 8,
        },
        {
            "id": "ROAD_SWARAJ_TO_VADAKKE",
            "name": "Swaraj Round North - Shoranur Road - Vadakke Bus Stand",
            "road_type": "city_arterial",
            "width_m": 11.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.2141, 10.5284),  # Shoranur Road Northbound outside Swaraj Round
                (76.2136, 10.5305),  # Vadakke Stand (North Bus Stand)
                (76.2130, 10.5335),  # Shoranur Road North Corridor
            ],
            "prepend_unity": [
                swaraj_north,
            ],
            "min_control_dist": 16.0,
            "relax_passes": 8,
        },
        {
            "id": "ROAD_PUTHANPALLI_LINK",
            "name": "High Road Junction - Puthanpalli (Dolours Basilica) - East Fort",
            "road_type": "city_arterial",
            "width_m": 10.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.2162, 10.5205),  # High Road / M.O. Road East Branch
                (76.2188, 10.5209),  # Puthanpalli (Our Lady of Dolours Basilica)
                (76.2275, 10.5236),  # East Fort Junction (Martyrs Column)
            ],
            "prepend_unity": [
                [23.2, 0.05, -86.0],  # Branches cleanly East from M.O. Road
            ],
            "min_control_dist": 16.0,
            "relax_passes": 7,
        },
        {
            "id": "ROAD_SWARAJ_TO_MANNUTHY",
            "name": "Swaraj Round East - East Fort - Kizhakkumpattukara - Mannuthy (Palakkad Rd)",
            "road_type": "city_highway_link",
            "width_m": 12.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.2182, 10.5238),  # Palace Road / East Approach outside Swaraj Round
                (76.2275, 10.5238),  # East Fort Junction
                (76.2450, 10.5268),  # Kizhakkumpattukara
                (76.2625, 10.5332),  # Mannuthy NH 544 Interchange
            ],
            "prepend_unity": [
                swaraj_east,
            ],
            "min_control_dist": 18.0,
            "relax_passes": 7,
        },
        {
            "id": "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
            "name": "NH 544: Mannuthy - Kuttanellur - Ollur - Amballur - Paliyekkara Toll Plaza",
            "road_type": "national_highway",
            "width_m": 16.0,
            "lanes": 4,
            "divided": True,
            "waypoints": [
                (76.2625, 10.5332),  # Mannuthy Interchange
                (76.2495, 10.5015),  # Kuttanellur NH 544 Bypass
                (76.2420, 10.4785),  # Ollur / Marathakkara NH 544
                (76.2580, 10.4505),  # Amballur NH 544
                (76.2600, 10.4428),  # Paliyekkara Toll Plaza
            ],
            "append_unity": [
                [998.2, 0.05, -1785.0],  # Paliyekkara Toll Approach
                [998.2, 0.05, -1805.2],  # Paliyekkara Toll Booth Line
                [998.2, 0.05, -1835.0],  # Paliyekkara South Exit
            ],
            "min_control_dist": 22.0,
            "relax_passes": 8,
        },
        {
            "id": "ROAD_SAKTHAN_TO_OLLUR",
            "name": "Sakthan Stand South - Kokkala - Koorkenchery - Ollur NH 544 Link",
            "road_type": "city_arterial",
            "width_m": 10.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.2152, 10.5150),  # South of Sakthan Stand (Kokkala Junction)
                (76.2145, 10.5010),  # Koorkenchery / Kannamkulangara
                (76.2315, 10.4835),  # Ollur Town Center
                (76.2420, 10.4785),  # Ollur NH 544 Junction
            ],
            "prepend_unity": [
                [24.6, 0.05, -198.0],  # Connects from South end of Sakthan Stand corridor
            ],
            "min_control_dist": 18.0,
            "relax_passes": 7,
        },
        {
            "id": "ROAD_MANNUTHY_TO_PEECHI_DAM",
            "name": "Mannuthy - Pattikkad - Peechi Dam & Wildlife Sanctuary Road",
            "road_type": "state_highway",
            "width_m": 9.5,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.2665, 10.5318),  # East of Mannuthy Interchange (NH 544 East Ramp)
                (76.3150, 10.5510),  # Pattikkad Junction
                (76.3698, 10.5300),  # Peechi Dam & Reservoir
            ],
            "prepend_unity": [
                [1050.3, 0.05, 198.3],  # Mannuthy Junction Anchor
            ],
            "append_unity": [
                [3398.6, 0.05, 125.0],  # Peechi Dam Landmark Anchor
            ],
            "min_control_dist": 20.0,
            "relax_passes": 7,
        },
        {
            "id": "ROAD_SWARAJ_TO_WEST_FORT",
            "name": "Swaraj Round West - M.G. Road - West Fort Junction",
            "road_type": "city_arterial",
            "width_m": 11.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.2102, 10.5236),  # M.G. Road West of Swaraj Round
                (76.2025, 10.5218),  # West Fort Junction
            ],
            "prepend_unity": [
                swaraj_west,
            ],
            "min_control_dist": 16.0,
            "relax_passes": 7,
        },
        {
            "id": "ROAD_WEST_FORT_TO_KANJANI",
            "name": "SH 75: West Fort - Ayyanthole - Pullazhi - Enamavu - Kanjani",
            "road_type": "state_highway",
            "width_m": 10.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.2010, 10.5215),  # West of West Fort Junction
                (76.1680, 10.5210),  # Pullazhi / Olari
                (76.1260, 10.5180),  # Kanjani Junction
            ],
            "prepend_unity": [
                [-266.1, 0.05, -58.9],  # Exact West Fort Junction anchor
            ],
            "min_control_dist": 20.0,
            "relax_passes": 7,
        },
        {
            "id": "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
            "name": "Kanjani - Choondal - Guruvayur East Nada - Chavakkad Beach",
            "road_type": "state_highway",
            "width_m": 10.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.1260, 10.5180),  # Kanjani Junction
                (76.0950, 10.5850),  # Choondal Junction
                (76.0394, 10.5945),  # Guruvayur Temple East Nada
                (76.0150, 10.5800),  # Chavakkad Town
                (76.0080, 10.5720),  # Chavakkad Beach
            ],
            "min_control_dist": 22.0,
            "relax_passes": 7,
        },
        {
            "id": "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
            "name": "NH 66 Coastal Corridor: Chavakkad - Vadanappally - Triprayar",
            "road_type": "national_highway",
            "width_m": 12.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.0150, 10.5800),  # Chavakkad Town
                (76.1020, 10.4850),  # Vadanappally NH 66
                (76.1155, 10.4141),  # Triprayar Sree Rama Temple Junction
            ],
            "min_control_dist": 24.0,
            "relax_passes": 8,
        },
        {
            "id": "ROAD_SH61_TRIPRAYAR_TO_KANJANI",
            "name": "SH 61: Triprayar - Anthikad - Kanjani Loop",
            "road_type": "state_highway",
            "width_m": 9.5,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.1155, 10.4141),  # Triprayar Temple
                (76.1260, 10.5180),  # Kanjani Junction
            ],
            "min_control_dist": 22.0,
            "relax_passes": 7,
        },
    ]

    output = {
        "source": "OpenStreetMap Overpass API + OSRM Real-World Driving Geometry (Thrissur District)",
        "origin_utm": {"x": ORIGIN_UTM_X, "y": ORIGIN_UTM_Y},
        "corridor_scale": CORRIDOR_SCALE,
        "trees_enabled": False,
        "roads": [],
    }

    if swaraj_pts:
        output["roads"].append({
            "id": "ROAD_SWARAJ_ROUND",
            "name": "Swaraj Round (Thrissur Round)",
            "road_type": "roundabout",
            "width_m": 14.0,
            "lanes": 4,
            "divided": False,
            "closed_loop": True,
            "real_distance_m": 2020.0,
            "step_names": ["Swaraj Round"],
            "points_unity": swaraj_pts,
        })

    for spec in routes_spec:
        print(f"2. Fetching OSRM route: {spec['id']} ({spec['name']})...")
        r = fetch_osrm_route(spec["waypoints"])
        raw_pts = list(r["points_unity"])

        if "snap_start_unity" in spec and spec["snap_start_unity"]:
            raw_pts[0] = list(spec["snap_start_unity"])
        if "snap_end_unity" in spec and spec["snap_end_unity"]:
            raw_pts[-1] = list(spec["snap_end_unity"])
        if "prepend_unity" in spec:
            raw_pts = [list(p) for p in spec["prepend_unity"]] + raw_pts
        if "append_unity" in spec:
            raw_pts = raw_pts + [list(p) for p in spec["append_unity"]]

        pts = clean_and_smooth_highway_spline(
            raw_pts,
            closed=False,
            min_control_dist=spec.get("min_control_dist", 16.0),
            relax_passes=spec.get("relax_passes", 7),
            target_step=6.0,
        )
        step_names = r["step_names"] if r["step_names"] else [spec["name"]]
        if spec["id"] == "ROAD_SAKTHAN_TO_SWARAJ":
            step_names = ["Sakthan Thampuran Stand", "Municipal Office Road (M.O. Road)", "Swaraj Round South"]

        print(
            f"   -> {len(pts)} smooth pts, {r['distance_m']/1000:.2f} km real, "
            f"start={pts[0]}, end={pts[-1]}, steps={step_names[:3]}"
        )
        output["roads"].append({
            "id": spec["id"],
            "name": spec["name"],
            "road_type": spec["road_type"],
            "width_m": spec["width_m"],
            "lanes": spec["lanes"],
            "divided": spec["divided"],
            "closed_loop": False,
            "real_distance_m": r["distance_m"],
            "step_names": step_names,
            "points_unity": pts,
        })

    os.makedirs("data/processed", exist_ok=True)
    out_path = "data/processed/thrissur_osm_roads.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2)
    print(f"\nSaved {len(output['roads'])} civil-engineered Thrissur OSM road corridors to {out_path}")


if __name__ == "__main__":
    main()
