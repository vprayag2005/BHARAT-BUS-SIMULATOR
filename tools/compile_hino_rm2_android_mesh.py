#!/usr/bin/env python3
"""
Compiles the real imported Hino RM 2 3D model (unity/Assets/_Project/Vehicles/Models/Hino/HINO_RM2.obj)
and its 6 wheel meshes (Mesh_Hino_Wheel_0..5.asset) into a high-performance binary 3D mesh asset
(app/src/main/assets/hino_rm2_real_mesh.bin) with:
  - Real 3D curved front windshield (front_glass.001) + curved 3D rear windshield with smoked sunband (no yellow top box or rear cutout shield)
  - Ear-clipping triangulation of all N-gons (>4 vertices) so sweeping front side windows have zero holes
  - Analytical 3D Sutherland-Hodgman curve slicing across BOTH sides, front fascia, and rear tailgate for 100% smooth, stair-step-free paint (passing cleanly above both circular wheel arches)
  - Realistic 3D chrome-reflector projector headlamps under flush glass covers and vertical wrap-around rear LED light bars (no dot lights)
  - Zero 3D text objects on the bus body
  - Semi-transparent smoked solar side glass with 3D vertical window mullions, driver sliding window, pleated curtains, and 2x2 luxury passenger seats visible inside.
"""

import json
import math
import os
import struct
import urllib.request
from collections import Counter, defaultdict, deque

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
USER_DOWNLOAD_OBJ = r"C:\Users\praya\Downloads\Hino+rm+2 (1).obj"
OBJ_PATH = os.path.join(REPO_ROOT, "art_src/vehicles/hino_rm2/models/HINO_RM2.obj")
TMP_OBJ_PATH = "/tmp/HINO_RM2_real.obj"
WHEEL_DIR = os.path.join(REPO_ROOT, "art_src/vehicles/hino_rm2/models")
OUT_BIN_PATH = os.path.join(REPO_ROOT, "app/src/main/assets/hino_rm2_real_mesh.bin")


def get_real_obj_path():
    if os.path.exists(USER_DOWNLOAD_OBJ) and os.path.getsize(USER_DOWNLOAD_OBJ) > 10000:
        print(f"Using user download OBJ: {USER_DOWNLOAD_OBJ}")
        return USER_DOWNLOAD_OBJ
    if os.path.exists(OBJ_PATH) and os.path.getsize(OBJ_PATH) > 10000:
        return OBJ_PATH
    if os.path.exists(TMP_OBJ_PATH) and os.path.getsize(TMP_OBJ_PATH) > 10000:
        return TMP_OBJ_PATH
    with open(OBJ_PATH, "r", encoding="utf-8") as f:
        lines = f.read().splitlines()
    oid = [l.split("sha256:")[1].strip() for l in lines if "sha256:" in l][0]
    size = int([l.split("size ")[1].strip() for l in lines if l.startswith("size ")][0])
    req_body = json.dumps({
        "operation": "download",
        "transfers": ["basic"],
        "objects": [{"oid": oid, "size": size}],
    }).encode("utf-8")
    req = urllib.request.Request(
        "https://github.com/vprayag2005/BHARAT-BUS-SIMULATOR.git/info/lfs/objects/batch",
        data=req_body,
        headers={
            "Accept": "application/vnd.git-lfs+json",
            "Content-Type": "application/vnd.git-lfs+json",
        },
    )
    with urllib.request.urlopen(req) as resp:
        meta = json.loads(resp.read().decode("utf-8"))
    href = meta["objects"][0]["actions"]["download"]["href"]
    print("Downloading real HINO_RM2.obj from Git LFS to /tmp/HINO_RM2_real.obj...")
    urllib.request.urlretrieve(href, TMP_OBJ_PATH)
    return TMP_OBJ_PATH


# Part IDs for dynamic animation/lighting in Android renderer:
PART_BODY_STATIC = 0
PART_HEADLIGHT = 1
PART_BRAKELIGHT = 2
PART_REVERSE_LIGHT = 3
PART_BLINKER_LEFT = 4
PART_BLINKER_RIGHT = 5
PART_WHEEL_FL = 6
PART_WHEEL_FR = 7
PART_WHEEL_ML = 8
PART_WHEEL_RL = 9
PART_WHEEL_MR = 10
PART_WHEEL_RR = 11
PART_STEERING_WHEEL = 12
PART_GLASS_SEMI = 13


def face_normal_and_area(verts, fv):
    """
    Computes the true outward unit normal and surface area of a polygon in Bus local space.
    Because X was negated when converting right-handed OBJ -> left-handed Bus local space,
    the outward normal is v x u = (v2 - v0) x (v1 - v0).
    """
    if len(fv) < 3:
        return (0.0, 1.0, 0.0), 0.0
    x0, y0, z0 = verts[fv[0]]
    nx, ny, nz = 0.0, 0.0, 0.0
    area = 0.0
    for i in range(1, len(fv) - 1):
        x1, y1, z1 = verts[fv[i]]
        x2, y2, z2 = verts[fv[i + 1]]
        ux, uy, uz = x1 - x0, y1 - y0, z1 - z0
        vx, vy, vz = x2 - x0, y2 - y0, z2 - z0
        cx = uz * vy - uy * vz
        cy = ux * vz - uz * vx
        cz = uy * vx - ux * vy
        seg_a = 0.5 * math.sqrt(cx * cx + cy * cy + cz * cz)
        area += seg_a
        nx += cx
        ny += cy
        nz += cz
    norm = math.sqrt(nx * nx + ny * ny + nz * nz)
    if norm > 1e-8:
        nx /= norm
        ny /= norm
        nz /= norm
    else:
        nx, ny, nz = 0.0, 1.0, 0.0
    return (nx, ny, nz), area


def pts_normal_and_area(pts):
    """Computes outward unit normal and surface area directly from a list of 3D (x, y, z) tuples."""
    if len(pts) < 3:
        return (0.0, 1.0, 0.0), 0.0
    x0, y0, z0 = pts[0]
    nx, ny, nz = 0.0, 0.0, 0.0
    area = 0.0
    for i in range(1, len(pts) - 1):
        x1, y1, z1 = pts[i]
        x2, y2, z2 = pts[i + 1]
        ux, uy, uz = x1 - x0, y1 - y0, z1 - z0
        vx, vy, vz = x2 - x0, y2 - y0, z2 - z0
        cx = uz * vy - uy * vz
        cy = ux * vz - uz * vx
        cz = uy * vx - ux * vy
        seg_a = 0.5 * math.sqrt(cx * cx + cy * cy + cz * cz)
        area += seg_a
        nx += cx
        ny += cy
        nz += cz
    norm = math.sqrt(nx * nx + ny * ny + nz * nz)
    if norm > 1e-8:
        nx /= norm
        ny /= norm
        nz /= norm
    else:
        nx, ny, nz = 0.0, 1.0, 0.0
    return (nx, ny, nz), area


def triangulate_ngon(verts, fv, fuv):
    """
    Ear-clipping triangulation for arbitrary N-gons (e.g. 12-gon, 30-gon, 48-gon front sweeping windows)
    projected onto their dominant 2D axis plane so no vertices are ever truncated by fv[:4].
    """
    n = len(fv)
    if n <= 4:
        return [(fv, fuv)]
    (nx, ny, nz), _ = face_normal_and_area(verts, fv)
    ax, ay, az = abs(nx), abs(ny), abs(nz)
    if ax >= ay and ax >= az:
        pts2d = [(verts[vi][2], verts[vi][1]) for vi in fv]
    elif ay >= ax and ay >= az:
        pts2d = [(verts[vi][0], verts[vi][2]) for vi in fv]
    else:
        pts2d = [(verts[vi][0], verts[vi][1]) for vi in fv]

    signed_area = 0.0
    for i in range(n):
        x1, y1 = pts2d[i]
        x2, y2 = pts2d[(i + 1) % n]
        signed_area += x1 * y2 - x2 * y1
    orient = 1.0 if signed_area >= 0.0 else -1.0

    def cross2(a, b, c):
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])

    def pt_in_tri(p, a, b, c):
        c1 = cross2(a, b, p) * orient
        c2 = cross2(b, c, p) * orient
        c3 = cross2(c, a, p) * orient
        return c1 >= -1e-7 and c2 >= -1e-7 and c3 >= -1e-7

    idxs = list(range(n))
    tris = []
    guard = 0
    while len(idxs) > 3 and guard < n * 3:
        guard += 1
        ear_found = False
        m = len(idxs)
        for i in range(m):
            i_prev = idxs[(i - 1) % m]
            i_curr = idxs[i]
            i_next = idxs[(i + 1) % m]
            a, b, c = pts2d[i_prev], pts2d[i_curr], pts2d[i_next]
            if cross2(a, b, c) * orient <= 1e-7:
                continue
            has_inside = False
            for j in range(m):
                if j in ((i - 1) % m, i, (i + 1) % m):
                    continue
                p = pts2d[idxs[j]]
                if (
                    abs(p[0] - a[0]) + abs(p[1] - a[1]) < 1e-6
                    or abs(p[0] - b[0]) + abs(p[1] - b[1]) < 1e-6
                    or abs(p[0] - c[0]) + abs(p[1] - c[1]) < 1e-6
                ):
                    continue
                if pt_in_tri(p, a, b, c):
                    has_inside = True
                    break
            if not has_inside:
                tris.append(
                    (
                        [fv[i_prev], fv[i_curr], fv[i_next]],
                        [fuv[i_prev], fuv[i_curr], fuv[i_next]],
                    )
                )
                idxs.pop(i)
                ear_found = True
                break
        if not ear_found:
            i_prev, i_curr, i_next = idxs[0], idxs[1], idxs[2]
            tris.append(
                (
                    [fv[i_prev], fv[i_curr], fv[i_next]],
                    [fuv[i_prev], fuv[i_curr], fuv[i_next]],
                )
            )
            idxs.pop(1)
    if len(idxs) == 3:
        i0, i1, i2 = idxs
        tris.append(([fv[i0], fv[i1], fv[i2]], [fuv[i0], fuv[i1], fuv[i2]]))
    return tris


def triangulate_3d_pts(pts):
    """Splits a convex/clipped 3D polygon with >4 vertices into quads and triangles."""
    n = len(pts)
    if n < 3:
        return []
    if n <= 4:
        return [pts]
    out = []
    i = 1
    while i + 2 < n:
        out.append([pts[0], pts[i], pts[i + 1], pts[i + 2]])
        i += 2
    if i + 1 < n:
        out.append([pts[0], pts[i], pts[i + 1]])
    return out


def clip_poly_by_fn(pts, fn):
    """
    Clips a 3D polygon [p0, p1, ...] against the smooth surface y = fn(z).
    Returns (above_polys, below_polys) where each list contains 0 or 1 polygon (list of 3D points).
    """
    n = len(pts)
    if n < 3:
        return [], []
    vals = [p[1] - fn(p[2]) for p in pts]
    if all(v >= -1e-5 for v in vals):
        return [pts], []
    if all(v <= 1e-5 for v in vals):
        return [], [pts]

    above = []
    below = []
    for i in range(n):
        p1 = pts[i]
        p2 = pts[(i + 1) % n]
        v1 = vals[i]
        v2 = vals[(i + 1) % n]
        in1 = v1 >= 0.0
        in2 = v2 >= 0.0
        if in1:
            above.append(p1)
        else:
            below.append(p1)
        if in1 != in2 and abs(v2 - v1) > 1e-8:
            t = max(0.0, min(1.0, -v1 / (v2 - v1)))
            ix = p1[0] + t * (p2[0] - p1[0])
            iy = p1[1] + t * (p2[1] - p1[1])
            iz = p1[2] + t * (p2[2] - p1[2])
            ip = (ix, iy, iz)
            above.append(ip)
            below.append(ip)
    res_a = [above] if len(above) >= 3 else []
    res_b = [below] if len(below) >= 3 else []
    return res_a, res_b


def clip_poly_by_z(pts, z_cut):
    """
    Clips a 3D polygon [p0, p1, ...] against the vertical plane z = z_cut.
    Returns (front_polys, back_polys).
    """
    n = len(pts)
    if n < 3:
        return [], []
    vals = [p[2] - z_cut for p in pts]
    if all(v >= -1e-5 for v in vals):
        return [pts], []
    if all(v <= 1e-5 for v in vals):
        return [], [pts]

    front = []
    back = []
    for i in range(n):
        p1 = pts[i]
        p2 = pts[(i + 1) % n]
        v1 = vals[i]
        v2 = vals[(i + 1) % n]
        in1 = v1 >= 0.0
        in2 = v2 >= 0.0
        if in1:
            front.append(p1)
        else:
            back.append(p1)
        if in1 != in2 and abs(v2 - v1) > 1e-8:
            t = max(0.0, min(1.0, -v1 / (v2 - v1)))
            ix = p1[0] + t * (p2[0] - p1[0])
            iy = p1[1] + t * (p2[1] - p1[1])
            iz = p1[2] + t * (p2[2] - p1[2])
            ip = (ix, iy, iz)
            front.append(ip)
            back.append(ip)
    res_f = [front] if len(front) >= 3 else []
    res_b = [back] if len(back) >= 3 else []
    return res_f, res_b


# Exact piecewise-linear livery boundary planes y = f(z) across the entire coach length.
# Because f(z) is strictly linear inside every Z-interval ([-6.25, 3.85], [3.85, 4.38], [4.38, 5.80]),
# clipping against the vertical Z-joints (z = -5.62, 2.35, 3.85, 4.38) and then y = f(z) is 100% mathematically
# exact (true 3D planes) with ZERO sliver misclassifications and seamless wrapping around all 4 corners!
def _lerp_z(z, z0, z1, y0, y1):
    if z <= z0:
        return y0
    if z >= z1:
        return y1
    return y0 + (y1 - y0) * ((z - z0) / (z1 - z0))


def y_red_top(z):
    # Rear (-6.20): 1.38m | Rear wheel (-2.80): 1.32m | Front wheel (+3.00): 1.22m | Bend (+3.85): 1.20m
    # Front door & front fascia (>= +4.38): 0.72m
    if z <= 3.85:
        return _lerp_z(z, -6.20, 3.85, 1.38, 1.20)
    return _lerp_z(z, 3.85, 4.38, 1.20, 0.72)


def y_gold_top(z):
    if z <= 3.85:
        return _lerp_z(z, -6.20, 3.85, 1.49, 1.28)
    return _lerp_z(z, 3.85, 4.38, 1.28, 0.80)


def y_burg_top(z):
    if z <= 3.85:
        return _lerp_z(z, -6.20, 3.85, 1.65, 1.39)
    return _lerp_z(z, 3.85, 4.38, 1.39, 0.89)


def y_blue_top(z):
    if z <= 3.85:
        return _lerp_z(z, -6.20, 3.85, 1.94, 1.56)
    return _lerp_z(z, 3.85, 4.38, 1.56, 0.98)


def y_window_sill(z):
    # Horizontal window beltline at Y=2.01m for Z <= 2.35m, sweeping down to Y=1.55m at front pillar (Z=4.38m)
    if z <= 2.35:
        return 2.01
    return _lerp_z(z, 2.35, 4.38, 2.01, 1.55)


def y_roof_cantrail(z):
    return 3.06


Z_SLICE_CUTS = [-5.62, 2.35, 3.85, 4.38]

Y_SLICE_FNS = [
    y_roof_cantrail,
    y_window_sill,
    y_blue_top,
    y_burg_top,
    y_gold_top,
    y_red_top,
]

REAR_Y_SLICE_FNS = [
    lambda z: 3.13,
    lambda z: 2.10,
    y_blue_top,
    y_burg_top,
    y_gold_top,
    y_red_top,
]


def slice_body_poly_along_livery(pts):
    """
    Slices exterior coachwork polygons along vertical Z-joint planes and exact piecewise-linear
    horizontal/sloped livery planes so every color boundary is 100% razor-sharp and continuous.
    """
    cy = sum(p[1] for p in pts) / len(pts)
    cz = sum(p[2] for p in pts) / len(pts)

    if cy >= 3.16:
        return [pts]

    polys = [pts]
    for z_cut in Z_SLICE_CUTS:
        next_polys = []
        for p in polys:
            fr, bk = clip_poly_by_z(p, z_cut)
            next_polys.extend(fr)
            next_polys.extend(bk)
        polys = next_polys

    fns = REAR_Y_SLICE_FNS if cz < -5.85 else Y_SLICE_FNS
    for fn in fns:
        next_polys = []
        for p in polys:
            ab, bl = clip_poly_by_fn(p, fn)
            next_polys.extend(ab)
            next_polys.extend(bl)
        polys = next_polys

    final_polys = []
    for p in polys:
        final_polys.extend(triangulate_3d_pts(p))
    return final_polys


def evaluate_coach_body_color(cx, cy, cz, nx, ny, nz):
    """
    Evaluates the clean, high-gloss automotive paint color for a polygon on the Hino RM2 body.
    Because the exact same 4 functions y_blue_top, y_burg_top, y_gold_top, y_red_top govern the
    entire lower body across sides, front corners, front fascia, and rear tailgate, all transitions
    wrap 100% seamlessly around every corner without any normal-dependent or X-dependent jumps.
    """
    PEARL_WHITE = (244, 247, 252, 255)
    ROYAL_BLUE = (16, 84, 160, 255)
    BURGUNDY = (146, 26, 26, 255)
    GOLD_YELLOW = (228, 178, 20, 255)
    CRIMSON_RED = (186, 26, 24, 255)
    OBSIDIAN_BLACK = (22, 26, 32, 255)

    # 1. Roof & upper cantrail crown
    if cy >= 3.06 or (ny > 0.72 and cy > 2.45):
        return PEARL_WHITE

    # 2. Rear panoramic window surround frame
    if cz < -6.00 and 2.10 <= cy < 3.13 and abs(cx) < 1.00:
        return OBSIDIAN_BLACK

    # 3. Side panoramic window beltline & pillars (between rear C-pillar Z=-5.62 and front A-pillar Z=4.38)
    sill_y = y_window_sill(cz)
    if cy >= sill_y:
        if -5.62 <= cz <= 4.38 and abs(cx) > 0.85:
            return OBSIDIAN_BLACK
        return PEARL_WHITE

    # 4. Continuous wrap-around 4-band luxury coach livery (Front + Both Sides + Rear)
    blue_y = y_blue_top(cz)
    burg_y = y_burg_top(cz)
    gold_y = y_gold_top(cz)
    red_y = y_red_top(cz)

    if cy >= blue_y:
        return PEARL_WHITE
    if cy >= burg_y:
        return ROYAL_BLUE
    if cy >= gold_y:
        return BURGUNDY
    if cy >= red_y:
        return GOLD_YELLOW
    return CRIMSON_RED


def unsubdivide_bfs(verts, face_list, require_same_cat=False):
    """
    Collapses Catmull-Clark 2x2 quad patches into their exact parent quad using parity-locked BFS,
    strictly preserving original vertex winding order and rejecting patches that fold across sharp creases.
    """
    v2f = defaultdict(list)
    f_normals = []
    for fi, (fv, fuv, cat, mtl) in enumerate(face_list):
        n, _ = face_normal_and_area(verts, fv)
        f_normals.append(n)
        if len(fv) == 4:
            for v in fv:
                v2f[v].append(fi)
    used_f = [False] * len(face_list)
    new_faces = []

    def try_patch(v):
        flist = v2f[v]
        if len(flist) != 4 or any(used_f[fi] for fi in flist):
            return None
        cats = {face_list[fi][2] for fi in flist}
        if require_same_cat and len(cats) > 1:
            return None
        n0 = f_normals[flist[0]]
        for fi in flist[1:]:
            ni = f_normals[fi]
            if (n0[0] * ni[0] + n0[1] * ni[1] + n0[2] * ni[2]) < 0.985:
                return None

        qs = [face_list[fi][0] for fi in flist]
        vcnt = Counter(u for q in qs for u in q)
        if len(vcnt) != 9:
            return None
        mids = [u for u, c in vcnt.items() if c == 2]
        if len(mids) != 4:
            return None
        q_info = {}
        for fi in flist:
            q = face_list[fi][0]
            quv = face_list[fi][1]
            idx = q.index(v)
            m_next = q[(idx + 1) % 4]
            c_diag = q[(idx + 2) % 4]
            uv_diag = quv[(idx + 2) % 4] if len(quv) == 4 else (0.5, 0.5)
            m_prev = q[(idx + 3) % 4]
            if m_next in q_info:
                return None
            q_info[m_next] = (c_diag, uv_diag, m_prev)
        if len(q_info) != 4:
            return None
        ordered_corners = []
        ordered_uvs = []
        curr_m = next(iter(q_info.keys()))
        for _ in range(4):
            if curr_m not in q_info:
                return None
            c_diag, uv_diag, next_m = q_info[curr_m]
            ordered_corners.append(c_diag)
            ordered_uvs.append(uv_diag)
            curr_m = next_m
        if len(ordered_corners) != 4 or len(set(ordered_corners)) != 4:
            return None
        cn, _ = face_normal_and_area(verts, ordered_corners)
        if (cn[0] * n0[0] + cn[1] * n0[1] + cn[2] * n0[2]) < 0.985:
            return None
        dom_cat = Counter(face_list[fi][2] for fi in flist).most_common(1)[0][0]
        dom_mtl = face_list[flist[0]][3]
        return flist, ordered_corners, ordered_uvs, mids, dom_cat, dom_mtl

    candidates = sorted(
        v2f.keys(),
        key=lambda v: sum(1 for fi in v2f[v] for u in face_list[fi][0] if len(v2f[u]) != 4),
        reverse=True,
    )
    for seed_v in candidates:
        if try_patch(seed_v) is None:
            continue
        queue = deque([seed_v])
        while queue:
            v = queue.popleft()
            res = try_patch(v)
            if res is None:
                continue
            flist, corners, cuvs, mids, dom_cat, dom_mtl = res
            for fi in flist:
                used_f[fi] = True
            new_faces.append((corners, cuvs, dom_cat, dom_mtl))
            for m in mids:
                other_fis = [fi for fi in v2f[m] if not used_f[fi]]
                if len(other_fis) == 2:
                    qA, qB = face_list[other_fis[0]][0], face_list[other_fis[1]][0]
                    shared = (set(qA) & set(qB)) - {m}
                    for nv in shared:
                        if len(v2f[nv]) == 4:
                            queue.append(nv)

    for fi, item in enumerate(face_list):
        if not used_f[fi]:
            new_faces.append(item)
    return new_faces


def merge_coplanar_quad_pairs(verts, face_list, cos_thresh=0.985):
    """
    Merges adjacent quads sharing an edge when they have the same category/material and nearly
    identical normals, preserving outward winding order.
    """
    edge2f = defaultdict(list)
    normals = []
    for fi, (fv, fuv, cat, mtl) in enumerate(face_list):
        n, _ = face_normal_and_area(verts, fv)
        normals.append(n)
        if len(fv) == 4:
            for i in range(4):
                a, b = fv[i], fv[(i + 1) % 4]
                edge = (min(a, b), max(a, b))
                edge2f[edge].append(fi)

    used = [False] * len(face_list)
    merged = []
    for edge, fis in edge2f.items():
        if len(fis) != 2:
            continue
        f1, f2 = fis
        if used[f1] or used[f2]:
            continue
        fv1, fuv1, cat1, mtl1 = face_list[f1]
        fv2, fuv2, cat2, mtl2 = face_list[f2]
        if cat1 != cat2 or mtl1 != mtl2:
            continue
        n1, n2 = normals[f1], normals[f2]
        if (n1[0] * n2[0] + n1[1] * n2[1] + n1[2] * n2[2]) < cos_thresh:
            continue
        u, v = edge
        idx1 = -1
        for i in range(4):
            if {fv1[i], fv1[(i + 1) % 4]} == {u, v}:
                idx1 = i
                break
        if idx1 == -1:
            continue
        a = fv1[idx1]
        b = fv1[(idx1 + 1) % 4]
        c1 = fv1[(idx1 + 2) % 4]
        d1 = fv1[(idx1 + 3) % 4]
        uv_c1 = fuv1[(idx1 + 2) % 4]
        uv_d1 = fuv1[(idx1 + 3) % 4]

        idx2 = -1
        for i in range(4):
            if fv2[i] == b and fv2[(i + 1) % 4] == a:
                idx2 = i
                break
        if idx2 == -1:
            continue
        c2 = fv2[(idx2 + 2) % 4]
        d2 = fv2[(idx2 + 3) % 4]
        uv_c2 = fuv2[(idx2 + 2) % 4]
        uv_d2 = fuv2[(idx2 + 3) % 4]
        if len({c1, d1, c2, d2}) != 4:
            continue

        def is_straight(p_prev, p_mid, p_next):
            vA = (
                verts[p_mid][0] - verts[p_prev][0],
                verts[p_mid][1] - verts[p_prev][1],
                verts[p_mid][2] - verts[p_prev][2],
            )
            vB = (
                verts[p_next][0] - verts[p_mid][0],
                verts[p_next][1] - verts[p_mid][1],
                verts[p_next][2] - verts[p_mid][2],
            )
            lA = math.sqrt(vA[0] ** 2 + vA[1] ** 2 + vA[2] ** 2)
            lB = math.sqrt(vB[0] ** 2 + vB[1] ** 2 + vB[2] ** 2)
            if lA < 1e-5 or lB < 1e-5:
                return False
            return (vA[0] * vB[0] + vA[1] * vB[1] + vA[2] * vB[2]) / (lA * lB) > 0.94

        if not (is_straight(d1, a, c2) and is_straight(c1, b, d2)):
            continue

        cand_fv = [c1, d1, c2, d2]
        mn, _ = face_normal_and_area(verts, cand_fv)
        if (mn[0] * n1[0] + mn[1] * n1[1] + mn[2] * n1[2]) < 0.92:
            continue

        used[f1] = True
        used[f2] = True
        merged.append((cand_fv, [uv_c1, uv_d1, uv_c2, uv_d2], cat1, mtl1))

    for fi, item in enumerate(face_list):
        if not used[fi]:
            merged.append(item)
    return merged


def parse_unity_wheel_asset(asset_path, bus_local_pos, is_left_side):
    """Parses a Unity YAML Mesh_Hino_Wheel_X.asset and returns pristine circular 3D tire & rim faces with true outward normals."""
    with open(asset_path, "r", encoding="utf-8", errors="ignore") as f:
        text = f.read()

    idx_hex = text.split("m_IndexBuffer: ")[1].splitlines()[0].strip()
    typeless_hex = text.split("_typelessdata: ")[1].splitlines()[0].strip()
    v_cnt = int(text.split("m_VertexCount: ")[1].splitlines()[0].strip())

    sub_section = text.split("m_SubMeshes:")[1].split("m_Shapes:")[0]
    sub_blocks = sub_section.split("- serializedVersion: 2")[1:]
    submeshes = []
    for sb in sub_blocks:
        lines = sb.splitlines()
        fb_list = [l.split(":")[1].strip() for l in lines if "firstByte:" in l]
        ic_list = [l.split(":")[1].strip() for l in lines if "indexCount:" in l]
        if fb_list and ic_list:
            submeshes.append((int(fb_list[0]) // 2, int(ic_list[0])))

    idx_bytes = bytes.fromhex(idx_hex)
    num_indices = len(idx_bytes) // 2
    indices = struct.unpack(f"<{num_indices}H", idx_bytes)

    v_bytes = bytes.fromhex(typeless_hex)
    stride = len(v_bytes) // v_cnt
    w_verts = []
    bx, by, bz = bus_local_pos
    for i in range(v_cnt):
        lx, ly, lz = struct.unpack_from("<fff", v_bytes, i * stride)
        if is_left_side:
            wx = bx - lx
            wy = by + ly
            wz = bz - lz
        else:
            wx = bx + lx
            wy = by + ly
            wz = bz + lz
        w_verts.append((wx, wy, wz))

    out_faces = []
    for sub_idx, (start_i, count_i) in enumerate(submeshes):
        is_rim = sub_idx == 1
        col = (218, 226, 238, 255) if is_rim else (28, 30, 36, 255)
        for t in range(start_i, start_i + count_i, 3):
            if is_left_side:
                i0, i1, i2 = indices[t], indices[t + 2], indices[t + 1]
            else:
                i0, i1, i2 = indices[t], indices[t + 1], indices[t + 2]
            p0, p1, p2 = w_verts[i0], w_verts[i1], w_verts[i2]
            ux, uy, uz = p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]
            vx, vy, vz = p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]
            cx = uy * vz - uz * vy
            cy = uz * vx - ux * vz
            cz = ux * vy - uy * vx
            l = math.sqrt(cx * cx + cy * cy + cz * cz)
            if l < 1e-6:
                continue
            nx, ny, nz = cx / l, cy / l, cz / l
            out_faces.append(([p0, p1, p2], col, (nx, ny, nz), is_rim))
    return out_faces


def main():
    print("Parsing HINO_RM2.obj...")
    verts = []
    uvs = []
    obj_faces = defaultdict(list)
    curr_obj = "default"
    curr_mtl = "none"

    real_obj_path = get_real_obj_path()
    with open(real_obj_path, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            if line.startswith("o ") or line.startswith("g "):
                curr_obj = line[2:].strip()
            elif line.startswith("usemtl "):
                curr_mtl = line[7:].strip()
            elif line.startswith("v "):
                p = line.split()
                verts.append(
                    (
                        -float(p[1]) * 0.685,
                        float(p[2]) * 0.720 + 2.12,
                        float(p[3]) * 0.765 + 0.55,
                    )
                )
            elif line.startswith("vt "):
                p = line.split()
                uvs.append((float(p[1]), float(p[2])))
            elif line.startswith("f "):
                parts = line.split()[1:]
                fv = []
                fuv = []
                for pt in parts:
                    sp = pt.split("/")
                    fv.append(int(sp[0]) - 1)
                    if len(sp) > 1 and sp[1]:
                        ui = int(sp[1]) - 1
                        fuv.append(uvs[ui] if 0 <= ui < len(uvs) else (0.5, 0.5))
                    else:
                        fuv.append((0.5, 0.5))
                if len(fv) > 4:
                    for tfv, tfuv in triangulate_ngon(verts, fv, fuv):
                        obj_faces[curr_obj].append((tfv, tfuv, 0, curr_mtl))
                else:
                    obj_faces[curr_obj].append((fv, fuv, 0, curr_mtl))

    # 1. Pre-filter inward-facing inner-wall trim faces and rear window plug from main_body BEFORE unsubdividing
    main_body_keys = [k for k in list(obj_faces.keys()) if k.lower().startswith("main_body")]
    if main_body_keys:
        outer_main = []
        total_main_faces = sum(len(obj_faces[k]) for k in main_body_keys)
        for k in main_body_keys:
            for fv, fuv, _, mtl in obj_faces[k]:
                (nx, ny, nz), area = face_normal_and_area(verts, fv)
                cx = sum(verts[vi][0] for vi in fv) / len(fv)
                cy = sum(verts[vi][1] for vi in fv) / len(fv)
                cz = sum(verts[vi][2] for vi in fv) / len(fv)
                # Exclude rear window cutout plug faces so the panoramic rear window opening is clean
                if cz < -6.05 and 2.14 <= cy <= 3.13 and abs(cx) < 0.86:
                    continue
                out_dot = nx * cx + ny * (cy - 1.65) + nz * (cz * 0.25)
                if out_dot >= -0.12 or cy < 0.85 or abs(cz) > 5.35:
                    outer_main.append((fv, fuv, 0, mtl))
            del obj_faces[k]
        print(f"  main_body pre-filter: kept {len(outer_main)} outer faces of {total_main_faces}")
        obj_faces["main_body"] = outer_main

    # 1b. Pre-filter front_glass.001 to keep ONLY the outward-facing front surface (preventing double-layer alpha patches)
    if "front_glass.001" in obj_faces:
        outer_fglass = []
        for fv, fuv, _, mtl in obj_faces["front_glass.001"]:
            (nx, ny, nz), area = face_normal_and_area(verts, fv)
            if nz > 0.25:
                outer_fglass.append((fv, fuv, 0, mtl))
        print(f"  front_glass pre-filter: kept {len(outer_fglass)} outer faces of {len(obj_faces['front_glass.001'])}")
        obj_faces["front_glass.001"] = outer_fglass

    # 2. Pre-filter side_glass to keep only the outward-facing side window shell (excluding notched rear cutout cz < -5.95)
    side_glass_keys = [k for k in list(obj_faces.keys()) if k.lower().startswith("side_glass")]
    if side_glass_keys:
        outer_sglass = []
        for k in side_glass_keys:
            for fv, fuv, _, mtl in obj_faces[k]:
                (nx, ny, nz), area = face_normal_and_area(verts, fv)
                cx = sum(verts[vi][0] for vi in fv) / len(fv)
                cz = sum(verts[vi][2] for vi in fv) / len(fv)
                if cz >= -5.95 and (cx * nx + (cz * 0.2) * nz) > 0.0:
                    outer_sglass.append((fv, fuv, 0, mtl))
            del obj_faces[k]
        print(f"  side_glass pre-filter: kept {len(outer_sglass)} outer side window faces")
        obj_faces["side_glass"] = outer_sglass

    # 3. Group 10 rows of 2x2 luxury passenger seats & driver seat
    seat_keys = [
        "Cube.002", "Cube.003", "Cube.016", "Cube.017", "Cube.018",
        "Cube.019", "Cube.020", "Cube.021", "Cube.022",
        "Cube.023", "Cube.024", "Cube.025",
    ]
    seat_faces = []
    for k in seat_keys:
        if k in obj_faces:
            for fv, fuv, _, mtl in obj_faces[k]:
                (nx, ny, nz), _ = face_normal_and_area(verts, fv)
                if ny > -0.45:
                    cat = 1 if mtl == "Material.001" else 2
                    seat_faces.append((fv, fuv, cat, mtl))
            del obj_faces[k]
    obj_faces["passenger_seats"] = seat_faces

    LAYER_INTERIOR = 6.50
    LAYER_EXTERIOR = 0.00
    LAYER_GLASS = -0.35
    LAYER_TRIM = -0.48
    LAYER_MIRRORS = -0.90

    livery_sliced_groups = {"main_body", "HEADLIGHT.001"}

    # Note: 3D text objects (rm.001, wide_suspetion.001, hino.001), yellow top destination sign (Plane.012),
    # rear cutout shield (BACK_GLASS_COVER.001), and rear dot lights (BACK_ROUND_LIGHT*) are excluded!
    target_specs = [
        # (object_name, unsub_passes, merge_passes, min_area, part_id, cull_mode, layer_bias)
        ("main_body", 3, 1, 1e-7, PART_BODY_STATIC, 1, LAYER_EXTERIOR),
        ("HEADLIGHT.001", 2, 1, 1e-7, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.01),     # Lower front bumper sculpted fog recess
        ("box_locker.004", 2, 1, 0.0002, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.04),  # Recessed black luggage latches
        ("box_locker.006", 2, 1, 1e-7, PART_BLINKER_LEFT, 1, LAYER_TRIM - 0.04),        # Side amber marker LED strips
        ("upper_ac.001", 3, 2, 0.0035, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.02),
        ("big_ac.001", 3, 2, 0.0025, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.03),
        ("base.001", 3, 2, 0.0250, PART_BODY_STATIC, 2, LAYER_INTERIOR + 1.50),
        ("exausht", 2, 1, 0.0008, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.02),
        # Real 3D Curved Front Windshield, Side Glass, Window Mullions & Driver Sliding Window
        ("front_glass.001", 1, 0, 1e-7, PART_GLASS_SEMI, 1, LAYER_GLASS),
        ("side_glass", 1, 0, 1e-7, PART_GLASS_SEMI, 1, LAYER_GLASS),
        ("glass_support.001", 0, 0, 0.0004, PART_BODY_STATIC, 1, LAYER_GLASS - 0.03),  # Black vertical window mullions
        ("DRIVER_GLASS.003", 0, 0, 0.0003, PART_BODY_STATIC, 1, LAYER_GLASS - 0.03),   # Driver sliding window frame
        ("DRIVER_GLASS.004", 0, 0, 0.0003, PART_GLASS_SEMI, 1, LAYER_GLASS - 0.01),    # Driver sliding glass pane 1
        ("DRIVER_GLASS.005", 0, 0, 0.0003, PART_GLASS_SEMI, 1, LAYER_GLASS - 0.01),    # Driver sliding glass pane 2
        ("glass_rubber_front.001", 3, 2, 1e-7, PART_BODY_STATIC, 1, LAYER_TRIM),
        # Sculpted Headlamp Housings, Rear Wrap-Around Light Bars & Chrome Hino Emblem (No dot lights or text!)
        ("headlight.003", 2, 1, 1e-7, PART_BODY_STATIC, 1, LAYER_TRIM),
        ("headlight.004", 2, 1, 1e-7, PART_HEADLIGHT, 1, LAYER_TRIM - 0.04),
        ("headlight.005", 2, 1, 1e-7, PART_BLINKER_LEFT, 1, LAYER_TRIM - 0.05),
        ("hino_logo.001", 2, 1, 1e-7, PART_BODY_STATIC, 1, LAYER_TRIM - 0.06),
        ("BACK_LIGHT_UP.001", 2, 1, 1e-7, PART_BRAKELIGHT, 1, LAYER_TRIM - 0.04),
        ("BACK_LIGHT_MIDDLE.001", 2, 1, 1e-7, PART_BRAKELIGHT, 1, LAYER_TRIM - 0.04),
        ("back_light.001", 2, 1, 1e-7, PART_BRAKELIGHT, 1, LAYER_TRIM - 0.04),
        ("back_light.004", 2, 1, 1e-7, PART_REVERSE_LIGHT, 1, LAYER_TRIM - 0.05),
        ("back_light.005", 2, 1, 1e-7, PART_BLINKER_LEFT, 1, LAYER_TRIM - 0.05),
        ("SIDE_LIGHT.001", 2, 1, 1e-7, PART_BLINKER_LEFT, 1, LAYER_TRIM - 0.04),
        ("ENGINE_LOCKER_HANDLE.001", 2, 1, 1e-7, PART_BODY_STATIC, 1, LAYER_TRIM - 0.04),
        # Protruding Wing Mirrors (Layer Mirrors)
        ("MIrror", 3, 2, 0.0018, PART_BODY_STATIC, 0, LAYER_MIRRORS),
        ("mirror_big.001", 3, 2, 0.0010, PART_BODY_STATIC, 0, LAYER_MIRRORS - 0.04),
        ("mirro_small.001", 2, 1, 0.0008, PART_BODY_STATIC, 0, LAYER_MIRRORS - 0.02),
        # Interior Cabin Details
        ("CURTAIN.001", 2, 1, 0.0020, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.35),
        ("BACK_CURTAIN.001", 2, 1, 0.0025, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.32),
        ("Plane.067", 3, 2, 0.0025, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.25),
        ("passenger_seats", 3, 2, 0.0055, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.15),
        ("indoor_box.001", 3, 2, 0.0120, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.10),
        ("dashboard.001", 4, 2, 0.0090, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.15),
        ("DASH.001", 3, 2, 0.0060, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.15),
        ("DASH_1.001", 3, 2, 0.0050, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.15),
        ("DASH_2.001", 4, 2, 0.0070, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.15),
        ("tissue.001", 3, 2, 0.0010, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.30),
        ("First_leather.001", 0, 0, 0.00001, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.20),
        ("Torus.011", 1, 0, 0.0000001, PART_STEERING_WHEEL, 2, LAYER_INTERIOR - 0.28),
        ("Cube.005", 2, 1, 0.0010, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.10),
        ("Cube.004", 2, 1, 0.0010, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.10),
        ("INSIDE_COVER.001", 3, 2, 0.0060, PART_BODY_STATIC, 2, LAYER_INTERIOR + 0.50),
    ]

    compiled_polys = []

    lx, ly, lz = -0.35, 0.80, 0.48
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    lx, ly, lz = lx / ln, ly / ln, lz / ln

    for oname, passes, merge_passes, min_area, base_part_id, cull_mode, layer_bias in target_specs:
        flist = list(obj_faces.get(oname, []))
        if not flist:
            continue

        if oname == "First_leather.001":
            flist = flist[::3]
        elif oname == "Torus.011":
            flist = flist[::4]

        require_cat = oname == "passenger_seats"
        for _ in range(passes):
            flist = unsubdivide_bfs(verts, flist, require_same_cat=require_cat)
        for _ in range(merge_passes):
            flist = merge_coplanar_quad_pairs(verts, flist, cos_thresh=0.972)

        is_livery_sliced = oname in livery_sliced_groups
        kept_cnt = 0

        for fv, fuv, cat, mtl in flist:
            base_pts = [verts[vi] for vi in fv[:4]]
            if is_livery_sliced:
                sub_polys = slice_body_poly_along_livery(base_pts)
            elif oname in ("CURTAIN.001", "BACK_CURTAIN.001"):
                ab, bl = clip_poly_by_fn(base_pts, lambda z: 2.86)
                sub_polys = []
                for p in ab + bl:
                    sub_polys.extend(triangulate_3d_pts(p))
            elif oname == "front_glass.001":
                ab, bl = clip_poly_by_fn(base_pts, lambda z: 2.78)
                sub_polys = []
                for p in ab + bl:
                    sub_polys.extend(triangulate_3d_pts(p))
            else:
                sub_polys = [base_pts]

            for pts in sub_polys:
                (nx, ny, nz), area = pts_normal_and_area(pts)
                if area < min_area:
                    continue

                cx = sum(p[0] for p in pts) / len(pts)
                cy = sum(p[1] for p in pts) / len(pts)
                cz = sum(p[2] for p in pts) / len(pts)

                # For side & rear curtains, keep clean upper valance (cy >= 2.86) + tied-back pillar drapes,
                # and exclude any curtain fringes near the front windshield (cz > 4.35).
                if oname == "CURTAIN.001":
                    if cz > 4.35:
                        continue
                    near_pillar = any(abs(cz - pz) < 0.19 for pz in (-4.50, -3.02, -1.70, -0.38, 0.94, 2.04))
                    if cy < 2.85 and not near_pillar:
                        continue
                elif oname == "BACK_CURTAIN.001":
                    if cy < 2.85 and abs(cx) < 0.82:
                        continue

                # Orient outward from bus center for exterior parts
                if cull_mode == 1 and oname not in ("main_body", "side_glass", "front_glass.001"):
                    out_vec_x = cx
                    out_vec_y = cy - 1.65
                    out_vec_z = cz * 0.35
                    if (nx * out_vec_x + ny * out_vec_y + nz * out_vec_z) < -0.05:
                        nx, ny, nz = -nx, -ny, -nz

                part_id = base_part_id
                if base_part_id == PART_BLINKER_LEFT:
                    part_id = PART_BLINKER_LEFT if cx < 0.0 else PART_BLINKER_RIGHT

                bias = layer_bias

                if is_livery_sliced or oname == "ENGINE_LOCKER_HANDLE.001":
                    rgba = evaluate_coach_body_color(cx, cy, cz, nx, ny, nz)
                elif oname == "front_glass.001":
                    if cy >= 2.78:
                        rgba = (16, 28, 42, 208)  # Smooth dark smoked anti-glare upper sunband
                    else:
                        rgba = (24, 40, 58, 156)  # Curved panoramic solar front windshield
                elif oname == "side_glass":
                    rgba = (22, 36, 52, 168)
                elif oname in ("DRIVER_GLASS.004", "DRIVER_GLASS.005"):
                    rgba = (22, 36, 52, 174)
                elif oname in ("glass_support.001", "DRIVER_GLASS.003", "box_locker.004"):
                    rgba = (20, 24, 30, 255)
                elif oname in ("CURTAIN.001", "BACK_CURTAIN.001", "Plane.067"):
                    rgba = (176, 28, 38, 255)
                elif oname == "passenger_seats":
                    if mtl == "Material.001":
                        rgba = (222, 210, 192, 255)
                    else:
                        rgba = (88, 52, 42, 255)
                elif oname == "indoor_box.001":
                    rgba = (212, 216, 224, 255)
                elif oname in ("upper_ac.001", "big_ac.001"):
                    rgba = (238, 242, 248, 255)
                elif oname == "headlight.003":
                    # Sculpted headlamp housing: bright chrome reflector bowls inside sleek obsidian bezel
                    if abs(cx) > 0.72 and 0.70 <= cy <= 1.04 and nz > 0.48:
                        rgba = (202, 216, 234, 255)
                    else:
                        rgba = (30, 35, 44, 255)
                elif oname in ("MIrror", "mirro_small.001", "glass_rubber_front.001", "Plane.046"):
                    rgba = (24, 28, 35, 255)
                elif oname == "mirror_big.001":
                    rgba = (165, 185, 210, 255)
                elif oname == "headlight.004":
                    rgba = (242, 248, 255, 255)
                elif oname == "headlight.005":
                    rgba = (250, 168, 18, 255)
                elif oname in ("hino_logo.001", "exausht"):
                    rgba = (226, 232, 240, 255)
                elif oname == "tissue.001":
                    rgba = (235, 220, 195, 255)
                elif oname in ("dashboard.001", "DASH.001", "DASH_1.001", "DASH_2.001"):
                    rgba = (32, 37, 46, 255)
                elif oname == "Torus.011":
                    rgba = (26, 28, 34, 255)
                elif oname == "First_leather.001":
                    rgba = (42, 48, 62, 255)
                elif oname in ("Cube.004", "Cube.005"):
                    rgba = (38, 48, 64, 255)
                elif oname == "INSIDE_COVER.001":
                    rgba = (44, 48, 56, 255)
                elif part_id == PART_BRAKELIGHT:
                    rgba = (212, 22, 30, 255)
                elif part_id == PART_REVERSE_LIGHT:
                    rgba = (236, 242, 250, 255)
                elif part_id in (PART_BLINKER_LEFT, PART_BLINKER_RIGHT):
                    rgba = (245, 158, 11, 255)
                else:
                    rgba = (38, 42, 50, 255)

                ndotl = nx * lx + ny * ly + nz * lz
                shade = 0.82 + 0.18 * max(-0.55, min(1.0, ndotl))
                r_sh = max(0, min(255, int(rgba[0] * shade)))
                g_sh = max(0, min(255, int(rgba[1] * shade)))
                b_sh = max(0, min(255, int(rgba[2] * shade)))

                compiled_polys.append(
                    (part_id, cull_mode, (r_sh, g_sh, b_sh, rgba[3]), (nx, ny, nz), pts[:4], bias)
                )
                kept_cnt += 1
        print(f"  {oname:26s}: kept {kept_cnt:5d} polys")

    # Fill the 16cm x 15cm rear center latch cutout in main_body at X in [-0.09, 0.09], Y in [1.74, 1.89], Z=-6.196
    rear_latch_fill = [(-0.09, 1.74, -6.196), (0.09, 1.74, -6.196), (0.09, 1.89, -6.196), (-0.09, 1.89, -6.196)]
    rl_ndotl = -1.0 * lz
    rl_shade = 0.82 + 0.18 * max(-0.55, min(1.0, rl_ndotl))
    rl_col = (int(16 * rl_shade), int(84 * rl_shade), int(160 * rl_shade), 255)
    compiled_polys.append((PART_BODY_STATIC, 1, rl_col, (0.0, 0.0, -1.0), rear_latch_fill, LAYER_EXTERIOR))

    # Full-Size 3D Hino RM-2 Driver Steering Wheel & Tilted Column (X = +0.62m, Y = 1.94m, Z = 5.12m, 480mm diameter)
    sw_cx, sw_cy, sw_cz = 0.62, 1.94, 5.12
    sw_tilt = math.radians(38.0)  # tilted back toward driver
    ct, st = math.cos(sw_tilt), math.sin(sw_tilt)
    def sw_world(lx, ly, lz):
        # Local X is radial right, Local Y is radial up on wheel plane, Local Z is column axis toward driver
        wy = sw_cy + ly * ct - lz * st
        wz = sw_cz - ly * st - lz * ct
        return (sw_cx + lx, wy, wz)

    # Outer Steering Wheel Torus Rim (R = 0.24m -> 48cm full coach diameter, grip radius = 0.022m)
    rim_r, tube_r = 0.24, 0.022
    u_segs, v_segs = 18, 6
    for iu in range(u_segs):
        u0 = 2.0 * math.pi * iu / u_segs
        u1 = 2.0 * math.pi * (iu + 1) / u_segs
        cu0, su0 = math.cos(u0), math.sin(u0)
        cu1, su1 = math.cos(u1), math.sin(u1)
        for iv in range(v_segs):
            v0 = 2.0 * math.pi * iv / v_segs
            v1 = 2.0 * math.pi * (iv + 1) / v_segs
            cv0, sv0 = math.cos(v0), math.sin(v0)
            cv1, sv1 = math.cos(v1), math.sin(v1)
            p00 = sw_world((rim_r + tube_r * cv0) * cu0, (rim_r + tube_r * cv0) * su0, tube_r * sv0)
            p10 = sw_world((rim_r + tube_r * cv0) * cu1, (rim_r + tube_r * cv0) * su1, tube_r * sv0)
            p11 = sw_world((rim_r + tube_r * cv1) * cu1, (rim_r + tube_r * cv1) * su1, tube_r * sv1)
            p01 = sw_world((rim_r + tube_r * cv1) * cu0, (rim_r + tube_r * cv1) * su0, tube_r * sv1)
            um = 0.5 * (u0 + u1)
            vm = 0.5 * (v0 + v1)
            lnx = math.cos(vm) * math.cos(um)
            lny = math.cos(vm) * math.sin(um)
            lnz = math.sin(vm)
            wnx = lnx
            wny = lny * ct - lnz * st
            wnz = -lny * st - lnz * ct
            compiled_polys.append((PART_STEERING_WHEEL, 1, (26, 30, 36, 255), (wnx, wny, wnz), [p00, p10, p11, p01], LAYER_INTERIOR - 0.34))

    # 3 Coach Spokes + Center Horn Hub
    for ang_deg in (-162.0, -18.0, -90.0):
        ar = math.radians(ang_deg)
        ca, sa = math.cos(ar), math.sin(ar)
        px, py = -sa * 0.022, ca * 0.022
        sp0 = sw_world(ca * 0.055 - px, sa * 0.055 - py, 0.0)
        sp1 = sw_world(ca * 0.228 - px, sa * 0.228 - py, 0.0)
        sp2 = sw_world(ca * 0.228 + px, sa * 0.228 + py, 0.0)
        sp3 = sw_world(ca * 0.055 + px, sa * 0.055 + py, 0.0)
        compiled_polys.append((PART_STEERING_WHEEL, 2, (36, 42, 52, 255), (0.0, ct, -st), [sp0, sp1, sp2, sp3], LAYER_INTERIOR - 0.34))
    hub_pts = [
        sw_world(-0.068, -0.055, 0.015),
        sw_world(0.068, -0.055, 0.015),
        sw_world(0.068, 0.055, 0.015),
        sw_world(-0.068, 0.055, 0.015),
    ]
    compiled_polys.append((PART_STEERING_WHEEL, 2, (32, 37, 46, 255), (0.0, ct, -st), hub_pts, LAYER_INTERIOR - 0.35))

    # Static Steering Column Boot to Floor/Dash
    col_pts = [
        sw_world(-0.042, -0.042, 0.0),
        sw_world(0.042, -0.042, 0.0),
        sw_world(0.045, -0.045, -0.45),
        sw_world(-0.045, -0.045, -0.45),
    ]
    compiled_polys.append((PART_BODY_STATIC, 2, (24, 28, 34, 255), (0.0, 0.7, -0.7), col_pts, LAYER_INTERIOR - 0.22))

    # Smoothly curved 8-segment 3D rear panoramic windshield with upper smoked sunband (flush inside rear window frame)
    rear_nx_segs = 8
    rear_y_bands = [
        (2.14, 2.52, (22, 36, 52, 178)),
        (2.52, 2.90, (22, 36, 52, 178)),
        (2.90, 3.13, (16, 26, 40, 212)),
    ]
    for y0, y1, r_rgba in rear_y_bands:
        w0 = 0.96 - 0.02 * ((y0 - 2.14) / 0.99)
        w1 = 0.96 - 0.02 * ((y1 - 2.14) / 0.99)
        for ix in range(rear_nx_segs):
            u0 = -1.0 + 2.0 * (ix / rear_nx_segs)
            u1 = -1.0 + 2.0 * ((ix + 1) / rear_nx_segs)
            x00, x10 = u0 * w0, u1 * w0
            x11, x01 = u1 * w1, u0 * w1
            # Subtle barrel curve along rear glass (-6.188 at edges to -6.208 at center)
            z00 = -6.188 - 0.020 * (1.0 - u0 * u0)
            z10 = -6.188 - 0.020 * (1.0 - u1 * u1)
            um = 0.5 * (u0 + u1)
            rnx = 0.14 * um
            rny = 0.04
            rnz = -math.sqrt(max(0.1, 1.0 - rnx * rnx - rny * rny))
            r_pts = [(x00, y0, z00), (x10, y0, z10), (x11, y1, z10), (x01, y1, z00)]
            compiled_polys.append((PART_GLASS_SEMI, 1, r_rgba, (rnx, rny, rnz), r_pts, LAYER_GLASS))

    chassis_pan = [
        (-1.12, 0.44, -6.05),
        (1.12, 0.44, -6.05),
        (1.12, 0.44, 5.30),
        (-1.12, 0.44, 5.30),
    ]
    compiled_polys.append(
        (PART_BODY_STATIC, 0, (18, 20, 24, 255), (0.0, -1.0, 0.0), chassis_pan, LAYER_INTERIOR + 0.90)
    )

    wheel_well_boxes = [
        ([(-0.88, 0.35, 2.25), (-0.88, 1.22, 2.35), (-0.88, 1.22, 3.65), (-0.88, 0.35, 3.75)], (-1.0, 0.0, 0.0)),
        ([(-1.25, 1.22, 2.35), (-0.88, 1.22, 2.35), (-0.88, 1.22, 3.65), (-1.25, 1.22, 3.65)], (0.0, -1.0, 0.0)),
        ([(-1.25, 0.44, 3.70), (-0.88, 0.44, 3.70), (-0.88, 1.22, 3.70), (-1.25, 1.22, 3.70)], (0.0, 0.0, -1.0)),
        ([(-1.25, 0.44, 2.30), (-1.25, 1.22, 2.30), (-0.88, 1.22, 2.30), (-0.88, 0.44, 2.30)], (0.0, 0.0, 1.0)),

        ([(0.88, 0.35, 2.25), (0.88, 0.35, 3.75), (0.88, 1.22, 3.65), (0.88, 1.22, 2.35)], (1.0, 0.0, 0.0)),
        ([(0.88, 1.22, 2.35), (1.25, 1.22, 2.35), (1.25, 1.22, 3.65), (0.88, 1.22, 3.65)], (0.0, -1.0, 0.0)),
        ([(0.88, 0.44, 3.70), (1.25, 0.44, 3.70), (1.25, 1.22, 3.70), (0.88, 1.22, 3.70)], (0.0, 0.0, -1.0)),
        ([(0.88, 0.44, 2.30), (0.88, 1.22, 2.30), (1.25, 1.22, 2.30), (1.25, 0.44, 2.30)], (0.0, 0.0, 1.0)),

        ([(-0.62, 0.35, -3.55), (-0.62, 1.22, -3.45), (-0.62, 1.22, -2.15), (-0.62, 0.35, -2.05)], (-1.0, 0.0, 0.0)),
        ([(-1.25, 1.22, -3.45), (-0.62, 1.22, -3.45), (-0.62, 1.22, -2.15), (-1.25, 1.22, -2.15)], (0.0, -1.0, 0.0)),
        ([(-1.25, 0.44, -2.10), (-0.62, 0.44, -2.10), (-0.62, 1.22, -2.10), (-1.25, 1.22, -2.10)], (0.0, 0.0, -1.0)),
        ([(-1.25, 0.44, -3.50), (-1.25, 1.22, -3.50), (-0.62, 1.22, -3.50), (-0.62, 0.44, -3.50)], (0.0, 0.0, 1.0)),

        ([(0.62, 0.35, -3.55), (0.62, 0.35, -2.05), (0.62, 1.22, -2.15), (0.62, 1.22, -3.45)], (1.0, 0.0, 0.0)),
        ([(0.62, 1.22, -3.45), (1.25, 1.22, -3.45), (1.25, 1.22, -2.15), (0.62, 1.22, -2.15)], (0.0, -1.0, 0.0)),
        ([(0.62, 0.44, -2.10), (1.25, 0.44, -2.10), (1.25, 1.22, -2.10), (0.62, 1.22, -2.10)], (0.0, 0.0, -1.0)),
        ([(0.62, 0.44, -3.50), (0.62, 1.22, -3.50), (1.25, 1.22, -3.50), (1.25, 0.44, -3.50)], (0.0, 0.0, 1.0)),
    ]
    for wh_pts, wh_norm in wheel_well_boxes:
        compiled_polys.append((PART_BODY_STATIC, 0, (18, 20, 24, 255), wh_norm, wh_pts, LAYER_INTERIOR + 0.85))

    wheel_defs = [
        (0, PART_WHEEL_FL, (-1.02, 0.52, 3.00), True),
        (1, PART_WHEEL_FR, (1.02, 0.52, 3.00), False),
        (2, PART_WHEEL_ML, (-1.02, 0.52, -2.80), True),
        (3, PART_WHEEL_RL, (-0.76, 0.52, -2.80), True),
        (4, PART_WHEEL_MR, (1.02, 0.52, -2.80), False),
        (5, PART_WHEEL_RR, (0.76, 0.52, -2.80), False),
    ]
    total_wheel_polys = 0
    for w_idx, w_part, bus_pos, is_left in wheel_defs:
        asset_p = os.path.join(WHEEL_DIR, f"Mesh_Hino_Wheel_{w_idx}.asset")
        if os.path.exists(asset_p):
            w_faces = parse_unity_wheel_asset(asset_p, bus_pos, is_left)
            for pts, col, wn, is_rim in w_faces:
                wbias = LAYER_EXTERIOR - (0.06 if is_rim else 0.02)
                compiled_polys.append((w_part, 0, col, wn, pts, wbias))
            total_wheel_polys += len(w_faces)
    print(f"  6 Real 3D Wheels          : kept {total_wheel_polys:5d} polys")
    print(f"TOTAL COMPILED 3D POLYGONS: {len(compiled_polys)}")

    os.makedirs(os.path.dirname(OUT_BIN_PATH), exist_ok=True)
    with open(OUT_BIN_PATH, "wb") as out_f:
        out_f.write(b"HRM3")
        out_f.write(struct.pack("<I", len(compiled_polys)))
        for part_id, cull_flag, (r, g, b, a), (nx, ny, nz), pts, bias in compiled_polys:
            vcnt = min(4, len(pts))
            out_f.write(
                struct.pack(
                    "<BBBBBBBf3f",
                    part_id,
                    vcnt,
                    cull_flag,
                    r,
                    g,
                    b,
                    a,
                    bias,
                    nx,
                    ny,
                    nz,
                )
            )
            for i in range(vcnt):
                px, py, pz = pts[i]
                out_f.write(struct.pack("<fff", px, py, pz))

    sz_kb = os.path.getsize(OUT_BIN_PATH) / 1024.0
    print(f"Saved {OUT_BIN_PATH} ({sz_kb:.1f} KB)")


if __name__ == "__main__":
    main()
