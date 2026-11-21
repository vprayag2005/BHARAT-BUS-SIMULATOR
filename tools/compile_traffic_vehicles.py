#!/usr/bin/env python3
"""
Compile all 13 real .glb traffic vehicle models from the user's Google Drive pack
(/tmp/gdrive_vehicles/*.glb) into Android OpenGL ES 3.0 assets:
  1. app/src/main/assets/thrissur_traffic_atlas.png
     (2048x1280 RGBA PNG, 8x5 = 40 tiles of 256x256: layers 0..35 embedded textures,
      layer 36 = 16x16 PBR solid-color swatch palette)
  2. app/src/main/assets/thrissur_traffic_vehicles.bin
     (TVM1 indexed 3D mesh format with full glTF node transforms, normals, UVs,
      per-vertex texture layer, and material/lighting flags for headlights, dynamic
      brake lights, tinted glass, clearcoat paint, and ambulance/police beacons)
"""
import hashlib
import json
import math
import os
import struct
import subprocess
import tempfile
import zlib

GLB_DIR = "/tmp/gdrive_vehicles"
OUT_ATLAS = "app/src/main/assets/thrissur_traffic_atlas.png"
OUT_BIN = "app/src/main/assets/thrissur_traffic_vehicles.bin"

TILE_SIZE = 256
ATLAS_COLS = 8
ATLAS_ROWS = 5

# Canonical model order matching ThrissurTrafficModels.kt constants (0..12)
VEHICLE_SPECS = [
    ("auto", "auto.glb"),
    ("car1", "car1.glb"),
    ("car2", "car2.glb"),
    ("omini", "omini.glb"),
    ("ksrtc_red", "ksrtc_red.glb"),
    ("ksrtc_blue", "ksrtc_blue.glb"),
    ("private_bus_1", "private_bus_1.glb"),
    ("private_bus_2", "private_bus_2.glb"),
    ("private_bus_3", "private_bus_3.glb"),
    ("truck1", "truck1.glb"),
    ("truck2", "truck2.glb"),
    ("ambulance", "ambulance.glb"),
    ("police", "police.glb"),
]

# Material flags packed into lower 3 bits of vertex packedMat (texLayer * 8 + matFlag)
MAT_MATTE = 0
MAT_GLOSSY_PAINT = 1
MAT_GLASS = 2
MAT_EMISSIVE_WHITE = 3
MAT_BRAKE_TAIL = 4
MAT_EMISSIVE_AMBER = 5
MAT_BEACON_BLUE = 6
MAT_BEACON_RED = 7


def mat4_mul(a, b):
    c = [0.0] * 16
    for col in range(4):
        for row in range(4):
            c[col * 4 + row] = (
                a[0 * 4 + row] * b[col * 4 + 0]
                + a[1 * 4 + row] * b[col * 4 + 1]
                + a[2 * 4 + row] * b[col * 4 + 2]
                + a[3 * 4 + row] * b[col * 4 + 3]
            )
    return c


def node_local_mat(node):
    if "matrix" in node:
        return [float(x) for x in node["matrix"]]
    tx, ty, tz = node.get("translation", (0.0, 0.0, 0.0))
    qx, qy, qz, qw = node.get("rotation", (0.0, 0.0, 0.0, 1.0))
    sx, sy, sz = node.get("scale", (1.0, 1.0, 1.0))
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


def mat3_inv_transpose(m):
    a00, a10, a20 = m[0], m[1], m[2]
    a01, a11, a21 = m[4], m[5], m[6]
    a02, a12, a22 = m[8], m[9], m[10]
    c00 = a11 * a22 - a12 * a21
    c01 = -(a10 * a22 - a12 * a20)
    c02 = a10 * a21 - a11 * a20
    c10 = -(a01 * a22 - a02 * a21)
    c11 = a00 * a22 - a02 * a20
    c12 = -(a00 * a21 - a01 * a20)
    c20 = a01 * a12 - a02 * a11
    c21 = -(a00 * a12 - a02 * a10)
    c22 = a00 * a11 - a01 * a10
    det = a00 * c00 + a01 * c01 + a02 * c02
    if abs(det) < 1e-10:
        return (1, 0, 0, 0, 1, 0, 0, 0, 1)
    inv_det = 1.0 / det
    return (
        c00 * inv_det, c10 * inv_det, c20 * inv_det,
        c01 * inv_det, c11 * inv_det, c21 * inv_det,
        c02 * inv_det, c12 * inv_det, c22 * inv_det,
    )


def decode_image_to_rgba_tile(img_bytes, mime_type, size=TILE_SIZE):
    ext = ".png" if "png" in mime_type.lower() else ".jpg"
    with tempfile.NamedTemporaryFile(suffix=ext, delete=False) as tf:
        tf.write(img_bytes)
        tmp_path = tf.name
    try:
        rgba = subprocess.check_output(
            ["convert", tmp_path, "-resize", f"{size}x{size}!", "-depth", "8", "rgba:-"]
        )
        # Ensure opaque alpha for vehicle textures
        buf = bytearray(rgba)
        for i in range(3, len(buf), 4):
            buf[i] = 255
        return bytes(buf)
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


def write_png_rgba(path, width, height, rgba_bytes):
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw_rows = bytearray()
    row_bytes = width * 4
    for y in range(height):
        raw_rows.append(0)
        off = y * row_bytes
        raw_rows.extend(rgba_bytes[off : off + row_bytes])

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw_rows), level=9)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def read_accessor(g, blob, acc_idx):
    acc = g["accessors"][acc_idx]
    bv = g["bufferViews"][acc["bufferView"]]
    offset = bv.get("byteOffset", 0) + acc.get("byteOffset", 0)
    count = acc["count"]
    ctype = acc["componentType"]
    atype = acc["type"]
    ncomp = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[atype]
    fmt_ch = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}[ctype]
    elem_size = struct.calcsize("<" + fmt_ch * ncomp)
    stride = bv.get("byteStride", elem_size)
    unpack = struct.Struct("<" + fmt_ch * ncomp).unpack_from
    return [unpack(blob, offset + i * stride) for i in range(count)]


def linear_to_srgb_byte(c):
    c = max(0.0, min(1.0, float(c)))
    if c <= 0.0031308:
        s = c * 12.92
    else:
        s = 1.055 * (c ** (1.0 / 2.4)) - 0.055
    return max(0, min(255, int(round(s * 255.0))))


def classify_material(model_key, mat_dict, has_tex):
    nm = mat_dict.get("name", "").lower()
    pbr = mat_dict.get("pbrMetallicRoughness", {})
    bc = pbr.get("baseColorFactor", [1.0, 1.0, 1.0, 1.0])
    em = mat_dict.get("emissiveFactor", [0.0, 0.0, 0.0])
    alpha_mode = mat_dict.get("alphaMode", "OPAQUE")
    metallic = float(pbr.get("metallicFactor", 0.0))
    roughness = float(pbr.get("roughnessFactor", 0.5))

    # Skip translucent outer headlamp/taillamp cover lenses on car2 so glowing LEDs inside stay sharp
    if alpha_mode == "BLEND" and ("head_lens_clear" in nm or "tail_lens_red" in nm):
        return ("SKIP", None, None)

    # Glass / windshields
    if "glass" in nm or "windshield" in nm or "window" in nm:
        return ("KEEP", MAT_GLASS, (20, 30, 40))

    # Emergency beacons on ambulance & police
    if "beacon_blue" in nm:
        return ("KEEP", MAT_BEACON_BLUE, (35, 115, 255))

    # Rear brake / taillights (per-vertex Z check refines front markers & roof red strobes later)
    if any(k in nm for k in ("taillight", "led_tail", "tail_led")):
        return ("KEEP", MAT_BRAKE_TAIL, (235, 24, 24))

    # Amber turn/marker lamps
    if "amber_emissive" in nm:
        return ("KEEP", MAT_EMISSIVE_AMBER, (250, 150, 20))

    # Headlights / DRL / backlit signage
    if any(k in nm for k in ("headlight", "drl_white", "led_drl", "proj_core", "dest_board", "ws_police_banner", "mirrored_sign")):
        rgb = (
            linear_to_srgb_byte(max(bc[0], em[0])),
            linear_to_srgb_byte(max(bc[1], em[1])),
            linear_to_srgb_byte(max(bc[2], em[2])),
        )
        return ("KEEP", MAT_EMISSIVE_WHITE, rgb)

    # Non-textured base color in sRGB
    rgb = (
        linear_to_srgb_byte(bc[0]),
        linear_to_srgb_byte(bc[1]),
        linear_to_srgb_byte(bc[2]),
    )

    is_matte = any(k in nm for k in ("tire", "tyre", "rubber", "underbody", "sack", "interior", "seat", "dash", "plastic", "grille", "grill", "black_trim", "housing", "mudflap", "chassis", "road", "driver", "skin", "shirt"))
    if is_matte or (roughness > 0.62 and metallic < 0.25):
        flag = MAT_MATTE
    else:
        flag = MAT_GLOSSY_PAINT
    return ("KEEP", flag, rgb)


def main():
    texture_tiles = []       # list of 256x256 RGBA bytes
    tex_hash_to_layer = {}   # sha256 -> layer index
    swatch_colors = []       # list of (r, g, b)
    swatch_to_idx = {}

    def get_swatch_uv(rgb):
        # Quantize slightly to keep swatch palette well under 256 entries
        qrgb = (rgb[0] & 0xFC, rgb[1] & 0xFC, rgb[2] & 0xFC)
        idx = swatch_to_idx.get(qrgb)
        if idx is None:
            if len(swatch_colors) >= 256:
                # Find closest existing swatch
                best_i = 0
                best_d = 10**9
                for si, sc in enumerate(swatch_colors):
                    d = (sc[0] - qrgb[0]) ** 2 + (sc[1] - qrgb[1]) ** 2 + (sc[2] - qrgb[2]) ** 2
                    if d < best_d:
                        best_d = d
                        best_i = si
                idx = best_i
            else:
                idx = len(swatch_colors)
                swatch_colors.append(qrgb)
                swatch_to_idx[qrgb] = idx
        col = idx % 16
        row = idx // 16
        return ((col + 0.5) / 16.0, (row + 0.5) / 16.0)

    # First pass: collect all embedded textures across all 13 GLBs so we know exact swatch layer index
    parsed_glbs = []
    for model_key, filename in VEHICLE_SPECS:
        path = os.path.join(GLB_DIR, filename)
        if not os.path.exists(path):
            raise FileNotFoundError(f"Missing GLB file: {path}")
        with open(path, "rb") as f:
            data = f.read()
        jlen = struct.unpack_from("<I", data, 12)[0]
        g = json.loads(data[20 : 20 + jlen].decode("utf-8"))
        blob = data[20 + jlen + 8 :]

        img_layer_map = {}
        for img_idx, img in enumerate(g.get("images", [])):
            bv = g["bufferViews"][img["bufferView"]]
            off = bv.get("byteOffset", 0)
            length = bv["byteLength"]
            img_bytes = blob[off : off + length]
            h = hashlib.sha256(img_bytes).hexdigest()
            layer = tex_hash_to_layer.get(h)
            if layer is None:
                layer = len(texture_tiles)
                tex_hash_to_layer[h] = layer
                tile_rgba = decode_image_to_rgba_tile(img_bytes, img.get("mimeType", "image/png"))
                texture_tiles.append(tile_rgba)
            img_layer_map[img_idx] = layer
        parsed_glbs.append((model_key, filename, g, blob, img_layer_map))

    swatch_layer = len(texture_tiles)
    total_atlas_slots = ATLAS_COLS * ATLAS_ROWS
    if swatch_layer >= total_atlas_slots:
        raise RuntimeError(f"Atlas overflow: {swatch_layer + 1} layers > {total_atlas_slots} slots")
    print(f"Unique embedded textures: {swatch_layer} (swatch layer = {swatch_layer}, atlas slots = {total_atlas_slots})")

    compiled_models = []
    total_verts = 0
    total_tris = 0

    for model_key, filename, g, blob, img_layer_map in parsed_glbs:
        nodes = g.get("nodes", [])
        world_mats = [None] * len(nodes)
        ident = [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]

        def dfs(ni, parent_m):
            m = mat4_mul(parent_m, node_local_mat(nodes[ni]))
            world_mats[ni] = m
            for ch in nodes[ni].get("children", []):
                dfs(ch, m)

        for sc in g.get("scenes", []):
            for r in sc.get("nodes", []):
                dfs(r, ident)

        mats = g.get("materials", [])
        textures = g.get("textures", [])

        verts = []       # list of (qx, qy, qz, qnx, qny, qnz, qu, qv, layer, flag)
        vert_map = {}
        indices = []

        min_x = min_y = min_z = 1e9
        max_x = max_y = max_z = -1e9

        is_emergency = model_key in ("ambulance", "police")

        for ni, n in enumerate(nodes):
            if "mesh" not in n:
                continue
            m = world_mats[ni] or ident
            nm_mat = mat3_inv_transpose(m)
            # Check determinant sign for winding flip
            det3 = (
                m[0] * (m[5] * m[10] - m[6] * m[9])
                - m[4] * (m[1] * m[10] - m[2] * m[9])
                + m[8] * (m[1] * m[6] - m[2] * m[5])
            )
            flip_winding = det3 < 0.0

            mesh = g["meshes"][n["mesh"]]
            for prim in mesh.get("primitives", []):
                mat_idx = prim.get("material", -1)
                mat_dict = mats[mat_idx] if 0 <= mat_idx < len(mats) else {}
                pbr = mat_dict.get("pbrMetallicRoughness", {})
                tex_info = pbr.get("baseColorTexture")
                has_tex = tex_info is not None

                action, base_flag, solid_rgb = classify_material(model_key, mat_dict, has_tex)
                if action == "SKIP":
                    continue

                if has_tex:
                    tex_idx = tex_info["index"]
                    img_idx = textures[tex_idx]["source"]
                    tex_layer = img_layer_map[img_idx]
                    swatch_uv = None
                else:
                    tex_layer = swatch_layer
                    swatch_uv = get_swatch_uv(solid_rgb)

                attrs = prim["attributes"]
                pos_raw = read_accessor(g, blob, attrs["POSITION"])
                has_norm = "NORMAL" in attrs
                norm_raw = read_accessor(g, blob, attrs["NORMAL"]) if has_norm else None
                has_uv = has_tex and ("TEXCOORD_0" in attrs)
                uv_raw = read_accessor(g, blob, attrs["TEXCOORD_0"]) if has_uv else None

                if "indices" in prim:
                    idx_raw = [x[0] for x in read_accessor(g, blob, prim["indices"])]
                else:
                    idx_raw = list(range(len(pos_raw)))

                # Transform positions & normals
                wpos = []
                wnorm = []
                for vi, p in enumerate(pos_raw):
                    wx = m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12]
                    wy = m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13]
                    wz = m[2] * p[0] + m[6] * p[1] + m[10] * p[2] + m[14]
                    wpos.append((wx, wy, wz))
                    if wx < min_x: min_x = wx
                    if wx > max_x: max_x = wx
                    if wy < min_y: min_y = wy
                    if wy > max_y: max_y = wy
                    if wz < min_z: min_z = wz
                    if wz > max_z: max_z = wz

                    if has_norm:
                        nr = norm_raw[vi]
                        nx = nm_mat[0] * nr[0] + nm_mat[3] * nr[1] + nm_mat[6] * nr[2]
                        ny = nm_mat[1] * nr[0] + nm_mat[4] * nr[1] + nm_mat[7] * nr[2]
                        nz = nm_mat[2] * nr[0] + nm_mat[5] * nr[1] + nm_mat[8] * nr[2]
                        nlen = math.sqrt(nx * nx + ny * ny + nz * nz)
                        if nlen > 1e-6:
                            wnorm.append((nx / nlen, ny / nlen, nz / nlen))
                        else:
                            wnorm.append((0.0, 1.0, 0.0))

                def emit_vert(vi, fn):
                    wx, wy, wz = wpos[vi]
                    nx, ny, nz = wnorm[vi] if has_norm else fn
                    if has_uv:
                        u, v = uv_raw[vi]
                        if math.isnan(u) or math.isinf(u): u = 0.5
                        if math.isnan(v) or math.isinf(v): v = 0.5
                    else:
                        u, v = swatch_uv

                    flag = base_flag
                    # Refine brake lights vs front markers vs emergency roof red strobes!
                    if base_flag == MAT_BRAKE_TAIL:
                        if is_emergency and wy > 1.80:
                            flag = MAT_BEACON_RED
                        elif wz >= -0.45:
                            # Front/roof red marker lights on private buses stay subtle static emissive
                            flag = MAT_EMISSIVE_WHITE
                        else:
                            flag = MAT_BRAKE_TAIL
                    elif is_emergency and base_flag == MAT_EMISSIVE_AMBER and wy > 1.80:
                        flag = MAT_BEACON_RED

                    qx = max(-32767, min(32767, int(round(wx * 1000.0))))
                    qy = max(-32767, min(32767, int(round(wy * 1000.0))))
                    qz = max(-32767, min(32767, int(round(wz * 1000.0))))
                    qnx = max(-127, min(127, int(round(nx * 127.0))))
                    qny = max(-127, min(127, int(round(ny * 127.0))))
                    qnz = max(-127, min(127, int(round(nz * 127.0))))
                    # Clamp UVs to [-7.9, 7.9] range so int16 * 4096 never overflows
                    u_c = max(-7.9, min(7.9, float(u)))
                    v_c = max(-7.9, min(7.9, float(v)))
                    qu = max(-32767, min(32767, int(round(u_c * 4096.0))))
                    qv = max(-32767, min(32767, int(round(v_c * 4096.0))))

                    key = (qx, qy, qz, qnx, qny, qnz, qu, qv, tex_layer, flag)
                    out_i = vert_map.get(key)
                    if out_i is None:
                        out_i = len(verts)
                        verts.append(key)
                        vert_map[key] = out_i
                    return out_i

                for ti in range(0, len(idx_raw) - 2, 3):
                    i0, i1, i2 = idx_raw[ti], idx_raw[ti + 1], idx_raw[ti + 2]
                    if flip_winding:
                        i1, i2 = i2, i1
                    if i0 == i1 or i1 == i2 or i2 == i0:
                        continue
                    if not has_norm:
                        p0, p1, p2 = wpos[i0], wpos[i1], wpos[i2]
                        e1 = (p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2])
                        e2 = (p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2])
                        cx = e1[1] * e2[2] - e1[2] * e2[1]
                        cy = e1[2] * e2[0] - e1[0] * e2[2]
                        cz = e1[0] * e2[1] - e1[1] * e2[0]
                        cl = math.sqrt(cx * cx + cy * cy + cz * cz)
                        fn = (cx / cl, cy / cl, cz / cl) if cl > 1e-8 else (0.0, 1.0, 0.0)
                    else:
                        fn = (0.0, 1.0, 0.0)
                    v0 = emit_vert(i0, fn)
                    v1 = emit_vert(i1, fn)
                    v2 = emit_vert(i2, fn)
                    if v0 != v1 and v1 != v2 and v2 != v0:
                        indices.extend((v0, v1, v2))

        # Shift Y slightly if min_y != 0 so tires rest cleanly on road surface Y = 0.0
        y_shift_mm = int(round(-min_y * 1000.0))
        if abs(y_shift_mm) > 2:
            verts = [
                (v[0], max(-32767, min(32767, v[1] + y_shift_mm)), v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9])
                for v in verts
            ]
            max_y -= min_y
            min_y = 0.0

        if len(verts) >= 65535:
            raise RuntimeError(f"Model {model_key} exceeded uint16 vertex limit: {len(verts)}")

        half_x = max(abs(min_x), abs(max_x))
        half_z = max(abs(min_z), abs(max_z))
        height_y = max(0.5, max_y - min_y)

        compiled_models.append((model_key, half_x, half_z, height_y, verts, indices))
        total_verts += len(verts)
        total_tris += len(indices) // 3
        print(
            f"  [{len(compiled_models)-1:2d}] {model_key:14s} | verts={len(verts):5d} tris={len(indices)//3:5d} "
            f"| halfX={half_x:.2f} halfZ={half_z:.2f} heightY={height_y:.2f}"
        )

    # Build Layer 36: 16x16 solid color swatch tile (256x256 RGBA)
    swatch_tile = bytearray(TILE_SIZE * TILE_SIZE * 4)
    for si, (r, g_c, b_c) in enumerate(swatch_colors):
        col = si % 16
        row = si // 16
        for py in range(row * 16, (row + 1) * 16):
            row_off = py * TILE_SIZE * 4
            for px in range(col * 16, (col + 1) * 16):
                idx = row_off + px * 4
                swatch_tile[idx] = r
                swatch_tile[idx + 1] = g_c
                swatch_tile[idx + 2] = b_c
                swatch_tile[idx + 3] = 255
    texture_tiles.append(bytes(swatch_tile))
    print(f"Solid PBR color swatches packed into layer {swatch_layer}: {len(swatch_colors)} / 256")

    # Assemble 2048x1280 RGBA atlas PNG (8x5 tiles)
    atlas_w = ATLAS_COLS * TILE_SIZE
    atlas_h = ATLAS_ROWS * TILE_SIZE
    atlas_buf = bytearray(atlas_w * atlas_h * 4)
    for layer_idx, tile_rgba in enumerate(texture_tiles):
        tc = layer_idx % ATLAS_COLS
        tr = layer_idx // ATLAS_COLS
        dst_x = tc * TILE_SIZE
        dst_y = tr * TILE_SIZE
        for y in range(TILE_SIZE):
            src_off = y * TILE_SIZE * 4
            dst_off = ((dst_y + y) * atlas_w + dst_x) * 4
            atlas_buf[dst_off : dst_off + TILE_SIZE * 4] = tile_rgba[src_off : src_off + TILE_SIZE * 4]

    write_png_rgba(OUT_ATLAS, atlas_w, atlas_h, bytes(atlas_buf))

    # Write TVM1 binary mesh pack
    os.makedirs(os.path.dirname(OUT_BIN), exist_ok=True)
    with open(OUT_BIN, "wb") as f:
        f.write(b"TVM1")
        f.write(struct.pack("<HH", 1, len(compiled_models)))
        v_packer = struct.Struct("<hhhbbbBhhBB")
        for model_key, half_x, half_z, height_y, verts, indices in compiled_models:
            name_b = model_key.encode("utf-8")
            f.write(struct.pack("<H", len(name_b)))
            f.write(name_b)
            f.write(struct.pack("<fffII", half_x, half_z, height_y, len(verts), len(indices)))
            v_chunk = bytearray(len(verts) * 16)
            for vi, v in enumerate(verts):
                v_packer.pack_into(v_chunk, vi * 16, v[0], v[1], v[2], v[3], v[4], v[5], 0, v[6], v[7], v[8], v[9])
            f.write(v_chunk)
            f.write(struct.pack(f"<{len(indices)}H", *indices))

    atlas_mb = os.path.getsize(OUT_ATLAS) / (1024.0 * 1024.0)
    bin_mb = os.path.getsize(OUT_BIN) / (1024.0 * 1024.0)
    print(f"DONE! Total verts={total_verts}, tris={total_tris}")
    print(f"  Atlas: {OUT_ATLAS} ({atlas_w}x{atlas_h}, {atlas_mb:.2f} MB)")
    print(f"  Mesh:  {OUT_BIN} ({bin_mb:.2f} MB)")


if __name__ == "__main__":
    main()
