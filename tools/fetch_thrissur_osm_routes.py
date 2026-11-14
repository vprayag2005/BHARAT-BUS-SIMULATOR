import json
import math
import os
import urllib.parse
import urllib.request
from pyproj import Transformer

ORIGIN_UTM_X = 632883.2
ORIGIN_UTM_Y = 1163650.18
CORRIDOR_SCALE = 0.20

transformer = Transformer.from_crs("EPSG:4326", "EPSG:32643", always_xy=True)


def lonlat_to_unity(lon, lat, y=0.0):
    utm_x, utm_y = transformer.transform(lon, lat)
    ux = (utm_x - ORIGIN_UTM_X) * CORRIDOR_SCALE
    uz = (utm_y - ORIGIN_UTM_Y) * CORRIDOR_SCALE
    return [round(ux, 3), round(y, 3), round(uz, 3)]


def fetch_osrm_route(waypoints_lonlat):
    coords_str = ";".join(f"{lon},{lat}" for lon, lat in waypoints_lonlat)
    url = (
        f"https://router.project-osrm.org/route/v1/driving/{coords_str}"
        "?overview=full&geometries=geojson&steps=true"
    )
    req = urllib.request.Request(url, headers={"User-Agent": "BharatBusSim/1.0"})
    with urllib.request.urlopen(req, timeout=25) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    if data.get("code") != "Ok" or not data.get("routes"):
        raise RuntimeError(f"OSRM failed: {data}")
    route = data["routes"][0]
    coords = route["geometry"]["coordinates"]
    step_names = []
    for leg in route.get("legs", []):
        for step in leg.get("steps", []):
            name = step.get("name", "").strip()
            if name and (not step_names or step_names[-1] != name):
                step_names.append(name)
    return {
        "distance_m": route["distance"],
        "duration_s": route["duration"],
        "step_names": step_names,
        "points_unity": [lonlat_to_unity(lon, lat, 0.02) for lon, lat in coords],
    }


def fetch_swaraj_round_overpass():
    query = """
    [out:json][timeout:30];
    (
      way["name"~"Swaraj Round",i](10.520,76.210,10.529,76.219);
    );
    out body;
    >;
    out skel qt;
    """
    data = urllib.parse.urlencode({"data": query}).encode("utf-8")
    req = urllib.request.Request(
        "https://overpass-api.de/api/interpreter",
        data=data,
        headers={"User-Agent": "BharatBusSim/1.0"},
    )
    with urllib.request.urlopen(req, timeout=35) as resp:
        res = json.loads(resp.read().decode("utf-8"))

    nodes = {
        e["id"]: (e["lon"], e["lat"])
        for e in res["elements"]
        if e["type"] == "node"
    }
    ways = [e for e in res["elements"] if e["type"] == "way"]

    # Stitch Swaraj Round ways into a single ordered closed loop
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

    pts = [lonlat_to_unity(*nodes[nid], 0.02) for nid in ordered if nid in nodes]
    # Ensure closed loop
    if pts and math.hypot(pts[0][0] - pts[-1][0], pts[0][2] - pts[-1][2]) > 0.5:
        pts.append(pts[0])
    return pts


def resample_polyline(pts, min_dist=2.0):
    if len(pts) < 2:
        return pts
    out = [pts[0]]
    for p in pts[1:]:
        dx = p[0] - out[-1][0]
        dz = p[2] - out[-1][2]
        if math.hypot(dx, dz) >= min_dist:
            out.append(p)
    if out[-1] != pts[-1]:
        out.append(pts[-1])
    return out


def main():
    print("Fetching Swaraj Round from Overpass API...")
    swaraj_pts = fetch_swaraj_round_overpass()
    print(f"  Swaraj Round points: {len(swaraj_pts)}")

    routes_spec = [
        {
            "id": "ROAD_SAKTHAN_TO_SWARAJ",
            "name": "Sakthan Stand - High Road - Swaraj Round",
            "road_type": "city_arterial",
            "width_m": 10.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.2150, 10.5165),  # Sakthan Stand
                (76.2148, 10.5230),  # High Road
                (76.2144, 10.5235),  # Swaraj Round South
            ],
        },
        {
            "id": "ROAD_SWARAJ_TO_VADAKKE",
            "name": "Swaraj Round North - Shoranur Road - Vadakke Stand",
            "road_type": "city_arterial",
            "width_m": 10.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.2144, 10.5255),  # Swaraj Round North
                (76.2135, 10.5285),  # Vadakke Stand
            ],
        },
        {
            "id": "ROAD_SWARAJ_TO_MANNUTHY",
            "name": "Swaraj Round East - East Fort - Mannuthy (Palakkad Rd)",
            "road_type": "city_highway_link",
            "width_m": 12.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.2162, 10.5244),  # Swaraj Round East
                (76.2275, 10.5238),  # East Fort
                (76.2625, 10.5332),  # Mannuthy Interchange
            ],
        },
        {
            "id": "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
            "name": "NH 544: Mannuthy - Ollur - Amballur - Paliyekkara Toll",
            "road_type": "national_highway",
            "width_m": 16.0,
            "lanes": 4,
            "divided": True,
            "waypoints": [
                (76.2625, 10.5332),  # Mannuthy Interchange
                (76.2420, 10.4785),  # Ollur NH 544
                (76.2580, 10.4505),  # Amballur NH 544
                (76.2600, 10.4428),  # Paliyekkara Toll Plaza
            ],
        },
        {
            "id": "ROAD_SWARAJ_TO_WEST_FORT",
            "name": "Swaraj Round West - MG Road - West Fort",
            "road_type": "city_arterial",
            "width_m": 10.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.2126, 10.5244),  # Swaraj Round West
                (76.2025, 10.5218),  # West Fort
            ],
        },
        {
            "id": "ROAD_PUTHANPALLI_LINK",
            "name": "Swaraj Round South - Puthanpalli - East Fort",
            "road_type": "city_arterial",
            "width_m": 9.0,
            "lanes": 2,
            "divided": False,
            "waypoints": [
                (76.2144, 10.5235),  # Swaraj Round South
                (76.2183, 10.5209),  # Puthanpalli
                (76.2275, 10.5238),  # East Fort
            ],
        },
    ]

    output = {
        "source": "OpenStreetMap Overpass API + OSRM Driving Geometry",
        "origin_utm": {"x": ORIGIN_UTM_X, "y": ORIGIN_UTM_Y},
        "corridor_scale": CORRIDOR_SCALE,
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
            "step_names": ["Swaraj Round"],
            "points_unity": resample_polyline(swaraj_pts, min_dist=1.5),
        })

    for spec in routes_spec:
        print(f"Fetching OSRM route: {spec['id']}...")
        r = fetch_osrm_route(spec["waypoints"])
        pts = resample_polyline(r["points_unity"], min_dist=2.0)
        print(f"  {len(pts)} pts, {r['distance_m']/1000:.2f} km real, steps: {r['step_names'][:5]}")
        output["roads"].append({
            "id": spec["id"],
            "name": spec["name"],
            "road_type": spec["road_type"],
            "width_m": spec["width_m"],
            "lanes": spec["lanes"],
            "divided": spec["divided"],
            "closed_loop": False,
            "real_distance_m": r["distance_m"],
            "step_names": r["step_names"],
            "points_unity": pts,
        })

    os.makedirs("data/processed", exist_ok=True)
    out_path = "data/processed/thrissur_osm_roads.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2)
    print(f"Saved {len(output['roads'])} real Thrissur roads to {out_path}")


if __name__ == "__main__":
    main()
