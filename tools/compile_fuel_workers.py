#!/usr/bin/env python3
"""
Compiles both Google Drive rigged FBX fuel station worker human characters into:
  - app/src/main/assets/thrissur_fuel_workers.bin (FWK1 skinned 17-bone mesh format)
  - app/src/main/assets/thrissur_fuel_workers_atlas.png (2048x512 = 8x2 grid of 256x256 RGBA tiles)

Worker 0 (Fuel Worker 1): Petrol Bay Attendant (`indian man in kurta (1).fbx`)
Worker 1 (Fuel Worker 2): Diesel Bay Attendant (`indian man in kurta (2).zip -> model.fbx`)
"""
import math
import os
import struct
import subprocess
import zlib
from collections import defaultdict

BONE_NAMES = [
    "Hips",          # 0
    "Spine",         # 1
    "Spine2",        # 2
    "Neck",          # 3
    "Head",          # 4
    "LeftArm",       # 5
    "LeftForeArm",   # 6
    "LeftHand",      # 7
    "RightArm",      # 8
    "RightForeArm",  # 9
    "RightHand",     # 10
    "LeftUpLeg",     # 11
    "LeftLeg",       # 12
    "LeftFoot",      # 13
    "RightUpLeg",    # 14
    "RightLeg",      # 15
    "RightFoot",     # 16
]

def map_fbx_bone_to_17(bone_name: str) -> int:
    bn = bone_name.strip()
    if bn == "Hips": return 0
    if bn == "Spine": return 1
    if bn in ("Spine1", "Spine2"): return 2
    if bn.startswith("Neck"): return 3
    if bn in ("Head", "LeftEye", "RightEye", "HeadTop_End"): return 4
    if bn in ("LeftShoulder", "LeftArm"): return 5
    if bn.startswith("LeftForeArm"): return 6
    if bn.startswith("LeftHand"): return 7
    if bn in ("RightShoulder", "RightArm"): return 8
    if bn.startswith("RightForeArm"): return 9
    if bn.startswith("RightHand"): return 10
    if bn == "LeftUpLeg": return 11
    if bn == "LeftLeg": return 12
    if bn in ("LeftFoot", "LeftToeBase", "LeftToe_End"): return 13
    if bn == "RightUpLeg": return 14
    if bn == "RightLeg": return 15
    if bn in ("RightFoot", "RightToeBase", "RightToe_End"): return 16
    return 0


def load_fbx(path: str):
    with open(path, "rb") as f:
        data = f.read()
    ver = struct.unpack_from("<I", data, 23)[0]
    is_64 = ver >= 7500
    pos = 27

    def read_prop(p):
        t = chr(data[p]); p += 1
        if t == "Y": return struct.unpack_from("<h", data, p)[0], p + 2
        if t == "C": return data[p] != 0, p + 1
        if t == "I": return struct.unpack_from("<i", data, p)[0], p + 4
        if t == "F": return struct.unpack_from("<f", data, p)[0], p + 4
        if t == "D": return struct.unpack_from("<d", data, p)[0], p + 8
        if t == "L": return struct.unpack_from("<q", data, p)[0], p + 8
        if t in ("f", "d", "l", "i", "b"):
            arr_len, enc, comp_len = struct.unpack_from("<III", data, p); p += 12
            raw = data[p:p + comp_len]; p += comp_len
            return ("ARR", t, arr_len, enc, raw), p
        if t in ("S", "R"):
            slen = struct.unpack_from("<I", data, p)[0]; p += 4
            val = data[p:p + slen]; p += slen
            if t == "S": val = val.decode("utf-8", errors="replace")
            return val, p
        raise ValueError(f"Unknown FBX property type {t!r}")

    def read_node(p):
        if is_64:
            end_off, num_props, _ = struct.unpack_from("<QQQ", data, p); p += 24
        else:
            end_off, num_props, _ = struct.unpack_from("<III", data, p); p += 12
        nlen = data[p]; p += 1
        name = data[p:p + nlen].decode("utf-8", errors="replace"); p += nlen
        if end_off == 0:
            return None, p
        props = [None] * num_props
        for i in range(num_props):
            props[i], p = read_prop(p)
        ch = []
        ns = 25 if is_64 else 13
        while p < end_off - ns:
            c, p = read_node(p)
            if c is None:
                break
            ch.append(c)
        return (name, props, ch), end_off

    roots = []
    ns = 25 if is_64 else 13
    while pos < len(data) - ns:
        n, pos = read_node(pos)
        if n is None:
            break
        roots.append(n)
    return roots


def decode_arr(prop):
    _, t, alen, enc, raw = prop
    if enc == 1:
        raw = zlib.decompress(raw)
    fmt = {"d": "d", "f": "f", "i": "i", "l": "q", "b": "B"}[t]
    return struct.unpack(f"<{alen}{fmt}", raw)


def child_by_name(children, name):
    for c in children:
        if c[0] == name:
            return c
    return None


def compile_worker(fbx_path: str, mesh_layer_map: dict, grid_res_map: dict):
    roots = load_fbx(fbx_path)
    objs = child_by_name(roots, "Objects")[2]
    conns = child_by_name(roots, "Connections")[2]

    models = {}
    geoms = {}
    skins = {}
    clusters = {}
    pose_node = None

    for cn, cp, cc in objs:
        if cn == "Model":
            models[cp[0]] = (cp[1].split("\x00")[0], cp[2], cc)
        elif cn == "Geometry" and cp[2] == "Mesh":
            geoms[cp[0]] = (cp[1].split("\x00")[0], cc)
        elif cn == "Deformer" and cp[2] == "Skin":
            skins[cp[0]] = cp[0]
        elif cn == "Deformer" and cp[2] == "Cluster":
            clusters[cp[0]] = cc
        elif cn == "Pose" and pose_node is None:
            pose_node = cc

    # Extract 17 rest-pose bone positions
    rest_pos = [(0.0, 0.0, 0.0)] * len(BONE_NAMES)
    name_to_idx = {n: i for i, n in enumerate(BONE_NAMES)}
    if pose_node:
        for pn, _, pc in pose_node:
            if pn == "PoseNode":
                nid = child_by_name(pc, "Node")[1][0]
                mat = decode_arr(child_by_name(pc, "Matrix")[1][0])
                mname = models.get(nid, ("", "", []))[0]
                if mname in name_to_idx:
                    rest_pos[name_to_idx[mname]] = (float(mat[12]), float(mat[13]), float(mat[14]))

    # Wire Skin -> Geometry, Cluster -> Skin, BoneModel -> Cluster
    skin_to_geom = {}
    geom_to_clusters = defaultdict(list)
    cluster_to_bone = {}
    cluster_to_skin = {}

    for _, cp, _ in conns:
        src, dst = cp[1], cp[2]
        if src in skins and dst in geoms:
            skin_to_geom[src] = dst
        elif src in clusters and dst in skins:
            cluster_to_skin[src] = dst
        elif src in models and dst in clusters:
            cluster_to_bone[dst] = models[src][0]

    for cid, sid in cluster_to_skin.items():
        gid = skin_to_geom.get(sid)
        if gid is not None:
            geom_to_clusters[gid].append(cid)

    out_verts = []   # list of 13-float tuples: px,py,pz, nx,ny,nz, u,v,layer, b0,b1,w0,w1
    out_indices = [] # list of int

    for gid, (mname, cc) in geoms.items():
        if mname not in mesh_layer_map:
            continue
        tex_layer = float(mesh_layer_map[mname])
        cell_size = grid_res_map.get(mname, 0.018)

        vs_raw = decode_arr(child_by_name(cc, "Vertices")[1][0])
        pvi = decode_arr(child_by_name(cc, "PolygonVertexIndex")[1][0])
        num_ctrl_verts = len(vs_raw) // 3

        # Normals (ByVertice, Direct)
        nm_node = child_by_name(cc, "LayerElementNormal")
        norms_raw = decode_arr(child_by_name(nm_node[2], "Normals")[1][0]) if nm_node else None
        nm_map = child_by_name(nm_node[2], "MappingInformationType")[1][0] if nm_node else "ByVertice"

        # UVs (ByPolygonVertex, IndexToDirect)
        uv_node = child_by_name(cc, "LayerElementUV")
        uvs_raw = decode_arr(child_by_name(uv_node[2], "UV")[1][0]) if uv_node else None
        uv_idx_node = child_by_name(uv_node[2], "UVIndex") if uv_node else None
        uv_indices = decode_arr(uv_idx_node[1][0]) if uv_idx_node else None

        # Accumulate 17-bone skin weights per control vertex
        vert_bone_weights = [defaultdict(float) for _ in range(num_ctrl_verts)]
        for cid in geom_to_clusters.get(gid, []):
            bname = cluster_to_bone.get(cid, "Hips")
            b17 = map_fbx_bone_to_17(bname)
            cl_cc = clusters[cid]
            idx_n = child_by_name(cl_cc, "Indexes")
            w_n = child_by_name(cl_cc, "Weights")
            if idx_n is None or w_n is None:
                continue
            c_inds = decode_arr(idx_n[1][0])
            c_ws = decode_arr(w_n[1][0])
            for vi, w in zip(c_inds, c_ws):
                if 0 <= vi < num_ctrl_verts and w > 1e-4:
                    vert_bone_weights[vi][b17] += float(w)

        # Fallback bone assignment by height if any control vertex has 0 weights
        ctrl_skin = []
        for vi in range(num_ctrl_verts):
            bw = vert_bone_weights[vi]
            if not bw:
                py = vs_raw[vi * 3 + 1]
                fb = 4 if py > 1.50 else (2 if py > 1.15 else 0)
                ctrl_skin.append((float(fb), float(fb), 1.0, 0.0))
            else:
                top = sorted(bw.items(), key=lambda kv: -kv[1])[:2]
                if len(top) == 1:
                    ctrl_skin.append((float(top[0][0]), float(top[0][0]), 1.0, 0.0))
                else:
                    s = top[0][1] + top[1][1]
                    ctrl_skin.append((float(top[0][0]), float(top[1][0]), top[0][1] / s, top[1][1] / s))

        # Parse polygons into triangles (fan triangulation)
        tris = []
        poly = []
        for poly_v_cursor, raw_idx in enumerate(pvi):
            is_end = raw_idx < 0
            vi = (-raw_idx - 1) if is_end else raw_idx
            poly.append((vi, poly_v_cursor))
            if is_end:
                if len(poly) >= 3:
                    v0 = poly[0]
                    for k in range(1, len(poly) - 1):
                        tris.append((v0, poly[k], poly[k + 1]))
                poly = []

        # UV-aware spatial vertex clustering decimation (preserves seams & bone boundaries!)
        uv_quant = 0.06 if mname != "haircut" else 0.14
        cluster_acc = {}
        cluster_id_map = {}

        def get_corner_key(vi, pv_cursor):
            px = vs_raw[vi * 3]
            py = vs_raw[vi * 3 + 1]
            pz = vs_raw[vi * 3 + 2]
            if uvs_raw is not None:
                ui = uv_indices[pv_cursor] if uv_indices is not None else pv_cursor
                u = float(uvs_raw[ui * 2])
                v = 1.0 - float(uvs_raw[ui * 2 + 1])
            else:
                u, v = 0.5, 0.5
            b0 = int(ctrl_skin[vi][0])
            key = (
                int(math.floor(px / cell_size)),
                int(math.floor(py / cell_size)),
                int(math.floor(pz / cell_size)),
                int(math.floor(u / uv_quant)),
                int(math.floor(v / uv_quant)),
                b0,
            )
            return key, px, py, pz, u, v

        base_vert_offset = len(out_verts)
        mesh_tris_keys = []
        for c0, c1, c2 in tris:
            corners = []
            for vi, pvc in (c0, c1, c2):
                key, px, py, pz, u, v = get_corner_key(vi, pvc)
                if norms_raw is not None:
                    n_idx = vi if nm_map == "ByVertice" else pvc
                    nx = float(norms_raw[n_idx * 3])
                    ny = float(norms_raw[n_idx * 3 + 1])
                    nz = float(norms_raw[n_idx * 3 + 2])
                else:
                    nx, ny, nz = 0.0, 1.0, 0.0
                if key not in cluster_acc:
                    cluster_acc[key] = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, defaultdict(float), 0]
                acc = cluster_acc[key]
                acc[0] += px; acc[1] += py; acc[2] += pz
                acc[3] += nx; acc[4] += ny; acc[5] += nz
                acc[6] += u;  acc[7] += v
                b0, b1, w0, w1 = ctrl_skin[vi]
                acc[8][int(b0)] += w0
                if w1 > 1e-4:
                    acc[8][int(b1)] += w1
                acc[9] += 1
                corners.append(key)
            if corners[0] != corners[1] and corners[1] != corners[2] and corners[0] != corners[2]:
                mesh_tris_keys.append(tuple(corners))

        # Deduplicate triangles after clustering
        seen_tris = set()
        used_keys = set()
        clean_tris = []
        for k0, k1, k2 in mesh_tris_keys:
            canon = tuple(sorted((k0, k1, k2)))
            if canon in seen_tris:
                continue
            seen_tris.add(canon)
            clean_tris.append((k0, k1, k2))
            used_keys.add(k0); used_keys.add(k1); used_keys.add(k2)

        for key in used_keys:
            acc = cluster_acc[key]
            cnt = float(acc[9])
            px, py, pz = acc[0] / cnt, acc[1] / cnt, acc[2] / cnt
            nx, ny, nz = acc[3], acc[4], acc[5]
            nlen = math.sqrt(nx * nx + ny * ny + nz * nz)
            if nlen > 1e-5:
                nx /= nlen; ny /= nlen; nz /= nlen
            else:
                nx, ny, nz = 0.0, 1.0, 0.0
            u, v = acc[6] / cnt, acc[7] / cnt
            top = sorted(acc[8].items(), key=lambda kv: -kv[1])[:2]
            if len(top) == 1:
                b0, b1, w0, w1 = float(top[0][0]), float(top[0][0]), 1.0, 0.0
            else:
                s = top[0][1] + top[1][1]
                b0, b1, w0, w1 = float(top[0][0]), float(top[1][0]), top[0][1] / s, top[1][1] / s
            cluster_id_map[key] = len(out_verts)
            out_verts.append((px, py, pz, nx, ny, nz, u, v, tex_layer, b0, b1, w0, w1))

        for k0, k1, k2 in clean_tris:
            out_indices.append(cluster_id_map[k0])
            out_indices.append(cluster_id_map[k1])
            out_indices.append(cluster_id_map[k2])

        print(f"  Mesh {mname:20s}: {len(tris):5d} -> {len(clean_tris):4d} tris ({len(used_keys):4d} verts) layer={int(tex_layer)}")

    assert len(out_verts) <= 65535, f"Too many verts: {len(out_verts)}"
    return rest_pos, out_verts, out_indices


def write_png_rgba(path: str, width: int, height: int, rgba: bytearray):
    def png_chunk(chunk_type: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(chunk_type + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + chunk_type + data + struct.pack(">I", crc)

    raw_rows = bytearray()
    stride = width * 4
    for y in range(height):
        raw_rows.append(0)
        raw_rows.extend(rgba[y * stride:(y + 1) * stride])

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw_rows), 6)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(png_chunk(b"IHDR", ihdr))
        f.write(png_chunk(b"IDAT", idat))
        f.write(png_chunk(b"IEND", b""))


def load_rgba_tile_256(img_path: str, is_hair: bool = False) -> bytearray:
    res = subprocess.run(
        ["convert", img_path, "-resize", "256x256!", "-depth", "8", "rgba:-"],
        check=True,
        capture_output=True,
    )
    buf = bytearray(res.stdout)
    expected = 256 * 256 * 4
    if len(buf) != expected:
        raise RuntimeError(f"Expected {expected} bytes for {img_path}, got {len(buf)}")
    if is_hair:
        for i in range(0, expected, 4):
            a = buf[i + 3]
            if a < 85:
                buf[i] = 18; buf[i + 1] = 15; buf[i + 2] = 14; buf[i + 3] = 0
            else:
                buf[i + 3] = 255
    return buf


def build_atlas(out_path: str):
    TILE = 256
    COLS, ROWS = 8, 2
    W, H = COLS * TILE, ROWS * TILE
    atlas = bytearray([255] * (W * H * 4))

    tex_layers = {
        0: "/tmp/gdrive_fuel_workers/w1/textures/AvatarBodyMale_Color_1K.jpeg",
        1: "/tmp/gdrive_fuel_workers/w1/textures/AvatarHeadMale_Color_1K.jpeg",
        2: "/tmp/gdrive_fuel_workers/w1/textures/AvatarEyes_Color_512.jpeg",
        3: "/tmp/gdrive_fuel_workers/w1/textures/HairCard1_Color_1K.png",
        4: "/tmp/gdrive_fuel_workers/w1/textures/Parz_Color_1K.jpeg",

        6: "/tmp/gdrive_fuel_workers/w2/textures/AvatarBodyMale_Color_1K.jpeg",
        7: "/tmp/gdrive_fuel_workers/w2/textures/AvatarHeadMale_Color_1K.jpeg",
        8: "/tmp/gdrive_fuel_workers/w2/textures/AvatarEyes_Color_512.jpeg",
        9: "/tmp/gdrive_fuel_workers/w2/textures/HairCard1_Color_1K.png",
        10: "/tmp/gdrive_fuel_workers/w2/textures/kurta_Tandzut_Color_1K.jpeg",
        11: "/tmp/gdrive_fuel_workers/w2/textures/dhoti_Tandzut_Color_1K.jpeg",
        12: "/tmp/gdrive_fuel_workers/w2/textures/shoes_Tandzut_Color_1K.jpeg",
    }

    for layer, tpath in tex_layers.items():
        tile = load_rgba_tile_256(tpath, is_hair=(layer in (3, 9)))
        gx = (layer % COLS) * TILE
        gy = (layer // COLS) * TILE
        for row in range(TILE):
            src0 = row * TILE * 4
            dst0 = ((gy + row) * W + gx) * 4
            atlas[dst0:dst0 + TILE * 4] = tile[src0:src0 + TILE * 4]

    write_png_rgba(out_path, W, H, atlas)
    print(f"Saved atlas {out_path} ({os.path.getsize(out_path):,} bytes)")


def main():
    os.makedirs("app/src/main/assets", exist_ok=True)
    atlas_out = "app/src/main/assets/thrissur_fuel_workers_atlas.png"
    bin_out = "app/src/main/assets/thrissur_fuel_workers.bin"

    build_atlas(atlas_out)

    print("Compiling Worker 1 (Petrol Attendant)...")
    w1_layers = {
        "AvatarBody": 0,
        "AvatarHead": 1,
        "AvatarLeftEyeball": 2,
        "AvatarRightEyeball": 2,
        "haircut": 3,
        "outfit": 4,
    }
    w1_res = {
        "AvatarHead": 0.011,
        "AvatarLeftEyeball": 0.008,
        "AvatarRightEyeball": 0.008,
        "haircut": 0.018,
        "AvatarBody": 0.022,
        "outfit": 0.022,
    }
    w1 = compile_worker("/tmp/gdrive_fuel_workers/w1/worker1.fbx", w1_layers, w1_res)

    print("Compiling Worker 2 (Diesel Attendant)...")
    w2_layers = {
        "AvatarBody": 6,
        "AvatarHead": 7,
        "AvatarLeftEyeball": 8,
        "AvatarRightEyeball": 8,
        "haircut": 9,
        "outfit_top": 10,
        "outfit_bottom": 11,
        "outfit_shoes": 12,
    }
    w2_res = {
        "AvatarHead": 0.011,
        "AvatarLeftEyeball": 0.008,
        "AvatarRightEyeball": 0.008,
        "haircut": 0.018,
        "AvatarBody": 0.022,
        "outfit_top": 0.021,
        "outfit_bottom": 0.021,
        "outfit_shoes": 0.018,
    }
    w2 = compile_worker("/tmp/gdrive_fuel_workers/w2/extracted/avatar/model.fbx", w2_layers, w2_res)

    # Write FWK1 binary file
    with open(bin_out, "wb") as f:
        f.write(b"FWK1")
        f.write(struct.pack("<III", 1, 2, len(BONE_NAMES)))
        for idx, (rest_pos, verts, indices) in enumerate((w1, w2)):
            for bx, by, bz in rest_pos:
                f.write(struct.pack("<fff", bx, by, bz))
            f.write(struct.pack("<II", len(verts), len(indices)))
            for v in verts:
                f.write(struct.pack("<13f", *v))
            f.write(struct.pack(f"<{len(indices)}H", *indices))
            print(f"Worker {idx}: {len(verts):,} verts, {len(indices)//3:,} tris")

    print(f"Saved binary {bin_out} ({os.path.getsize(bin_out):,} bytes)")


if __name__ == "__main__":
    main()
