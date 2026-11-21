#!/usr/bin/env python3
"""
Compile the 2 real Indian Fuel Station .glb assets from Google Drive
(/tmp/gdrive_fuel_station/Indian_Fuel_Station.glb and Fuel_Price_Signboard.glb)
into Android OpenGL ES 3.0 assets:
  1. app/src/main/assets/thrissur_fuel_station_atlas.png
     (2048x768 RGBA PNG, 8x3 = 24 tiles of 256x256:
      layers 0..22 = real embedded baseColor textures blended with emissive glow where present,
      layer 23     = 16x16 solid-color PBR material swatch palette)
  2. app/src/main/assets/thrissur_fuel_station_meshes.bin
     (Z1M2 indexed 3D mesh format compatible with Zone1GDriveEnvironmentRenderer.parseBinaryMeshes,
      preserving 100% intact geometry, glTF node world transforms, native Y=0.0 ground datum,
      exact vertex normals, and repeating per-vertex UVs).
"""
import hashlib
import json
import math
import os
import struct
import subprocess
import tempfile
import zlib

GLB_DIR = "/tmp/gdrive_fuel_station"
OUT_ATLAS = "app/src/main/assets/thrissur_fuel_station_atlas.png"
OUT_BIN = "app/src/main/assets/thrissur_fuel_station_meshes.bin"

TILE_SIZE = 512
ATLAS_COLS = 8
ATLAS_ROWS = 3
SWATCH_LAYER = 23  # Last tile of 8x3 atlas

MODELS = [
    ("indian_fuel_station", 0, "Indian_Fuel_Station.glb"),
    ("fuel_price_signboard", 1, "Fuel_Price_Signboard.glb"),
]


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
        return (1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
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
        buf = bytearray(rgba)
        for i in range(3, len(buf), 4):
            buf[i] = 255
        return bytes(buf)
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


def blend_emissive_tile(base_rgba, emissive_rgba, strength=0.32):
    out = bytearray(base_rgba)
    for i in range(0, len(out), 4):
        out[i + 0] = min(255, int(out[i + 0] + emissive_rgba[i + 0] * strength))
        out[i + 1] = min(255, int(out[i + 1] + emissive_rgba[i + 1] * strength))
        out[i + 2] = min(255, int(out[i + 2] + emissive_rgba[i + 2] * strength))
        out[i + 3] = 255
    return bytes(out)


def write_png_rgba(path, width, height, rgba_bytes):
    def chunk(tag, data):
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

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


def main():
    texture_tiles = []       # list of 256x256 RGBA bytes (layers 0..22)
    tex_hash_to_layer = {}   # sha256 -> layer index
    swatch_colors = []       # list of (r, g, b) packed into SWATCH_LAYER
    swatch_to_idx = {}

    def get_swatch_uv(rgb):
        qrgb = (rgb[0], rgb[1], rgb[2])
        idx = swatch_to_idx.get(qrgb)
        if idx is None:
            idx = len(swatch_colors)
            swatch_colors.append(qrgb)
            swatch_to_idx[qrgb] = idx
        col = idx % 16
        row = idx // 16
        # Center of 16x16 pixel swatch cell inside 256x256 tile
        return ((col + 0.5) / 16.0, (row + 0.5) / 16.0)

    def register_texture_tile(base_bytes, base_mime, em_bytes=None, em_mime=None, em_strength=0.30):
        key_hash = hashlib.sha256(base_bytes + (em_bytes or b"")).hexdigest()
        if key_hash in tex_hash_to_layer:
            return tex_hash_to_layer[key_hash]
        tile = decode_image_to_rgba_tile(base_bytes, base_mime, TILE_SIZE)
        if em_bytes is not None:
            em_tile = decode_image_to_rgba_tile(em_bytes, em_mime or "image/png", TILE_SIZE)
            tile = blend_emissive_tile(tile, em_tile, strength=em_strength)
        layer = len(texture_tiles)
        if layer >= SWATCH_LAYER:
            raise RuntimeError(f"Exceeded max texture layers ({SWATCH_LAYER})!")
        texture_tiles.append(tile)
        tex_hash_to_layer[key_hash] = layer
        return layer

    compiled_models = []

    for model_id, cat_id, filename in MODELS:
        glb_path = os.path.join(GLB_DIR, filename)
        with open(glb_path, "rb") as f:
            magic, ver, length, c0len, c0type = struct.unpack("<4sIIII", f.read(20))
            assert magic == b"glTF", f"Not a valid GLB: {glb_path}"
            g = json.loads(f.read(c0len).decode("utf-8"))
            c1len, c1type = struct.unpack("<II", f.read(8))
            blob = f.read(c1len)

        def extract_img_bytes(img_idx):
            img = g["images"][img_idx]
            bv = g["bufferViews"][img["bufferView"]]
            off = bv.get("byteOffset", 0)
            blen = bv["byteLength"]
            return blob[off : off + blen], img.get("mimeType", "image/png")

        # Build node hierarchy world matrices
        nodes = g.get("nodes", [])
        parent_map = {}
        for ni, n in enumerate(nodes):
            for c in n.get("children", []):
                parent_map[c] = ni

        def get_world_mat(ni):
            m = node_local_mat(nodes[ni])
            if ni in parent_map:
                return mat4_mul(get_world_mat(parent_map[ni]), m)
            return m

        vert_map = {}
        vertices = []
        indices = []
        min_x = min_y = min_z = 1e9
        max_x = max_y = max_z = -1e9

        for ni, n in enumerate(nodes):
            if "mesh" not in n:
                continue
            wm = get_world_mat(ni)
            nm3 = mat3_inv_transpose(wm)
            mesh = g["meshes"][n["mesh"]]
            for prim in mesh["primitives"]:
                mi = prim.get("material")
                mat = g["materials"][mi] if mi is not None else {}
                mat_name = mat.get("name", "")
                pbr = mat.get("pbrMetallicRoughness", {})
                has_base_tex = "baseColorTexture" in pbr

                if has_base_tex:
                    t_idx = pbr["baseColorTexture"]["index"]
                    img_idx = g["textures"][t_idx]["source"]
                    base_b, base_m = extract_img_bytes(img_idx)
                    em_b = em_m = None
                    if "emissiveTexture" in mat:
                        et_idx = mat["emissiveTexture"]["index"]
                        eimg_idx = g["textures"][et_idx]["source"]
                        em_b, em_m = extract_img_bytes(eimg_idx)
                    layer = register_texture_tile(base_b, base_m, em_b, em_m, em_strength=0.28)
                    swatch_uv = None
                    fallback_rgb = (220, 220, 220)
                else:
                    bc = pbr.get("baseColorFactor", [0.8, 0.8, 0.8, 1.0])
                    em = mat.get("emissiveFactor", [0.0, 0.0, 0.0])
                    if "glass_window" in mat_name.lower():
                        # Architectural tinted blue-slate reflective glass for opaque instanced shader
                        rgb = (44, 86, 118)
                    elif "led_light" in mat_name.lower():
                        rgb = (255, 252, 235)
                    else:
                        rgb = (
                            linear_to_srgb_byte(min(1.0, bc[0] + em[0] * 0.5)),
                            linear_to_srgb_byte(min(1.0, bc[1] + em[1] * 0.5)),
                            linear_to_srgb_byte(min(1.0, bc[2] + em[2] * 0.5)),
                        )
                    layer = SWATCH_LAYER
                    swatch_uv = get_swatch_uv(rgb)
                    fallback_rgb = rgb

                pos_raw = read_accessor(g, blob, prim["attributes"]["POSITION"])
                has_norm = "NORMAL" in prim["attributes"]
                norm_raw = read_accessor(g, blob, prim["attributes"]["NORMAL"]) if has_norm else None
                has_uv = "TEXCOORD_0" in prim["attributes"]
                uv_raw = read_accessor(g, blob, prim["attributes"]["TEXCOORD_0"]) if (has_base_tex and has_uv) else None
                idxs = [x[0] for x in read_accessor(g, blob, prim["indices"])] if "indices" in prim else list(range(len(pos_raw)))

                # Transform vertices into exact GLB native world space (preserving Y=0.0 ground plane!)
                xformed_pos = []
                xformed_norm = []
                for vi, (px, py, pz) in enumerate(pos_raw):
                    wx = wm[0] * px + wm[4] * py + wm[8] * pz + wm[12]
                    wy = wm[1] * px + wm[5] * py + wm[9] * pz + wm[13]
                    wz = wm[2] * px + wm[6] * py + wm[10] * pz + wm[14]
                    xformed_pos.append((wx, wy, wz))
                    if wx < min_x: min_x = wx
                    if wx > max_x: max_x = wx
                    if wy < min_y: min_y = wy
                    if wy > max_y: max_y = wy
                    if wz < min_z: min_z = wz
                    if wz > max_z: max_z = wz

                    if has_norm:
                        nx, ny, nz = norm_raw[vi]
                        wnx = nm3[0] * nx + nm3[3] * ny + nm3[6] * nz
                        wny = nm3[1] * nx + nm3[4] * ny + nm3[7] * nz
                        wnz = nm3[2] * nx + nm3[5] * ny + nm3[8] * nz
                        nl = math.sqrt(wnx * wnx + wny * wny + wnz * wnz)
                        if nl > 1e-6:
                            xformed_norm.append((wnx / nl, wny / nl, wnz / nl))
                        else:
                            xformed_norm.append((0.0, 1.0, 0.0))

                for ti in range(0, len(idxs) - 2, 3):
                    i0, i1, i2 = idxs[ti], idxs[ti + 1], idxs[ti + 2]
                    p0, p1, p2 = xformed_pos[i0], xformed_pos[i1], xformed_pos[i2]
                    if not has_norm:
                        ux, uy, uz = p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]
                        vx, vy, vz = p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]
                        fnx, fny, fnz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
                        fl = math.sqrt(fnx * fnx + fny * fny + fnz * fnz)
                        fn = (fnx / fl, fny / fl, fnz / fl) if fl > 1e-7 else (0.0, 1.0, 0.0)
                        n0 = n1 = n2 = fn
                    else:
                        n0, n1, n2 = xformed_norm[i0], xformed_norm[i1], xformed_norm[i2]

                    if swatch_uv is not None:
                        uv0 = uv1 = uv2 = swatch_uv
                    elif uv_raw is not None:
                        uv0, uv1, uv2 = uv_raw[i0], uv_raw[i1], uv_raw[i2]
                    else:
                        uv0 = uv1 = uv2 = (0.5, 0.5)

                    tri_idxs = []
                    for p, nm, uv in ((p0, n0, uv0), (p1, n1, uv1), (p2, n2, uv2)):
                        qx = max(-32767, min(32767, int(round(p[0] * 1000.0))))
                        qy = max(-32767, min(32767, int(round(p[1] * 1000.0))))
                        qz = max(-32767, min(32767, int(round(p[2] * 1000.0))))
                        qnx = max(-127, min(127, int(round(nm[0] * 127.0))))
                        qny = max(-127, min(127, int(round(nm[1] * 127.0))))
                        qnz = max(-127, min(127, int(round(nm[2] * 127.0))))
                        # Match ThrissurGlWorldRenderer's left-handed screen-right basis
                        # (right = (+fwd.z, 0, -fwd.x)) so all glTF signage text reads cleanly
                        # left-to-right instead of mirror-reversed.
                        raw_u = float(uv[0]) if swatch_uv is not None else (1.0 - float(uv[0]))
                        u_clamped = max(-15.9, min(15.9, raw_u))
                        v_clamped = max(-15.9, min(15.9, float(uv[1])))
                        qu = max(-32767, min(32767, int(round(u_clamped * 2000.0))))
                        qv = max(-32767, min(32767, int(round(v_clamped * 2000.0))))
                        key = (qx, qy, qz, qnx, qny, qnz, qu, qv, layer, 0)
                        v_out = vert_map.get(key)
                        if v_out is None:
                            v_out = len(vertices)
                            if v_out >= 65535:
                                raise RuntimeError(f"Model {model_id} exceeded 65535 vertices!")
                            vert_map[key] = v_out
                            vertices.append((qx, qy, qz, qnx, qny, qnz, qu, qv, layer, 0, fallback_rgb[0], fallback_rgb[1], fallback_rgb[2]))
                        tri_idxs.append(v_out)
                    if tri_idxs[0] != tri_idxs[1] and tri_idxs[1] != tri_idxs[2] and tri_idxs[2] != tri_idxs[0]:
                        indices.append((tri_idxs[0], tri_idxs[1], tri_idxs[2]))

        half_x = max(abs(min_x), abs(max_x))
        half_z = max(abs(min_z), abs(max_z))
        height_y = max_y
        print(
            f"Compiled {model_id:22s}: verts={len(vertices):5d} tris={len(indices):5d} "
            f"X=[{min_x:.2f},{max_x:.2f}] Y=[{min_y:.2f},{max_y:.2f}] Z=[{min_z:.2f},{max_z:.2f}]"
        )
        compiled_models.append((model_id, cat_id, half_x, half_z, height_y, vertices, indices))

    # Build 16x16 solid-color swatch tile at SWATCH_LAYER
    swatch_cell = TILE_SIZE // 16
    swatch_tile = bytearray(TILE_SIZE * TILE_SIZE * 4)
    for s_idx, (r, g_c, b_c) in enumerate(swatch_colors):
        col = s_idx % 16
        row = s_idx // 16
        for py in range(row * swatch_cell, (row + 1) * swatch_cell):
            for px in range(col * swatch_cell, (col + 1) * swatch_cell):
                off = (py * TILE_SIZE + px) * 4
                swatch_tile[off + 0] = r
                swatch_tile[off + 1] = g_c
                swatch_tile[off + 2] = b_c
                swatch_tile[off + 3] = 255

    # Assemble 8x3 = 24-tile RGBA texture atlas PNG
    atlas_w = ATLAS_COLS * TILE_SIZE
    atlas_h = ATLAS_ROWS * TILE_SIZE
    atlas_buf = bytearray(atlas_w * atlas_h * 4)

    def blit_tile(layer_idx, tile_bytes):
        col = layer_idx % ATLAS_COLS
        row = layer_idx // ATLAS_COLS
        for y in range(TILE_SIZE):
            dst_off = ((row * TILE_SIZE + y) * atlas_w + col * TILE_SIZE) * 4
            src_off = y * TILE_SIZE * 4
            atlas_buf[dst_off : dst_off + TILE_SIZE * 4] = tile_bytes[src_off : src_off + TILE_SIZE * 4]

    for li, tb in enumerate(texture_tiles):
        blit_tile(li, tb)
    blit_tile(SWATCH_LAYER, bytes(swatch_tile))

    write_png_rgba(OUT_ATLAS, atlas_w, atlas_h, bytes(atlas_buf))
    print(f"Wrote atlas -> {OUT_ATLAS} ({os.path.getsize(OUT_ATLAS)/1024:.1f} KB, {len(texture_tiles)} textures + {len(swatch_colors)} PBR swatches)")

    # Write Z1M2 binary mesh file
    os.makedirs(os.path.dirname(OUT_BIN), exist_ok=True)
    with open(OUT_BIN, "wb") as f:
        f.write(b"Z1M2")
        f.write(struct.pack("<I", len(compiled_models)))
        for model_id, cat_id, half_x, half_z, height_y, vertices, indices in compiled_models:
            id_b = model_id.encode("utf-8")
            f.write(struct.pack("<HBfffII", len(id_b), cat_id, half_x, half_z, height_y, len(vertices), len(indices)))
            f.write(id_b)
            for v in vertices:
                f.write(struct.pack("<hhhbbbhhBBBBB", *v))
            for i0, i1, i2 in indices:
                f.write(struct.pack("<HHH", i0, i1, i2))

    print(f"Wrote binary meshes -> {OUT_BIN} ({os.path.getsize(OUT_BIN)/1024:.1f} KB)")


if __name__ == "__main__":
    main()
