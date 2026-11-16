#!/usr/bin/env python3
"""
Compiles the real imported Hino RM 2 3D model (unity/Assets/_Project/Vehicles/Models/Hino/HINO_RM2.obj),
its 6 wheel meshes (Mesh_Hino_Wheel_0..5.asset), and its UV textures (Eagle paint test.png,
Rm 2 paint .png, Eagle glass.png, FRONT GLASS.png, plate no.png, tissue rtexture.png)
into a high-performance binary 3D mesh asset (app/src/main/assets/hino_rm2_real_mesh.bin)
with accurate outward normals, 3-layer interior/exterior depth separation, and backface-culling flags
for locked 60 FPS rendering in the Android 3D viewport (Thrissur3DRoadViewport.kt).
"""

import json
import math
import os
import struct
import subprocess
import urllib.request
from collections import Counter, defaultdict, deque

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
USER_DOWNLOAD_OBJ = r"C:\Users\praya\Downloads\Hino+rm+2 (1).obj"
OBJ_PATH = os.path.join(REPO_ROOT, "unity/Assets/_Project/Vehicles/Models/Hino/HINO_RM2.obj")
TMP_OBJ_PATH = "/tmp/HINO_RM2_real.obj"
WHEEL_DIR = os.path.join(REPO_ROOT, "unity/Assets/_Project/Vehicles/Models/Hino")
TEX_DIR = os.path.join(REPO_ROOT, "unity/Assets/_Project/Vehicles/Textures/Hino")
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


def load_rgba_image(filepath, w, h):
    try:
        from PIL import Image
        im = Image.open(filepath).convert("RGBA").resize((w, h), Image.Resampling.LANCZOS)
        return im.tobytes(), w, h
    except Exception:
        raw = subprocess.check_output(
            ["convert", filepath, "-resize", f"{w}x{h}!", "-depth", "8", "rgba:-"]
        )
        return raw, w, h


def sample_rgb(raw, w, h, u, v):
    u = u % 1.0
    v = v % 1.0
    ix = min(w - 1, max(0, int(u * w)))
    iy = min(h - 1, max(0, int((1.0 - v) * h)))
    idx = (iy * w + ix) * 4
    return raw[idx], raw[idx + 1], raw[idx + 2], raw[idx + 3]


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
        # True reversed cross product (v x u) for reflected X axis:
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


def unsubdivide_bfs(verts, face_list, require_same_cat=True):
    """
    Collapses Catmull-Clark 2x2 quad patches into their exact parent quad using parity-locked BFS,
    strictly preserving original vertex winding order and rejecting patches that fold across sharp creases.
    face_list items: (fv, fuv, cat, mtl)
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
        # Ensure all 4 sub-quads face the same general direction (do not collapse across sharp 90/180 deg folds)
        n0 = f_normals[flist[0]]
        for fi in flist[1:]:
            ni = f_normals[fi]
            if (n0[0] * ni[0] + n0[1] * ni[1] + n0[2] * ni[2]) < 0.65:
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
            # Outer boundary of quad [v, m_next, c_diag, m_prev] runs from m_next -> c_diag -> m_prev!
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
        # Verify collapsed quad normal aligns with sub-quad normals
        cn, _ = face_normal_and_area(verts, ordered_corners)
        if (cn[0] * n0[0] + cn[1] * n0[1] + cn[2] * n0[2]) < 0.70:
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
    print("Loading textures...")
    kerala_raw, kw, kh = load_rgba_image(os.path.join(TEX_DIR, "Ak travels.png"), 1024, 512)
    eagle_raw, ew, eh = load_rgba_image(os.path.join(TEX_DIR, "Eagle paint test.png"), 1024, 512)
    rm2_ao_raw, aw, ah = load_rgba_image(os.path.join(TEX_DIR, "Rm 2 paint .png"), 1024, 512)
    eglass_raw, gw, gh = load_rgba_image(os.path.join(TEX_DIR, "Eagle glass.png"), 512, 512)

    def sample_livery_uv(u, v):
        u = max(0.0, min(1.0, u))
        v = max(0.0, min(1.0, v))
        r, g, b, _ = sample_rgb(kerala_raw, kw, kh, u, v)
        ar, ag, ab, aa = sample_rgb(rm2_ao_raw, aw, ah, u, v)

        # Categorize for quad merging
        if r < 65 and g < 65 and b < 65:
            cat = 3 # Dark lower skirts / intake grilles / wheel arch trim
        elif r > 120 and g < 60 and b < 60:
            cat = 1 # Crimson red lower body
        elif (b > 90 and r < 60) or (r > 160 and g > 130 and b < 60):
            cat = 2 # Blue & Yellow aerodynamic racing sweep stripes
        else:
            cat = 0 # Pearl white upper body

        # Apply ambient occlusion & door seam shading from Rm 2 paint.png
        if aa > 15:
            ao_val = (ar + ag + ab) / (3.0 * 255.0)
            darkness = max(0.55, min(1.0, ao_val * 0.42 + 0.58))
            r = int(r * darkness)
            g = int(g * darkness)
            b = int(b * darkness)

        return (max(18, min(255, r)), max(18, min(255, g)), max(18, min(255, b)), 255), cat

    def sample_livery_quad(fv, fuv):
        u_c = sum(p[0] for p in fuv) / len(fuv)
        v_c = sum(p[1] for p in fuv) / len(fuv)
        return sample_livery_uv(u_c, v_c)

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
                obj_faces[curr_obj].append((fv, fuv, 0, curr_mtl))

    # Pre-filter inward-facing inner-wall trim faces from main_body BEFORE unsubdividing
    main_body_keys = [k for k in obj_faces if k.lower().startswith("main_body")]
    if main_body_keys:
        outer_main = []
        total_main_faces = sum(len(obj_faces[k]) for k in main_body_keys)
        for k in main_body_keys:
            for fv, fuv, _, mtl in obj_faces[k]:
                (nx, ny, nz), area = face_normal_and_area(verts, fv)
                cx = sum(verts[vi][0] for vi in fv) / len(fv)
                cy = sum(verts[vi][1] for vi in fv) / len(fv)
                cz = sum(verts[vi][2] for vi in fv) / len(fv)
                out_dot = nx * cx + ny * (cy - 1.65) + nz * (cz * 0.25)
                # Keep all outward-facing exterior body faces + wheel arches + bumpers
                if out_dot >= -0.12 or cy < 0.85 or abs(cz) > 5.35:
                    outer_main.append((fv, fuv, 0, mtl))
            del obj_faces[k]
        print(f"  main_body pre-filter: kept {len(outer_main)} outer faces of {total_main_faces}")
        obj_faces["main_body"] = outer_main

    livery_roots = ["main_body", "box_locker", "box_door"]
    for oname in list(obj_faces.keys()):
        if any(oname.lower().startswith(lr) for lr in livery_roots):
            tagged = []
            for fv, fuv, _, mtl in obj_faces[oname]:
                _, cat = sample_livery_quad(fv, fuv)
                tagged.append((fv, fuv, cat, mtl))
            obj_faces[oname] = tagged

    # Render Layer Bias Strategy (Painter's Algorithm):
    #   LAYER_INTERIOR (+6.5f) : Passenger seats, curtains, dashboard, steering wheel, internal chassis base
    #                            Always drawn BEFORE exterior body & windows so interior parts NEVER poke through walls!
    #   LAYER_EXTERIOR ( 0.0f) : Solid exterior coachwork (main_body, lockers, doors, rooftop AC, wheels)
    #   LAYER_GLASS    (-0.35f): Semi-transparent windows (side_glass, front glass, BACK_GLASS_COVER)
    #   LAYER_TRIM     (-0.48f): Headlights, taillights, chrome emblems, rubber window frames
    #   LAYER_MIRRORS  (-0.90f): High-mount exterior twin-arm wing mirrors
    LAYER_INTERIOR = 6.50
    LAYER_EXTERIOR = 0.00
    LAYER_GLASS = -0.35
    LAYER_TRIM = -0.48
    LAYER_MIRRORS = -0.90

    # cull_mode:
    #   0 = double-sided / always visible (mirrors)
    #   1 = true outward-normal backface culling (exterior body, glass, wheels, lights)
    #   2 = interior cabin detail (culled when viewing from behind the bus)
    target_specs = [
        # (object_name, unsub_passes, merge_passes, min_area, part_id, cull_mode, layer_bias)
        ("main_body", 3, 2, 0.0018, PART_BODY_STATIC, 1, LAYER_EXTERIOR),
        ("box_locker", 3, 2, 0.0020, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.02),
        ("box_locker.001", 3, 2, 0.0012, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.03),
        ("box_locker.002", 3, 2, 0.0012, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.03),
        ("box_door", 3, 2, 0.0020, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.02),
        ("upper_ac", 3, 2, 0.0040, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.02),
        ("big_ac.001", 3, 2, 0.0030, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.03),
        ("base", 3, 2, 0.0250, PART_BODY_STATIC, 2, LAYER_INTERIOR + 1.50),
        ("wide_suspetion", 3, 2, 0.0040, PART_BODY_STATIC, 2, LAYER_INTERIOR + 1.20),
        ("exausht_", 3, 2, 0.0018, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.02),
        ("aluminium_desing", 3, 2, 0.0120, PART_BODY_STATIC, 1, LAYER_EXTERIOR - 0.03),
        # Glass & Windows (Layer Glass, double-sided so never culled)
        ("side_glass", 3, 2, 0.0020, PART_GLASS_SEMI, 0, LAYER_GLASS),
        ("BACK_GLASS_COVER", 3, 2, 0.0025, PART_GLASS_SEMI, 0, LAYER_GLASS),
        ("glass_rubber_front", 3, 2, 0.0025, PART_BODY_STATIC, 1, LAYER_TRIM),
        # Lights, Emblems & Trim (Layer Trim)
        ("headlight", 3, 2, 0.0020, PART_BODY_STATIC, 1, LAYER_TRIM),
        ("headlight.001", 3, 2, 0.0006, PART_HEADLIGHT, 1, LAYER_TRIM - 0.05),
        ("headlight.002", 3, 2, 0.0004, PART_HEADLIGHT, 1, LAYER_TRIM - 0.05),
        ("HEADLIGHT", 1, 1, 0.00001, PART_HEADLIGHT, 1, LAYER_TRIM - 0.05),
        ("rm", 3, 2, 0.0006, PART_BODY_STATIC, 1, LAYER_TRIM - 0.06),
        ("hino_logo", 3, 2, 0.0006, PART_BODY_STATIC, 1, LAYER_TRIM - 0.06),
        ("BACK_LIGHT_UP", 3, 2, 0.0006, PART_BRAKELIGHT, 1, LAYER_TRIM - 0.04),
        ("BACK_LIGHT_MIDDLE", 3, 2, 0.0006, PART_BRAKELIGHT, 1, LAYER_TRIM - 0.04),
        ("BACK_ROUND_LIGHT", 3, 2, 0.0006, PART_BRAKELIGHT, 1, LAYER_TRIM - 0.05),
        ("BACK_ROUND_LIGHT_COVER", 3, 2, 0.0006, PART_BODY_STATIC, 1, LAYER_TRIM - 0.02),
        ("BACK_ROUND_LIGHT_MIDDLE", 3, 2, 0.0006, PART_BRAKELIGHT, 1, LAYER_TRIM - 0.05),
        ("back_light", 3, 2, 0.0010, PART_BRAKELIGHT, 1, LAYER_TRIM - 0.04),
        ("back_light.002", 3, 2, 0.0008, PART_REVERSE_LIGHT, 1, LAYER_TRIM - 0.05),
        ("back_light.003", 3, 2, 0.0008, PART_BLINKER_LEFT, 1, LAYER_TRIM - 0.05),
        ("Plane.012", 2, 1, 0.0008, PART_BODY_STATIC, 1, LAYER_TRIM - 0.05),
        ("SIDE_LIGHT", 3, 1, 0.0008, PART_BLINKER_LEFT, 1, LAYER_TRIM - 0.04),
        ("ENGINE_LOCKER_HANDLE", 3, 1, 0.0008, PART_BODY_STATIC, 1, LAYER_TRIM - 0.04),
        ("Plane.046", 3, 2, 0.0020, PART_BODY_STATIC, 1, LAYER_TRIM - 0.02),
        ("Plane.051", 1, 0, 0.0006, PART_BODY_STATIC, 1, LAYER_TRIM - 0.04),
        ("Plane.052", 1, 0, 0.0006, PART_BRAKELIGHT, 1, LAYER_TRIM - 0.05),
        ("Plane.041", 1, 0, 0.0006, PART_BLINKER_LEFT, 1, LAYER_TRIM - 0.05),
        # Protruding Wing Mirrors (Layer Mirrors)
        ("MIrror_", 3, 2, 0.0022, PART_BODY_STATIC, 0, LAYER_MIRRORS),
        ("mirror_big", 3, 2, 0.0012, PART_BODY_STATIC, 0, LAYER_MIRRORS - 0.04),
        # Interior Cabin Details (Layer Interior -> always behind exterior walls & glass!)
        ("Plane.036", 3, 2, 0.0022, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.25),  # Front red scalloped curtains
        ("dashboard", 4, 2, 0.0090, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.15),
        ("DASH", 3, 2, 0.0060, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.15),
        ("DASH_1", 3, 2, 0.0050, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.15),
        ("DASH_2", 4, 2, 0.0070, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.15),
        ("tissue", 3, 2, 0.0010, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.30),
        ("First_leather", 0, 0, 0.00001, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.20),
        ("Torus.011", 1, 0, 0.0000001, PART_STEERING_WHEEL, 2, LAYER_INTERIOR - 0.28),
        ("Cube.005", 2, 1, 0.0010, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.10),
        ("Cube.004", 2, 1, 0.0010, PART_BODY_STATIC, 2, LAYER_INTERIOR - 0.10),
        ("INSIDE_COVER", 3, 2, 0.0040, PART_BODY_STATIC, 2, LAYER_INTERIOR + 0.50),
    ]

    compiled_polys = []  # (part_id, cull_mode, rgba_tuple, normal_xyz, list_of_xyz_tuples, bias_dist)

    # Soft directional sunlight from top-front-left for natural 3D depth shading
    lx, ly, lz = -0.35, 0.80, 0.48
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    lx, ly, lz = lx / ln, ly / ln, lz / ln

    for oname, passes, merge_passes, min_area, base_part_id, cull_mode, layer_bias in target_specs:
        flist = list(obj_faces.get(oname, []))
        if not flist:
            for k in list(obj_faces.keys()):
                if k.lower() == oname.lower() or k.lower().startswith(oname.lower() + "."):
                    flist.extend(obj_faces[k])
        if not flist:
            continue
        if oname == "First_leather":
            flist = flist[::3]
        elif oname == "Torus.011":
            flist = flist[::4]
        is_livery = any(oname.lower().startswith(lr) for lr in livery_roots)
        for _ in range(passes):
            flist = unsubdivide_bfs(verts, flist, require_same_cat=is_livery)
        for _ in range(merge_passes):
            flist = merge_coplanar_quad_pairs(verts, flist, cos_thresh=0.972)

        kept_cnt = 0
        for fv, fuv, cat, mtl in flist:
            (nx, ny, nz), area = face_normal_and_area(verts, fv)
            if area < min_area and not (is_livery and cat == 1 and area >= 0.0009):
                continue

            cx = sum(verts[vi][0] for vi in fv) / len(fv)
            cy = sum(verts[vi][1] for vi in fv) / len(fv)
            cz = sum(verts[vi][2] for vi in fv) / len(fv)

            # For small trim/light meshes whose OBJ normals might be inverted, orient outward from bus center
            if cull_mode == 1 and oname not in ("main_body", "side_glass"):
                out_vec_x = cx
                out_vec_y = cy - 1.65
                out_vec_z = cz * 0.35
                if (nx * out_vec_x + ny * out_vec_y + nz * out_vec_z) < -0.05:
                    nx, ny, nz = -nx, -ny, -nz

            part_id = base_part_id
            if base_part_id == PART_BLINKER_LEFT:
                part_id = PART_BLINKER_LEFT if cx < 0.0 else PART_BLINKER_RIGHT

            u_avg = sum(p[0] for p in fuv) / len(fuv)
            v_avg = sum(p[1] for p in fuv) / len(fuv)
            bias = layer_bias

            if is_livery:
                rgba, _ = sample_livery_quad(fv, fuv)
            elif oname == "side_glass":
                rgba = (18, 25, 36, 245)
            elif oname == "BACK_GLASS_COVER":
                rgba = (16, 22, 30, 255)
            elif oname in ("Plane.036", "BACK_CURTAIN"):
                rgba = (218, 34, 38, 255)
            elif oname in ("upper_ac",):
                rgba = (228, 234, 242, 255)
            elif oname in ("MIrror_", "glass_rubber_front", "headlight", "BACK_ROUND_LIGHT_COVER", "Plane.046"):
                rgba = (24, 28, 35, 255)
            elif oname == "mirror_big":
                rgba = (56, 66, 82, 255)
            elif oname in ("headlight.001", "headlight.002", "HEADLIGHT"):
                rgba = (235, 245, 255, 255)
            elif oname in ("rm", "hino_logo", "wide_suspetion", "exausht_", "aluminium_desing", "ENGINE_LOCKER_HANDLE"):
                rgba = (226, 232, 240, 255)
            elif oname == "Plane.012":
                rgba = (250, 204, 21, 255)
            elif oname == "tissue":
                rgba = (235, 220, 195, 255)
            elif oname in ("dashboard", "DASH", "DASH_1", "DASH_2"):
                rgba = (32, 37, 46, 255)
            elif oname == "Torus.011":
                rgba = (26, 28, 34, 255)
            elif oname == "First_leather":
                rgba = (42, 48, 62, 255)
            elif oname in ("Cube.004", "Cube.005", "Cube.006"):
                rgba = (38, 48, 64, 255)
            elif oname == "INSIDE_COVER":
                rgba = (35, 38, 44, 255)
            elif part_id == PART_BRAKELIGHT:
                rgba = (215, 28, 32, 255)
            elif part_id == PART_REVERSE_LIGHT:
                rgba = (232, 238, 245, 255)
            elif part_id in (PART_BLINKER_LEFT, PART_BLINKER_RIGHT):
                rgba = (235, 145, 18, 255)
            else:
                rgba = (38, 42, 50, 255)

            ndotl = nx * lx + ny * ly + nz * lz
            shade = 0.82 + 0.18 * max(-0.55, min(1.0, ndotl))
            r_sh = max(0, min(255, int(rgba[0] * shade)))
            g_sh = max(0, min(255, int(rgba[1] * shade)))
            b_sh = max(0, min(255, int(rgba[2] * shade)))

            pts = [verts[vi] for vi in fv[:4]]
            compiled_polys.append(
                (part_id, cull_mode, (r_sh, g_sh, b_sh, rgba[3]), (nx, ny, nz), pts, bias)
            )
            kept_cnt += 1
        print(f"  {oname:24s}: kept {kept_cnt:4d} polys")

    # Reconstructed panoramic front windshield with destination LED header, sunband, and wiper blades
    # 1. Destination LED Display Header ("KERALA RTC SWIFT / SUPER FAST")
    dest_board_bg_l = [(-0.96, 2.86, 5.20), (0.00, 2.90, 5.23), (0.00, 3.16, 5.11), (-0.96, 3.10, 5.11)]
    dest_board_bg_r = [(0.00, 2.90, 5.23), (0.96, 2.86, 5.20), (0.96, 3.10, 5.11), (0.00, 3.16, 5.11)]
    dest_led_strip = [(-0.80, 2.92, 5.21), (0.80, 2.92, 5.21), (0.80, 3.08, 5.13), (-0.80, 3.08, 5.13)]
    compiled_polys.append((PART_BODY_STATIC, 0, (18, 22, 28, 255), (0.0, 0.45, 0.89), dest_board_bg_l, LAYER_GLASS - 0.05))
    compiled_polys.append((PART_BODY_STATIC, 0, (18, 22, 28, 255), (0.0, 0.45, 0.89), dest_board_bg_r, LAYER_GLASS - 0.05))
    compiled_polys.append((PART_HEADLIGHT, 0, (255, 195, 35, 255), (0.0, 0.45, 0.89), dest_led_strip, LAYER_GLASS - 0.06))

    # 2. Upper Sunband Tint Strip (Emerald/Smoked Anti-Glare Band)
    sunband_l = [(-1.00, 2.62, 5.30), (0.00, 2.65, 5.34), (0.00, 2.90, 5.23), (-0.96, 2.86, 5.20)]
    sunband_r = [(0.00, 2.65, 5.34), (1.00, 2.62, 5.30), (0.96, 2.86, 5.20), (0.00, 2.90, 5.23)]
    compiled_polys.append((PART_GLASS_SEMI, 1, (16, 44, 52, 210), (-0.15, 0.38, 0.91), sunband_l, LAYER_GLASS))
    compiled_polys.append((PART_GLASS_SEMI, 1, (16, 44, 52, 210), (0.15, 0.38, 0.91), sunband_r, LAYER_GLASS))

    # 3. Main Panoramic Solar Windshield Glass (Curved Left & Right Panels)
    windshield_l = [(-1.05, 1.55, 5.58), (0.00, 1.42, 5.74), (0.00, 2.65, 5.34), (-1.00, 2.62, 5.30)]
    windshield_r = [(0.00, 1.42, 5.74), (1.05, 1.55, 5.58), (1.00, 2.62, 5.30), (0.00, 2.65, 5.34)]
    compiled_polys.append((PART_GLASS_SEMI, 1, (26, 42, 58, 140), (-0.15, 0.32, 0.93), windshield_l, LAYER_GLASS))
    compiled_polys.append((PART_GLASS_SEMI, 1, (26, 42, 58, 140), (0.15, 0.32, 0.93), windshield_r, LAYER_GLASS))

    # 4. Front Windshield Wiper Blades (Articulated Black Rubber & Arms)
    wiper_driver = [(0.10, 1.48, 5.72), (0.88, 1.62, 5.56), (0.88, 1.66, 5.55), (0.10, 1.52, 5.71)]
    wiper_pass = [(-0.88, 1.62, 5.56), (-0.10, 1.48, 5.72), (-0.10, 1.52, 5.71), (-0.88, 1.66, 5.55)]
    compiled_polys.append((PART_BODY_STATIC, 1, (22, 24, 28, 255), (0.0, 0.32, 0.95), wiper_driver, LAYER_TRIM))
    compiled_polys.append((PART_BODY_STATIC, 1, (22, 24, 28, 255), (0.0, 0.32, 0.95), wiper_pass, LAYER_TRIM))

    # Reconstructed solid dark-smoke rear window glass (seals cabin completely from behind)
    # Spans X in [-1.08, 1.08], Y in [2.15, 3.16], Z = -6.185 (normal pointing -Z backward)
    rear_glass_quad = [
        (-1.08, 2.15, -6.185),
        (1.08, 2.15, -6.185),
        (1.04, 3.16, -6.185),
        (-1.04, 3.16, -6.185),
    ]
    compiled_polys.append(
        (PART_GLASS_SEMI, 0, (18, 24, 34, 255), (0.0, 0.0, -1.0), rear_glass_quad, LAYER_GLASS)
    )

    # Upper rear scalloped curtain trim (visible at rear window top)
    rear_curtain_quad = [
        (-1.03, 2.95, -6.175),
        (1.03, 2.95, -6.175),
        (1.03, 3.15, -6.175),
        (-1.03, 3.15, -6.175),
    ]
    compiled_polys.append(
        (PART_BODY_STATIC, 0, (198, 28, 36, 255), (0.0, 0.0, -1.0), rear_curtain_quad, LAYER_GLASS - 0.02)
    )

    # Solid dark undercarriage chassis floor spanning full length & width
    # Aligns flush with bottom rockers (Y = 0.44m) to completely seal the underside
    chassis_pan = [
        (-1.12, 0.44, -6.05),
        (1.12, 0.44, -6.05),
        (1.12, 0.44, 5.30),
        (-1.12, 0.44, 5.30),
    ]
    compiled_polys.append(
        (PART_BODY_STATIC, 0, (18, 20, 24, 255), (0.0, -1.0, 0.0), chassis_pan, LAYER_INTERIOR + 0.90)
    )

    # 3D Enclosed Wheel Well Boxes (Wheel Houses)
    # Completely seals each wheel arch so no far-side wheel or light can ever bleed through
    wheel_well_boxes = [
        # Front Left Wheel House (Inner wall X=-0.88m, Y=[0.35, 1.22], Z=[2.30, 3.70])
        ([(-0.88, 0.35, 2.25), (-0.88, 1.22, 2.35), (-0.88, 1.22, 3.65), (-0.88, 0.35, 3.75)], (-1.0, 0.0, 0.0)),
        ([(-1.25, 1.22, 2.35), (-0.88, 1.22, 2.35), (-0.88, 1.22, 3.65), (-1.25, 1.22, 3.65)], (0.0, -1.0, 0.0)),
        ([(-1.25, 0.44, 3.70), (-0.88, 0.44, 3.70), (-0.88, 1.22, 3.70), (-1.25, 1.22, 3.70)], (0.0, 0.0, -1.0)),
        ([(-1.25, 0.44, 2.30), (-1.25, 1.22, 2.30), (-0.88, 1.22, 2.30), (-0.88, 0.44, 2.30)], (0.0, 0.0, 1.0)),

        # Front Right Wheel House (Inner wall X=+0.88m, Y=[0.35, 1.22], Z=[2.30, 3.70])
        ([(0.88, 0.35, 2.25), (0.88, 0.35, 3.75), (0.88, 1.22, 3.65), (0.88, 1.22, 2.35)], (1.0, 0.0, 0.0)),
        ([(0.88, 1.22, 2.35), (1.25, 1.22, 2.35), (1.25, 1.22, 3.65), (0.88, 1.22, 3.65)], (0.0, -1.0, 0.0)),
        ([(0.88, 0.44, 3.70), (1.25, 0.44, 3.70), (1.25, 1.22, 3.70), (0.88, 1.22, 3.70)], (0.0, 0.0, -1.0)),
        ([(0.88, 0.44, 2.30), (0.88, 1.22, 2.30), (1.25, 1.22, 2.30), (1.25, 0.44, 2.30)], (0.0, 0.0, 1.0)),

        # Rear Left Wheel House (Inner wall X=-0.62m, Y=[0.35, 1.22], Z=[-3.50, -2.10])
        ([(-0.62, 0.35, -3.55), (-0.62, 1.22, -3.45), (-0.62, 1.22, -2.15), (-0.62, 0.35, -2.05)], (-1.0, 0.0, 0.0)),
        ([(-1.25, 1.22, -3.45), (-0.62, 1.22, -3.45), (-0.62, 1.22, -2.15), (-1.25, 1.22, -2.15)], (0.0, -1.0, 0.0)),
        ([(-1.25, 0.44, -2.10), (-0.62, 0.44, -2.10), (-0.62, 1.22, -2.10), (-1.25, 1.22, -2.10)], (0.0, 0.0, -1.0)),
        ([(-1.25, 0.44, -3.50), (-1.25, 1.22, -3.50), (-0.62, 1.22, -3.50), (-0.62, 0.44, -3.50)], (0.0, 0.0, 1.0)),

        # Rear Right Wheel House (Inner wall X=+0.62m, Y=[0.35, 1.22], Z=[-3.50, -2.10])
        ([(0.62, 0.35, -3.55), (0.62, 0.35, -2.05), (0.62, 1.22, -2.15), (0.62, 1.22, -3.45)], (1.0, 0.0, 0.0)),
        ([(0.62, 1.22, -3.45), (1.25, 1.22, -3.45), (1.25, 1.22, -2.15), (0.62, 1.22, -2.15)], (0.0, -1.0, 0.0)),
        ([(0.62, 0.44, -2.10), (1.25, 0.44, -2.10), (1.25, 1.22, -2.10), (0.62, 1.22, -2.10)], (0.0, 0.0, -1.0)),
        ([(0.62, 0.44, -3.50), (0.62, 1.22, -3.50), (1.25, 1.22, -3.50), (1.25, 0.44, -3.50)], (0.0, 0.0, 1.0)),
    ]
    for wh_pts, wh_norm in wheel_well_boxes:
        compiled_polys.append((PART_BODY_STATIC, 0, (18, 20, 24, 255), wh_norm, wh_pts, LAYER_INTERIOR + 0.85))

    # 6 Real 3D wheels from Mesh_Hino_Wheel_0..5.asset
    # Axle 1 (Front Steer): Z = +3.00m, recessed X = +-1.02m (centered in front wheel arch [2.45, 3.55])
    # Axle 2 (Rear Drive Axle, Dual Wheels): Z = -2.80m, outer X = +-1.02m, inner X = +-0.76m (centered in rear arch [-3.50, -2.15])
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
    print(f"  6 Real 3D Wheels        : kept {total_wheel_polys:4d} polys")
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
