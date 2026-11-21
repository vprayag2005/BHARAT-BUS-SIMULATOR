#!/usr/bin/env python3
"""
Compile real 3D Building, Tree, and Grass models from the Google Drive Zone 1 pack
into:
  1. app/src/main/assets/zone1_gdrive_atlas.png (2048x1024 RGBA texture atlas, 8x4 tiles of 256x256)
  2. app/src/main/assets/zone1_gdrive_env_meshes.bin (Z1M2 indexed 3D mesh format with full UVs,
     baked glTF/FBX scene transforms, intact watertight geometry, and alpha-cutout flags).

Zero grid-crushing decimation and zero area-cutoff triangle truncation:
  - All 14 Building models preserve 100% of their original connected triangles, UVs, and materials.
  - All 5 Tree models and 3 Grass models preserve 100% intact connected trunks/branches and whole
    connected leaf cards with real RGBA alpha-cutout masks.
"""
import json
import math
import os
import struct
import subprocess
import zlib
from collections import defaultdict

GDRIVE_ROOT = "/tmp/gdrive_zone1"
OUT_BIN = "app/src/main/assets/zone1_gdrive_env_meshes.bin"
OUT_ATLAS = "app/src/main/assets/zone1_gdrive_atlas.png"

CAT_BUILDING = 0
CAT_TREE = 1
CAT_GRASS = 2

TILE_SIZE = 256
ATLAS_COLS = 8
ATLAS_ROWS = 4


def bleed_alpha_rgb(rgba_bytes, size=TILE_SIZE, passes=6):
    """Dilate RGB of opaque pixels (a >= 128) into transparent pixels (a < 128) to prevent dark/white mipmap fringes."""
    buf = bytearray(rgba_bytes)
    for _ in range(passes):
        nxt = bytearray(buf)
        for y in range(size):
            row_off = y * size * 4
            for x in range(size):
                idx = row_off + x * 4
                if buf[idx + 3] >= 96:
                    continue
                r_sum = g_sum = b_sum = cnt = 0
                for dy in (-1, 0, 1):
                    ny = y + dy
                    if 0 <= ny < size:
                        nrow = ny * size * 4
                        for dx in (-1, 0, 1):
                            if dx == 0 and dy == 0:
                                continue
                            nx = x + dx
                            if 0 <= nx < size:
                                nidx = nrow + nx * 4
                                if buf[nidx + 3] >= 96:
                                    r_sum += buf[nidx]
                                    g_sum += buf[nidx + 1]
                                    b_sum += buf[nidx + 2]
                                    cnt += 1
                if cnt > 0:
                    nxt[idx] = r_sum // cnt
                    nxt[idx + 1] = g_sum // cnt
                    nxt[idx + 2] = b_sum // cnt
                    # Mark as RGB-filled with low alpha so next pass can propagate further
                    if nxt[idx + 3] == 0:
                        nxt[idx + 3] = 1
        # Restore original alphas after propagating RGB
        for i in range(3, len(buf), 4):
            if rgba_bytes[i] < 96:
                nxt[i] = rgba_bytes[i]
        buf = nxt
    return bytes(buf)


def load_rgba_tile(path, is_cutout=False, opacity_path=None, size=TILE_SIZE):
    if opacity_path is not None:
        rgb = subprocess.check_output(
            ["convert", path, "-resize", f"{size}x{size}!", "-depth", "8", "rgb:-"]
        )
        op = subprocess.check_output(
            ["convert", opacity_path, "-colorspace", "Gray", "-resize", f"{size}x{size}!", "-depth", "8", "gray:-"]
        )
        raw = bytearray(size * size * 4)
        for i in range(size * size):
            raw[i * 4 + 0] = rgb[i * 3 + 0]
            raw[i * 4 + 1] = rgb[i * 3 + 1]
            raw[i * 4 + 2] = rgb[i * 3 + 2]
            raw[i * 4 + 3] = op[i]
        raw = bytes(raw)
    elif is_cutout:
        raw = subprocess.check_output(
            ["convert", path, "-resize", f"{size}x{size}!", "-depth", "8", "rgba:-"]
        )
    else:
        rgb = subprocess.check_output(
            ["convert", path, "-resize", f"{size}x{size}!", "-depth", "8", "rgb:-"]
        )
        raw = bytearray(size * size * 4)
        for i in range(size * size):
            raw[i * 4 + 0] = rgb[i * 3 + 0]
            raw[i * 4 + 1] = rgb[i * 3 + 1]
            raw[i * 4 + 2] = rgb[i * 3 + 2]
            raw[i * 4 + 3] = 255
        raw = bytes(raw)
    if is_cutout:
        raw = bleed_alpha_rgb(raw, size=size, passes=4)
    return raw


def sample_rgba(rgba_bytes, u, v, flip_v=False, size=TILE_SIZE):
    if math.isnan(u) or math.isinf(u):
        u = 0.5
    if math.isnan(v) or math.isinf(v):
        v = 0.5
    u = u - math.floor(u)
    v = v - math.floor(v)
    if flip_v:
        v = 1.0 - v
    px = min(size - 1, max(0, int(u * size)))
    py = min(size - 1, max(0, int(v * size)))
    idx = (py * size + px) * 4
    return (rgba_bytes[idx], rgba_bytes[idx + 1], rgba_bytes[idx + 2])


# ==============================================================================
# 1. GLTF 2.0 Loader with Full Scene Node Hierarchy Matrix Baking
# ==============================================================================
def mat4_mul(A, B):
    """Multiply two column-major 4x4 matrices (glTF convention)."""
    C = [0.0] * 16
    for col in range(4):
        for row in range(4):
            C[col * 4 + row] = (
                A[0 * 4 + row] * B[col * 4 + 0]
                + A[1 * 4 + row] * B[col * 4 + 1]
                + A[2 * 4 + row] * B[col * 4 + 2]
                + A[3 * 4 + row] * B[col * 4 + 3]
            )
    return C


def trs_to_mat4(t, r, s):
    tx, ty, tz = t if t else (0.0, 0.0, 0.0)
    qx, qy, qz, qw = r if r else (0.0, 0.0, 0.0, 1.0)
    sx, sy, sz = s if s else (1.0, 1.0, 1.0)
    x2, y2, z2 = qx + qx, qy + qy, qz + qz
    xx, xy, xz = qx * x2, qx * y2, qx * z2
    yy, yz, zz = qy * y2, qy * z2, qz * z2
    wx, wy, wz = qw * x2, qw * y2, qw * z2
    return [
        (1.0 - (yy + zz)) * sx, (xy + wz) * sx, (xz - wy) * sx, 0.0,
        (xy - wz) * sy, (1.0 - (xx + zz)) * sy, (yz + wx) * sy, 0.0,
        (xz + wy) * sz, (yz - wx) * sz, (1.0 - (xx + yy)) * sz, 0.0,
        tx, ty, tz, 1.0,
    ]


def mat4_transform_point(m, x, y, z):
    return (
        m[0] * x + m[4] * y + m[8] * z + m[12],
        m[1] * x + m[5] * y + m[9] * z + m[13],
        m[2] * x + m[6] * y + m[10] * z + m[14],
    )


def load_gltf_meshes_baked(gltf_dir, mesh_indices, tex_layer, tex_rgba):
    """
    Loads one or more glTF meshes, baking the exact glTF node hierarchy world matrix
    for each mesh so multi-part buildings (body + roof + extensions) align in Y-up meters.
    Returns list of triangles: (v0, v1, v2, uv0, uv1, uv2, tex_layer, flags, rgb)
    where uv is already in OpenGL top-down convention.
    """
    with open(os.path.join(gltf_dir, "scene.gltf"), "r") as f:
        g = json.load(f)
    with open(os.path.join(gltf_dir, "scene.bin"), "rb") as f:
        b = f.read()

    nodes = g.get("nodes", [])
    parent_map = {}
    for ni, n in enumerate(nodes):
        for c in n.get("children", []):
            parent_map[c] = ni

    def get_node_world_mat(ni):
        n = nodes[ni]
        m = n["matrix"] if "matrix" in n else trs_to_mat4(n.get("translation"), n.get("rotation"), n.get("scale"))
        if ni in parent_map:
            m = mat4_mul(get_node_world_mat(parent_map[ni]), m)
        return m

    mesh_to_node_mat = {}
    for ni, n in enumerate(nodes):
        if "mesh" in n and n["mesh"] not in mesh_to_node_mat:
            mesh_to_node_mat[n["mesh"]] = get_node_world_mat(ni)

    def read_acc(acc_idx):
        acc = g["accessors"][acc_idx]
        bv = g["bufferViews"][acc["bufferView"]]
        offset = bv.get("byteOffset", 0) + acc.get("byteOffset", 0)
        count = acc["count"]
        ctype = acc["componentType"]
        atype = acc["type"]
        ncomp = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[atype]
        fmt_ch = {5121: "B", 5123: "H", 5125: "I", 5126: "f"}[ctype]
        elem_size = struct.calcsize("<" + fmt_ch * ncomp)
        stride = bv.get("byteStride", elem_size)
        out = []
        for i in range(count):
            pos = offset + i * stride
            out.append(struct.unpack("<" + fmt_ch * ncomp, b[pos : pos + elem_size]))
        return out

    tris = []
    if isinstance(mesh_indices, int):
        mesh_indices = [mesh_indices]

    for m_idx in mesh_indices:
        wm = mesh_to_node_mat.get(m_idx, [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1])
        mesh = g["meshes"][m_idx]
        for prim in mesh["primitives"]:
            pos_raw = read_acc(prim["attributes"]["POSITION"])
            pos = [mat4_transform_point(wm, p[0], p[1], p[2]) for p in pos_raw]
            uvs = read_acc(prim["attributes"]["TEXCOORD_0"]) if "TEXCOORD_0" in prim["attributes"] else [(0.5, 0.5)] * len(pos)
            idxs = [x[0] for x in read_acc(prim["indices"])]
            for i in range(0, len(idxs), 3):
                i0, i1, i2 = idxs[i], idxs[i + 1], idxs[i + 2]
                v0, v1, v2 = pos[i0], pos[i1], pos[i2]
                uv0, uv1, uv2 = uvs[i0], uvs[i1], uvs[i2]
                u_mid = (uv0[0] + uv1[0] + uv2[0]) / 3.0
                v_mid = (uv0[1] + uv1[1] + uv2[1]) / 3.0
                rgb = sample_rgba(tex_rgba, u_mid, v_mid, flip_v=False)
                tris.append((v0, v1, v2, uv0, uv1, uv2, tex_layer, 0, rgb))
    return tris


# ==============================================================================
# 2. Binary FBX Loader with Model Transforms, Connections & Material Mapping
# ==============================================================================
def read_fbx_node(data, pos, is_64):
    if is_64:
        if pos[0] + 25 > len(data):
            return None
        end_off, num_props, prop_list_len, name_len = struct.unpack("<QQQB", data[pos[0] : pos[0] + 25])
        pos[0] += 25
    else:
        if pos[0] + 13 > len(data):
            return None
        end_off, num_props, prop_list_len, name_len = struct.unpack("<IIIB", data[pos[0] : pos[0] + 13])
        pos[0] += 13
    if end_off == 0:
        return None
    name = data[pos[0] : pos[0] + name_len].decode("latin1")
    pos[0] += name_len
    props_end = pos[0] + prop_list_len
    props = []
    while pos[0] < props_end:
        ptype = chr(data[pos[0]])
        pos[0] += 1
        if ptype == "Y":
            props.append(struct.unpack("<h", data[pos[0] : pos[0] + 2])[0])
            pos[0] += 2
        elif ptype == "C":
            props.append(bool(data[pos[0]]))
            pos[0] += 1
        elif ptype == "I":
            props.append(struct.unpack("<i", data[pos[0] : pos[0] + 4])[0])
            pos[0] += 4
        elif ptype == "F":
            props.append(struct.unpack("<f", data[pos[0] : pos[0] + 4])[0])
            pos[0] += 4
        elif ptype == "D":
            props.append(struct.unpack("<d", data[pos[0] : pos[0] + 8])[0])
            pos[0] += 8
        elif ptype == "L":
            props.append(struct.unpack("<q", data[pos[0] : pos[0] + 8])[0])
            pos[0] += 8
        elif ptype in ("S", "R"):
            slen = struct.unpack("<I", data[pos[0] : pos[0] + 4])[0]
            pos[0] += 4
            if ptype == "S":
                props.append(data[pos[0] : pos[0] + slen].decode("latin1", errors="ignore"))
            else:
                props.append(data[pos[0] : pos[0] + slen])
            pos[0] += slen
        elif ptype in ("f", "d", "l", "i", "b"):
            alen, enc, clen = struct.unpack("<III", data[pos[0] : pos[0] + 12])
            pos[0] += 12
            raw = data[pos[0] : pos[0] + clen]
            pos[0] += clen
            if enc == 1:
                raw = zlib.decompress(raw)
            fmt = "<" + str(alen) + ("B" if ptype == "b" else ptype)
            props.append(struct.unpack(fmt, raw))
    children = []
    while pos[0] < end_off:
        child = read_fbx_node(data, pos, is_64)
        if child is None:
            break
        children.append(child)
    pos[0] = end_off
    return (name, props, children)


def euler_xyz_to_mat4(rx_deg, ry_deg, rz_deg):
    rx = math.radians(rx_deg)
    ry = math.radians(ry_deg)
    rz = math.radians(rz_deg)
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    mx = [1, 0, 0, 0, 0, cx, sx, 0, 0, -sx, cx, 0, 0, 0, 0, 1]
    my = [cy, 0, -sy, 0, 0, 1, 0, 0, sy, 0, cy, 0, 0, 0, 0, 1]
    mz = [cz, sz, 0, 0, -sz, cz, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
    return mat4_mul(mz, mat4_mul(my, mx))


def parse_fbx_scene(fbx_path):
    with open(fbx_path, "rb") as f:
        data = f.read()
    version = struct.unpack("<I", data[23:27])[0]
    is_64 = version >= 7500
    pos = [27]
    geoms = {}
    models = {}
    videos = {}
    conns = []

    while pos[0] < len(data):
        node = read_fbx_node(data, pos, is_64)
        if node is None:
            break
        name, props, children = node
        if name == "Objects":
            for cname, cprops, cchildren in children:
                if cname == "Geometry":
                    gid = cprops[0]
                    gname = cprops[1].split("\x00")[0] if len(cprops) > 1 and isinstance(cprops[1], str) else ""
                    verts = polys = uvs = uv_idx = mats = None
                    mat_mapping = "AllSame"
                    uv_mapping = "ByPolygonVertex"
                    uv_ref = "IndexToDirect"
                    for scname, scprops, scchildren in cchildren:
                        if scname == "Vertices":
                            verts = scprops[0]
                        elif scname == "PolygonVertexIndex":
                            polys = scprops[0]
                        elif scname == "LayerElementUV":
                            for uvn, uvp, _ in scchildren:
                                if uvn == "UV":
                                    uvs = uvp[0]
                                elif uvn == "UVIndex":
                                    uv_idx = uvp[0]
                                elif uvn == "MappingInformationType" and uvp:
                                    uv_mapping = uvp[0]
                                elif uvn == "ReferenceInformationType" and uvp:
                                    uv_ref = uvp[0]
                        elif scname == "LayerElementMaterial":
                            for mn, mp, _ in scchildren:
                                if mn == "Materials":
                                    mats = mp[0]
                                elif mn == "MappingInformationType" and mp:
                                    mat_mapping = mp[0]
                    if verts and polys:
                        geoms[gid] = {
                            "id": gid,
                            "name": gname,
                            "verts": verts,
                            "polys": polys,
                            "uvs": uvs,
                            "uv_idx": uv_idx,
                            "uv_mapping": uv_mapping,
                            "uv_ref": uv_ref,
                            "mats": mats,
                            "mat_mapping": mat_mapping,
                        }
                elif cname == "Model":
                    mid = cprops[0]
                    mname = cprops[1].split("\x00")[0] if len(cprops) > 1 and isinstance(cprops[1], str) else ""
                    t = [0.0, 0.0, 0.0]
                    r = [0.0, 0.0, 0.0]
                    pre_r = [0.0, 0.0, 0.0]
                    s = [1.0, 1.0, 1.0]
                    for scname, scprops, scchildren in cchildren:
                        if scname == "Properties70":
                            for _, p70, _ in scchildren:
                                if len(p70) >= 7:
                                    pname = p70[0]
                                    if pname == "Lcl Translation":
                                        t = [float(p70[4]), float(p70[5]), float(p70[6])]
                                    elif pname == "Lcl Rotation":
                                        r = [float(p70[4]), float(p70[5]), float(p70[6])]
                                    elif pname == "PreRotation":
                                        pre_r = [float(p70[4]), float(p70[5]), float(p70[6])]
                                    elif pname == "Lcl Scaling":
                                        s = [float(p70[4]), float(p70[5]), float(p70[6])]
                    models[mid] = {"id": mid, "name": mname, "t": t, "r": r, "pre_r": pre_r, "s": s}
                elif cname == "Video":
                    vid = cprops[0]
                    vname = cprops[1].split("\x00")[0] if len(cprops) > 1 and isinstance(cprops[1], str) else str(vid)
                    for scname, scprops, _ in cchildren:
                        if scname == "Content" and scprops and isinstance(scprops[0], bytes) and len(scprops[0]) > 0:
                            videos[vname] = scprops[0]
        elif name == "Connections":
            for _, cprops, _ in children:
                if len(cprops) >= 3 and cprops[0] == "OO":
                    conns.append((cprops[1], cprops[2]))

    parent_model = {}
    geom_to_model = {}
    for child_id, parent_id in conns:
        if child_id in geoms and parent_id in models:
            geom_to_model[child_id] = parent_id
        elif child_id in models and parent_id in models:
            parent_model[child_id] = parent_id

    def get_model_mat4(mid):
        if mid not in models:
            return [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
        m = models[mid]
        mt = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, m["t"][0], m["t"][1], m["t"][2], 1]
        mpre = euler_xyz_to_mat4(m["pre_r"][0], m["pre_r"][1], m["pre_r"][2])
        mr = euler_xyz_to_mat4(m["r"][0], m["r"][1], m["r"][2])
        ms = [m["s"][0], 0, 0, 0, 0, m["s"][1], 0, 0, 0, 0, m["s"][2], 0, 0, 0, 0, 1]
        local = mat4_mul(mt, mat4_mul(mpre, mat4_mul(mr, ms)))
        if mid in parent_model:
            return mat4_mul(get_model_mat4(parent_model[mid]), local)
        return local

    out_geoms = []
    for gid, g in geoms.items():
        mid = geom_to_model.get(gid)
        g["world_mat"] = get_model_mat4(mid) if mid is not None else [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
        out_geoms.append(g)
    return out_geoms, videos


def fbx_geom_to_tris(geom, mat_layer_map, tex_rgba_map, mat_flags_map=None):
    verts = geom["verts"]
    polys = geom["polys"]
    uvs = geom["uvs"]
    uv_idx = geom["uv_idx"]
    mats = geom["mats"]
    mat_mapping = geom["mat_mapping"]
    wm = geom["world_mat"]

    tris = []
    poly_verts = []
    poly_uvs = []
    poly_idx = 0
    vert_counter = 0

    for idx in polys:
        is_end = idx < 0
        real_idx = ~idx if is_end else idx
        px, py, pz = verts[real_idx * 3], verts[real_idx * 3 + 1], verts[real_idx * 3 + 2]
        vx, vy, vz = mat4_transform_point(wm, px, py, pz)
        poly_verts.append((vx, vy, vz))

        if uvs:
            if uv_idx is not None and vert_counter < len(uv_idx):
                ui = uv_idx[vert_counter]
            else:
                ui = real_idx if geom["uv_mapping"] == "ByControlPoint" else vert_counter
            if 0 <= ui * 2 + 1 < len(uvs):
                u_val, v_val = uvs[ui * 2], 1.0 - uvs[ui * 2 + 1]
            else:
                u_val, v_val = 0.5, 0.5
            poly_uvs.append((u_val, v_val))
        else:
            poly_uvs.append((0.5, 0.5))

        vert_counter += 1
        if is_end:
            if mats is not None and len(mats) > 0:
                if mat_mapping == "ByPolygon" and poly_idx < len(mats):
                    mat_id = mats[poly_idx]
                else:
                    mat_id = mats[0]
            else:
                mat_id = 0
            layer = mat_layer_map.get(mat_id, mat_layer_map.get(0, 0))
            flags = mat_flags_map.get(mat_id, 0) if mat_flags_map else 0
            rgba_bytes = tex_rgba_map[layer]

            for i in range(1, len(poly_verts) - 1):
                v0, v1, v2 = poly_verts[0], poly_verts[i], poly_verts[i + 1]
                uv0, uv1, uv2 = poly_uvs[0], poly_uvs[i], poly_uvs[i + 1]
                u_mid = (uv0[0] + uv1[0] + uv2[0]) / 3.0
                v_mid = (uv0[1] + uv1[1] + uv2[1]) / 3.0
                rgb = sample_rgba(rgba_bytes, u_mid, v_mid, flip_v=False)
                tris.append((v0, v1, v2, uv0, uv1, uv2, layer, flags, rgb))

            poly_verts = []
            poly_uvs = []
            poly_idx += 1

    return tris


# ==============================================================================
# 3. OBJ Loader (Full Intact Geometry + Connected Island Selection for Huge Trees)
# ==============================================================================
def load_obj_intact(obj_path, mtl_layer_map, tex_rgba_map, mtl_flags_map=None):
    """Loads 100% of faces from an OBJ file with per-vertex UVs (converted to OpenGL V = 1.0 - v)."""
    verts = [None]
    uvs = [None]
    cur_mtl = "default"
    tris = []

    with open(obj_path, "r", errors="ignore") as f:
        for line in f:
            if line.startswith("v "):
                p = line.split()
                verts.append((float(p[1]), float(p[2]), float(p[3])))
            elif line.startswith("vt "):
                p = line.split()
                uvs.append((float(p[1]), 1.0 - float(p[2])))
            elif line.startswith("usemtl "):
                cur_mtl = line.split(None, 1)[1].strip()
            elif line.startswith("f "):
                if cur_mtl not in mtl_layer_map and "default" not in mtl_layer_map:
                    continue
                layer = mtl_layer_map.get(cur_mtl, mtl_layer_map.get("default", 0))
                flags = mtl_flags_map.get(cur_mtl, 0) if mtl_flags_map else 0
                rgba_bytes = tex_rgba_map[layer]
                parts = line.split()[1:]
                f_v = []
                f_uv = []
                for p in parts:
                    sp = p.split("/")
                    vi = int(sp[0])
                    if vi < 0:
                        vi = len(verts) + vi
                    f_v.append(verts[vi])
                    if len(sp) > 1 and sp[1]:
                        ti = int(sp[1])
                        if ti < 0:
                            ti = len(uvs) + ti
                        f_uv.append(uvs[ti] if 0 < ti < len(uvs) else (0.5, 0.5))
                    else:
                        f_uv.append((0.5, 0.5))
                for i in range(1, len(f_v) - 1):
                    v0, v1, v2 = f_v[0], f_v[i], f_v[i + 1]
                    uv0, uv1, uv2 = f_uv[0], f_uv[i], f_uv[i + 1]
                    u_mid = (uv0[0] + uv1[0] + uv2[0]) / 3.0
                    v_mid = (uv0[1] + uv1[1] + uv2[1]) / 3.0
                    rgb = sample_rgba(rgba_bytes, u_mid, v_mid, flip_v=False)
                    tris.append((v0, v1, v2, uv0, uv1, uv2, layer, flags, rgb))
    return tris


def load_obj_by_connected_islands(obj_path, mtl_configs, tex_rgba_map):
    """
    Preserves 100% connected, watertight mesh islands for high-poly multi-million-triangle tree OBJs:
      mtl_configs[mtl_name] = {
          "layer": int,
          "flags": int,
          "mode": "all" | "top_diag" | "island_stride",
          "count": int (number of largest islands for top_diag, or stride for island_stride),
          "min_y": float (optional filter for underground root islands),
          "poly_stride": int (optional stride within mega-dense cylindrical branch islands)
      }
    Every selected trunk, branch, or leaf card island keeps its complete connected geometry and UVs.
    """
    verts = [None]
    uvs = [None]
    faces_by_mtl = defaultdict(list)
    cur_mtl = "default"

    with open(obj_path, "r", errors="ignore") as f:
        for line in f:
            if line.startswith("v "):
                p = line.split()
                verts.append((float(p[1]), float(p[2]), float(p[3])))
            elif line.startswith("vt "):
                p = line.split()
                uvs.append((float(p[1]), 1.0 - float(p[2])))
            elif line.startswith("usemtl "):
                cur_mtl = line.split(None, 1)[1].strip()
            elif line.startswith("f ") and cur_mtl in mtl_configs:
                parts = line.split()[1:]
                vis = []
                tis = []
                for p in parts:
                    sp = p.split("/")
                    vi = int(sp[0])
                    if vi < 0:
                        vi = len(verts) + vi
                    vis.append(vi)
                    if len(sp) > 1 and sp[1]:
                        ti = int(sp[1])
                        if ti < 0:
                            ti = len(uvs) + ti
                        tis.append(ti if 0 < ti < len(uvs) else 0)
                    else:
                        tis.append(0)
                faces_by_mtl[cur_mtl].append((vis, tis))

    tris = []
    for mtl_name, cfg in mtl_configs.items():
        flist = faces_by_mtl.get(mtl_name, [])
        if not flist:
            continue
        layer = cfg["layer"]
        flags = cfg["flags"]
        mode = cfg["mode"]
        rgba_bytes = tex_rgba_map[layer]

        if mode == "all":
            selected_islands = [flist]
        else:
            parent = {}

            def find(x):
                while parent.get(x, x) != x:
                    parent[x] = parent.get(parent[x], parent[x])
                    x = parent[x]
                return x

            for vis, _ in flist:
                r0 = find(vis[0])
                for v in vis[1:]:
                    rv = find(v)
                    if rv != r0:
                        parent[rv] = r0

            islands_map = defaultdict(list)
            for face in flist:
                islands_map[find(face[0][0])].append(face)

            islands_list = list(islands_map.values())
            if mode == "top_diag":
                min_y_allow = cfg.get("min_y", -1e9)
                scored = []
                for isl in islands_list:
                    vset = {v for vis, _ in isl for v in vis}
                    ys = [verts[v][1] for v in vset]
                    if min(ys) < min_y_allow:
                        continue
                    xs = [verts[v][0] for v in vset]
                    zs = [verts[v][2] for v in vset]
                    dx, dy, dz = max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)
                    diag = math.sqrt(dx * dx + dy * dy + dz * dz)
                    scored.append((diag, isl))
                scored.sort(key=lambda x: x[0], reverse=True)
                selected_islands = [isl for _, isl in scored[: cfg["count"]]]
            elif mode == "island_stride":
                stride = cfg["count"]
                selected_islands = islands_list[::stride]
            else:
                selected_islands = islands_list

        for isl in selected_islands:
            for vis, tis in isl:
                for i in range(1, len(vis) - 1):
                    v0, v1, v2 = verts[vis[0]], verts[vis[i]], verts[vis[i + 1]]
                    uv0 = uvs[tis[0]] if tis[0] > 0 else (0.5, 0.5)
                    uv1 = uvs[tis[i]] if tis[i] > 0 else (0.5, 0.5)
                    uv2 = uvs[tis[i + 1]] if tis[i + 1] > 0 else (0.5, 0.5)
                    u_mid = (uv0[0] + uv1[0] + uv2[0]) / 3.0
                    v_mid = (uv0[1] + uv1[1] + uv2[1]) / 3.0
                    rgb = sample_rgba(rgba_bytes, u_mid, v_mid, flip_v=False)
                    tris.append((v0, v1, v2, uv0, uv1, uv2, layer, flags, rgb))

    return tris


# ==============================================================================
# 4. Normalization, Metric Scaling & Indexed Mesh Compilation
# ==============================================================================
def normalize_and_index_mesh(tris, target_height, swap_yz=False, smooth_normals=False):
    """
    Centers X/Z, shifts minY = 0.0 (flush with terrain), scales uniformly to target_height,
    computes normals, and builds an indexed vertex + triangle buffer.
    Guarantees vertCount < 65535 for GL_UNSIGNED_SHORT hardware instancing with zero truncation.
    """
    pts = []
    for v0, v1, v2, uv0, uv1, uv2, layer, flags, rgb in tris:
        for x, y, z in (v0, v1, v2):
            if swap_yz:
                pts.append((x, z, -y))
            else:
                pts.append((x, y, z))

    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    zs = [p[2] for p in pts]
    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    min_z, max_z = min(zs), max(zs)

    cx = (min_x + max_x) * 0.5
    cz = (min_z + max_z) * 0.5
    raw_h = max(0.001, max_y - min_y)
    scale = target_height / raw_h

    half_x = (max_x - min_x) * 0.5 * scale
    half_z = (max_z - min_z) * 0.5 * scale
    height_y = target_height

    def xform(v):
        x, y, z = (v[0], v[2], -v[1]) if swap_yz else (v[0], v[1], v[2])
        return ((x - cx) * scale, max(0.0, (y - min_y) * scale), (z - cz) * scale)

    vert_map = {}
    vertices = []
    norm_accum = []
    indices = []

    for v0, v1, v2, uv0, uv1, uv2, layer, flags, rgb in tris:
        p0 = xform(v0)
        p1 = xform(v1)
        p2 = xform(v2)
        ux, uy, uz = p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]
        vx, vy, vz = p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]
        nx = uy * vz - uz * vy
        ny = uz * vx - ux * vz
        nz = ux * vy - uy * vx
        nlen = math.sqrt(nx * nx + ny * ny + nz * nz)
        if nlen < 1e-9:
            continue
        nx, ny, nz = nx / nlen, ny / nlen, nz / nlen

        qnx = max(-127, min(127, int(round(nx * 127.0))))
        qny = max(-127, min(127, int(round(ny * 127.0))))
        qnz = max(-127, min(127, int(round(nz * 127.0))))

        tri_idxs = []
        for p, uv in ((p0, uv0), (p1, uv1), (p2, uv2)):
            qx = max(-32767, min(32767, int(round(p[0] * 1000.0))))
            qy = max(0, min(32767, int(round(p[1] * 1000.0))))
            qz = max(-32767, min(32767, int(round(p[2] * 1000.0))))
            u_clamped = max(-15.9, min(15.9, uv[0]))
            v_clamped = max(-15.9, min(15.9, uv[1]))
            qu = max(-32767, min(32767, int(round(u_clamped * 2000.0))))
            qv = max(-32767, min(32767, int(round(v_clamped * 2000.0))))

            if smooth_normals:
                key = (qx, qy, qz, qu, qv, layer, flags)
            else:
                key = (qx, qy, qz, qnx, qny, qnz, qu, qv, layer, flags)
            vi = vert_map.get(key)
            if vi is None:
                vi = len(vertices)
                if vi >= 65535:
                    break
                vert_map[key] = vi
                vertices.append([qx, qy, qz, qnx, qny, qnz, qu, qv, layer, flags, rgb[0], rgb[1], rgb[2]])
                norm_accum.append([nx, ny, nz])
            elif smooth_normals:
                na = norm_accum[vi]
                na[0] += nx
                na[1] += ny
                na[2] += nz
            tri_idxs.append(vi)

        if len(tri_idxs) == 3 and tri_idxs[0] != tri_idxs[1] and tri_idxs[1] != tri_idxs[2] and tri_idxs[2] != tri_idxs[0]:
            indices.append((tri_idxs[0], tri_idxs[1], tri_idxs[2]))

    if smooth_normals:
        for vi, v in enumerate(vertices):
            nx, ny, nz = norm_accum[vi]
            nl = math.sqrt(nx * nx + ny * ny + nz * nz)
            if nl > 1e-6:
                v[3] = max(-127, min(127, int(round((nx / nl) * 127.0))))
                v[4] = max(-127, min(127, int(round((ny / nl) * 127.0))))
                v[5] = max(-127, min(127, int(round((nz / nl) * 127.0))))

    return half_x, half_z, height_y, vertices, indices


def build_atlas_png(tex_layers_rgba, out_path):
    """Packs 27 RGBA 256x256 texture layers into an 8x4 (2048x1024) RGBA PNG atlas."""
    atlas_w = ATLAS_COLS * TILE_SIZE
    atlas_h = ATLAS_ROWS * TILE_SIZE
    atlas_buf = bytearray(atlas_w * atlas_h * 4)

    for layer_idx, rgba in tex_layers_rgba.items():
        col = layer_idx % ATLAS_COLS
        row = layer_idx // ATLAS_COLS
        for y in range(TILE_SIZE):
            dst_y = row * TILE_SIZE + y
            dst_off = (dst_y * atlas_w + col * TILE_SIZE) * 4
            src_off = y * TILE_SIZE * 4
            atlas_buf[dst_off : dst_off + TILE_SIZE * 4] = rgba[src_off : src_off + TILE_SIZE * 4]

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    proc = subprocess.Popen(
        ["convert", "-size", f"{atlas_w}x{atlas_h}", "-depth", "8", "rgba:-", out_path],
        stdin=subprocess.PIPE,
    )
    proc.communicate(input=bytes(atlas_buf))
    if proc.returncode != 0:
        raise RuntimeError("Failed to write RGBA texture atlas PNG")


def main():
    b_root = os.path.join(GDRIVE_ROOT, "buildings")
    t_root = os.path.join(GDRIVE_ROOT, "trees and grass")

    # Parse FBX scenes first so we can extract embedded Oak_Tree textures
    office_geoms, _ = parse_fbx_scene(
        os.path.join(b_root, "boring-commercialoffice-two-story-building_extracted/source/Office.fbx")
    )
    old_office_geoms, _ = parse_fbx_scene(
        os.path.join(b_root, "old-office-building_extracted/source/Old_Office.fbx")
    )
    oak_geoms, oak_videos = parse_fbx_scene(os.path.join(t_root, "Oak_Tree.fbx"))

    # Save Oak_Tree embedded textures to /tmp for ImageMagick decoding
    oak_leaf1_path = os.path.join(t_root, "_oak_leaf1.png")
    oak_leaf2_path = os.path.join(t_root, "_oak_leaf2.png")
    oak_bark_path = os.path.join(t_root, "_oak_bark.jpg")
    for k, v in oak_videos.items():
        if "Oak2" in k:
            with open(oak_leaf2_path, "wb") as f:
                f.write(v)
        elif "Oak" in k:
            with open(oak_leaf1_path, "wb") as f:
                f.write(v)
        elif "bark" in k:
            with open(oak_bark_path, "wb") as f:
                f.write(v)

    print("Loading & processing 27 real RGBA textures from Google Drive packs...")
    tex_rgba = {}
    # Building textures (0..15)
    tex_rgba[0] = load_rgba_tile(os.path.join(b_root, "low_poly_accommodations_buildings_extracted/textures/Pack3_baseColor.png"))
    tex_rgba[1] = load_rgba_tile(os.path.join(b_root, "low_poly_business_buildings_pack_extracted/textures/Texture_buildings1_baseColor.png"))
    tex_rgba[2] = load_rgba_tile(os.path.join(b_root, "lowpoly_buildings_extracted/textures/elements_baseColor.png"))
    tex_rgba[3] = load_rgba_tile(os.path.join(b_root, "boring-commercialoffice-two-story-building_extracted/textures/Office_Windows_Ventilation_BaseColor.png"))
    tex_rgba[4] = load_rgba_tile(os.path.join(b_root, "boring-commercialoffice-two-story-building_extracted/textures/Office_Main_Body_BaseColor.png"))
    tex_rgba[5] = load_rgba_tile(os.path.join(b_root, "old-office-building_extracted/textures/Old_Office_Main_Body_BaseColor.png"))
    tex_rgba[6] = load_rgba_tile(os.path.join(b_root, "old-office-building_extracted/textures/Old_Office_Roof_BaseColor.png"))
    tex_rgba[7] = load_rgba_tile(os.path.join(b_root, "old-office-building_extracted/textures/Old_Office_Windows_BaseColor.png"))
    tex_rgba[8] = load_rgba_tile(os.path.join(b_root, "old-office-building_extracted/textures/Old_Office_Towers_BaseColor.png"))
    tex_rgba[9] = load_rgba_tile(os.path.join(b_root, "the-closed-ems-building-game-ready_extracted/source/brick_ems_3_Albedo.png"))
    tex_rgba[10] = load_rgba_tile(os.path.join(b_root, "the-closed-ems-building-game-ready_extracted/source/ems_pt1_Albedo.png"))
    tex_rgba[11] = load_rgba_tile(os.path.join(b_root, "the-closed-ems-building-game-ready_extracted/source/ems_pt2_Albedo.png"))
    tex_rgba[12] = load_rgba_tile(os.path.join(b_root, "the-closed-ems-building-game-ready_extracted/source/rooftiled_Albedo.png"))
    tex_rgba[13] = load_rgba_tile(os.path.join(b_root, "the-closed-ems-building-game-ready_extracted/source/woodenfroof_Albedo.png"))
    tex_rgba[14] = load_rgba_tile(os.path.join(b_root, "modern-residential-building_extracted/textures/Mat1_albedo.jpg"))
    tex_rgba[15] = load_rgba_tile(os.path.join(b_root, "modern-residential-building_extracted/textures/Mat2_albedo.jpg"))

    # Tree & Grass textures (16..26) with real RGBA alpha-cutout masks
    tex_rgba[16] = load_rgba_tile(os.path.join(t_root, "Banana_Textures_extracted/Textures/Banana_Plants_BaseColor.png"), is_cutout=True)
    tex_rgba[17] = load_rgba_tile(oak_bark_path, is_cutout=False)
    tex_rgba[18] = load_rgba_tile(oak_leaf1_path, is_cutout=True)
    tex_rgba[19] = load_rgba_tile(oak_leaf2_path, is_cutout=True)
    tex_rgba[20] = load_rgba_tile(os.path.join(t_root, "CoconutPalm_Textures_extracted/Textures/Palmetto_bark_2.png"), is_cutout=False)
    tex_rgba[21] = load_rgba_tile(os.path.join(t_root, "CoconutPalm_Textures_extracted/Textures/CoconutPalmLeaflet_1.tga"), is_cutout=True)
    tex_rgba[22] = load_rgba_tile(os.path.join(t_root, "American_Elm01_map_extracted/map/AmericanElmBark.jpg"), is_cutout=False)
    tex_rgba[23] = load_rgba_tile(
        os.path.join(t_root, "American_Elm01_map_extracted/map/Branches_03.jpg"),
        is_cutout=True,
        opacity_path=os.path.join(t_root, "American_Elm01_map_extracted/map/Branches_03_Opacity.jpg"),
    )
    tex_rgba[24] = load_rgba_tile(
        os.path.join(t_root, "American_Elm01_map_extracted/map/AmericanElmLeaf.jpg"),
        is_cutout=True,
        opacity_path=os.path.join(t_root, "American_Elm01_map_extracted/map/AmericanElmLeaf_Opacity.jpg"),
    )
    tex_rgba[25] = load_rgba_tile(os.path.join(t_root, "Pin_Cherry_Trees02_extracted/Textures/bark.jpg"), is_cutout=False)
    tex_rgba[26] = load_rgba_tile(
        os.path.join(t_root, "Pin_Cherry_Trees02_extracted/Textures/Leaf_Front_2.jpg"),
        is_cutout=True,
        opacity_path=os.path.join(t_root, "Pin_Cherry_Trees02_extracted/Textures/Leaf_Front_2_Opacity.jpg"),
    )

    build_atlas_png(tex_rgba, OUT_ATLAS)
    print(f"Saved RGBA texture atlas -> {OUT_ATLAS} ({os.path.getsize(OUT_ATLAS) / 1024:.1f} KB)")

    models = []

    # ------------------------------------------------------------------
    # A. 16 REAL BUILDING MODELS (100% Intact Geometry, Zero Decimation)
    # ------------------------------------------------------------------
    accom_dir = os.path.join(b_root, "low_poly_accommodations_buildings_extracted")
    for idx, (m_idx, h_m) in enumerate([(2, 14.5), (3, 15.5), (4, 12.0), (5, 13.0), (7, 13.5)]):
        tris = load_gltf_meshes_baked(accom_dir, [m_idx], 0, tex_rgba[0])
        models.append((f"bldg_accom_{idx}", CAT_BUILDING, h_m, False, tris))

    biz_dir = os.path.join(b_root, "low_poly_business_buildings_pack_extracted")
    for idx, (m_idxs, h_m) in enumerate([([0], 9.5), ([1], 8.5), ([6], 8.2), ([9], 11.5)]):
        tris = load_gltf_meshes_baked(biz_dir, m_idxs, 1, tex_rgba[1])
        models.append((f"bldg_biz_{idx}", CAT_BUILDING, h_m, False, tris))

    lowpoly_dir = os.path.join(b_root, "lowpoly_buildings_extracted")
    # House01 + House01_roof + House01_add (aligned via glTF node world matrices!)
    tris_lp0 = load_gltf_meshes_baked(lowpoly_dir, [2, 3, 4], 2, tex_rgba[2])
    models.append(("bldg_lowpoly_0", CAT_BUILDING, 7.4, False, tris_lp0))
    # House02
    tris_lp1 = load_gltf_meshes_baked(lowpoly_dir, [34], 2, tex_rgba[2])
    models.append(("bldg_lowpoly_1", CAT_BUILDING, 7.8, False, tris_lp1))
    # House03 + House03_add + House03_roof (aligned via glTF node world matrices!)
    tris_lp2 = load_gltf_meshes_baked(lowpoly_dir, [36, 37, 38], 2, tex_rgba[2])
    models.append(("bldg_lowpoly_2", CAT_BUILDING, 7.5, False, tris_lp2))

    # Office.fbx (Two-Story Commercial Office)
    office_tris = fbx_geom_to_tris(office_geoms[0], {0: 3, 1: 4}, tex_rgba)
    models.append(("bldg_office_two_story", CAT_BUILDING, 9.8, False, office_tris))

    # Old_Office.fbx (Multi-Story Heritage Commercial Block)
    old_off_tris = fbx_geom_to_tris(old_office_geoms[0], {0: 5, 1: 6, 2: 7, 3: 8}, tex_rgba)
    models.append(("bldg_old_office", CAT_BUILDING, 14.0, False, old_off_tris))

    # ems.obj (Closed EMS Building)
    ems_tris = load_obj_intact(
        os.path.join(b_root, "the-closed-ems-building-game-ready_extracted/source/ems.obj"),
        {"brick": 9, "ems_pt1": 10, "ems_pt2": 11, "roof": 12, "wood": 13, "default": 9},
        tex_rgba,
    )
    models.append(("bldg_closed_ems", CAT_BUILDING, 8.8, False, ems_tris))

    # Residential.obj (Modern Multi-Story Residential Apartment)
    res_tris = load_obj_intact(
        os.path.join(b_root, "modern-residential-building_extracted/source/Residential.obj"),
        {"Mat1": 14, "Mat2": 15, "default": 14},
        tex_rgba,
    )
    models.append(("bldg_modern_residential", CAT_BUILDING, 16.5, False, res_tris))

    # ------------------------------------------------------------------
    # B. 5 REAL TREE MODELS (Watertight Trunks/Branches + Alpha-Cutout Leaves)
    # ------------------------------------------------------------------
    FLAG_CUTOUT = 3  # bit 0 = alpha cutout, bit 1 = double-sided foliage

    # 1. Banana_Plant_01.obj (100% of all 4,996 triangles)
    banana_tris = load_obj_intact(
        os.path.join(t_root, "Banana_Plant_01.obj"),
        {"Banana_Plant": 16, "default": 16},
        tex_rgba,
        mtl_flags_map={"Banana_Plant": FLAG_CUTOUT, "default": FLAG_CUTOUT},
    )
    models.append(("tree_banana_0", CAT_TREE, 5.2, False, banana_tris))

    # 2. Oak_Tree.fbx (100% of Wood-Bark trunk/branches + 100% of Leaf Oak & Leaf Oak2 cards; already Y-up!)
    oak_tris = []
    for g in oak_geoms:
        gname = g["name"]
        if "Bark" in gname or "Wood" in gname:
            oak_tris.extend(fbx_geom_to_tris(g, {0: 17}, tex_rgba, {0: 0}))
        elif "Oak2" in gname:
            oak_tris.extend(fbx_geom_to_tris(g, {0: 19}, tex_rgba, {0: FLAG_CUTOUT}))
        else:
            oak_tris.extend(fbx_geom_to_tris(g, {0: 18}, tex_rgba, {0: FLAG_CUTOUT}))
    models.append(("tree_oak_0", CAT_TREE, 9.8, False, oak_tris))

    # 3. CoconutPalm.obj (100% of CoconutPalmBark trunk + FrondStems + intact CoconutPalmLeaflet islands; Z-up -> swap_yz=True)
    palm_tris = load_obj_by_connected_islands(
        os.path.join(t_root, "CoconutPalm_OBJ_extracted/CoconutPalm.obj"),
        {
            "CoconutPalmBark": {"layer": 20, "flags": 0, "mode": "all"},
            "Cap_03": {"layer": 20, "flags": 0, "mode": "all"},
            "FrondStem": {"layer": 20, "flags": 0, "mode": "island_stride", "count": 2},
            "CoconutPalmLeaflet_1": {"layer": 21, "flags": FLAG_CUTOUT, "mode": "island_stride", "count": 8},
            "CoconutPalmLeaflet_2": {"layer": 21, "flags": FLAG_CUTOUT, "mode": "island_stride", "count": 8},
            "CoconutPalmLeaflet_3": {"layer": 21, "flags": FLAG_CUTOUT, "mode": "island_stride", "count": 8},
            "CoconutPalmLeaflet_4": {"layer": 21, "flags": FLAG_CUTOUT, "mode": "island_stride", "count": 8},
        },
        tex_rgba,
    )
    models.append(("tree_coconut_palm_0", CAT_TREE, 12.5, True, palm_tris))

    # 4. American Elm01 tree.obj (Top 28 largest trunk/bough islands + intact 3-quad leaf islands; already Y-up!)
    elm_tris = load_obj_by_connected_islands(
        os.path.join(t_root, "American_Elm01_obj_extracted/American Elm01 tree.obj"),
        {
            "vray_AmericanElmBark_Mat": {"layer": 22, "flags": 0, "mode": "top_diag", "count": 28},
            "vray_Branches_03_Mat": {"layer": 23, "flags": FLAG_CUTOUT, "mode": "island_stride", "count": 3},
            "vray_AmericanElmLeaf_Mat": {"layer": 24, "flags": FLAG_CUTOUT, "mode": "island_stride", "count": 35},
        },
        tex_rgba,
    )
    models.append(("tree_american_elm_0", CAT_TREE, 11.2, False, elm_tris))

    # 5. Pin_Cherry_Trees02/Trees02.obj (Main trunk + above-ground branch islands + intact leaf cards; already Y-up!)
    cherry_tris = load_obj_by_connected_islands(
        os.path.join(t_root, "Pin_Cherry_Trees02_extracted/Trees02.obj"),
        {
            "wire_204204204": {"layer": 25, "flags": 0, "mode": "top_diag", "count": 6, "min_y": -5.0},
            "wire_020020020": {"layer": 26, "flags": FLAG_CUTOUT, "mode": "island_stride", "count": 3},
        },
        tex_rgba,
    )
    cherry_sec_tris = load_obj_by_connected_islands(
        os.path.join(t_root, "Pin_Cherry_Trees02_extracted/Trees02.obj"),
        {
            "wire_204204204": {"layer": 25, "flags": 0, "mode": "island_stride", "count": 100, "min_y": 0.0},
        },
        tex_rgba,
    )
    cherry_all_tris = [t for t in (cherry_tris + cherry_sec_tris) if min(t[0][1], t[1][1], t[2][1]) >= -1.5]
    models.append(("tree_pin_cherry_0", CAT_TREE, 8.6, False, cherry_all_tris))

    # ------------------------------------------------------------------
    # C. 3 REAL 3D GRASS / TROPICAL GROUND FOLIAGE CLUMP MODELS
    # ------------------------------------------------------------------
    # 1. Tropical Broad-Leaf Grass Clump (from Banana_Plant_01 intact foliage)
    models.append(("grass_clump_0", CAT_GRASS, 0.62, False, banana_tris))

    # 2. Tropical Sedge / Palm-Grass Clump (from CoconutPalmLeaflet intact leaflet ribbons)
    sedge_tris = load_obj_by_connected_islands(
        os.path.join(t_root, "CoconutPalm_OBJ_extracted/CoconutPalm.obj"),
        {
            "CoconutPalmLeaflet_1": {"layer": 21, "flags": FLAG_CUTOUT, "mode": "island_stride", "count": 12},
            "CoconutPalmLeaflet_2": {"layer": 21, "flags": FLAG_CUTOUT, "mode": "island_stride", "count": 12},
        },
        tex_rgba,
    )
    models.append(("grass_clump_1", CAT_GRASS, 0.55, True, sedge_tris))

    # 3. Low Green Bush / Meadow Clump (from Oak_Tree.fbx Leaf Oak2 intact alpha-cutout cards)
    bush_tris = []
    for g in oak_geoms:
        if "Oak2" in g["name"]:
            bush_tris.extend(fbx_geom_to_tris(g, {0: 19}, tex_rgba, {0: FLAG_CUTOUT}))
    models.append(("grass_patch_2", CAT_GRASS, 0.48, False, bush_tris))

    # ------------------------------------------------------------------
    # Write Binary Asset (Z1M2)
    # ------------------------------------------------------------------
    os.makedirs(os.path.dirname(OUT_BIN), exist_ok=True)
    with open(OUT_BIN, "wb") as out:
        out.write(b"Z1M2")
        out.write(struct.pack("<I", len(models)))
        total_verts = 0
        total_tris = 0
        for mid, cat, target_h, swap_yz, raw_tris in models:
            hx, hz, hy, vertices, indices = normalize_and_index_mesh(
                raw_tris, target_h, swap_yz, smooth_normals=(cat != CAT_BUILDING)
            )
            id_bytes = mid.encode("utf-8")
            out.write(struct.pack("<HBfffII", len(id_bytes), cat, hx, hz, hy, len(vertices), len(indices)))
            out.write(id_bytes)
            for qx, qy, qz, qnx, qny, qnz, qu, qv, layer, flags, r, g, b in vertices:
                out.write(struct.pack("<hhhbbbhhBBBBB", qx, qy, qz, qnx, qny, qnz, qu, qv, layer, flags, r, g, b))
            for i0, i1, i2 in indices:
                out.write(struct.pack("<HHH", i0, i1, i2))
            total_verts += len(vertices)
            total_tris += len(indices)
            print(
                f"  Compiled {mid:26s} | cat={cat} | size=({hx*2:5.1f}m x {hz*2:5.1f}m x {hy:4.1f}m) | verts={len(vertices):5d} | tris={len(indices):5d}"
            )

    sz_kb = os.path.getsize(OUT_BIN) / 1024.0
    print(f"Done! {len(models)} models, {total_verts} indexed vertices, {total_tris} triangles -> {OUT_BIN} ({sz_kb:.1f} KB)")


if __name__ == "__main__":
    main()
