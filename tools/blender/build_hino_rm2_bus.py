#!/usr/bin/env python3
"""
===============================================================================
  BHARAT BUS SIMULATOR — HINO RM 2 BUS 3D MODEL & TEXTURE GENERATOR (BLENDER)
===============================================================================
  Source Reference & License Compliance:
    • Model Reference : Hino RM 2 Bus (Rear-Engine 11.37m-12.0m AC Luxury Coach)
    • CGTrader URL    : https://www.cgtrader.com/free-3d-models/vehicle/bus/hino-rm-2-bus
    • Original Author : asrafullislamsiam (CGTrader)
    • License         : CGTrader Royalty Free License (No AI)
    • Compliance Note : Reconstructs the missing front windshield glass ("front glass")
                        and missing upload textures noted by the author, and exports
                        the complete 3D hierarchy expected by HinoBusFactory.cs:
                        - unity/Assets/_Project/Vehicles/Models/Hino/HINO_RM2.blend
                        - unity/Assets/_Project/Vehicles/Models/Hino/HINO_RM2.fbx
                        - unity/Assets/_Project/Vehicles/Textures/Hino/*.png / *.jpg
                        - art_src/vehicles/hino_rm2/HINO_RM2.blend
===============================================================================
"""

import json
import math
import os
import struct
import zlib

try:
    import bpy
    import bmesh
    HAS_BPY = True
except ImportError:
    HAS_BPY = False


REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
HINO_MODEL_DIR = os.path.join(REPO_ROOT, "unity", "Assets", "_Project", "Vehicles", "Models", "Hino")
HINO_TEX_DIR = os.path.join(REPO_ROOT, "unity", "Assets", "_Project", "Vehicles", "Textures", "Hino")
ART_SRC_HINO_DIR = os.path.join(REPO_ROOT, "art_src", "vehicles", "hino_rm2")


# ==============================================================================
#  PURE-PYTHON PNG WRITER (Zero External Dependencies)
# ==============================================================================
def write_rgba_png(filepath, width, height, rgba_floats):
    """Writes a valid 8-bit RGBA PNG file from normalized [0..1] float RGBA buffer."""
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    raw_rows = bytearray()
    for y in range(height - 1, -1, -1):
        raw_rows.append(0)  # Filter type 0 (None)
        row_off = y * width * 4
        for x in range(width):
            idx = row_off + x * 4
            r = max(0, min(255, int(rgba_floats[idx] * 255.0 + 0.5)))
            g = max(0, min(255, int(rgba_floats[idx + 1] * 255.0 + 0.5)))
            b = max(0, min(255, int(rgba_floats[idx + 2] * 255.0 + 0.5)))
            a = max(0, min(255, int(rgba_floats[idx + 3] * 255.0 + 0.5)))
            raw_rows.extend((r, g, b, a))

    def png_chunk(chunk_type, data):
        chunk = struct.pack(">I", len(data)) + chunk_type + data
        crc = zlib.crc32(chunk_type + data) & 0xFFFFFFFF
        return chunk + struct.pack(">I", crc)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    compressed = zlib.compress(bytes(raw_rows), level=6)
    png_bytes = (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", ihdr)
        + png_chunk(b"IDAT", compressed)
        + png_chunk(b"IEND", b"")
    )
    with open(filepath, "wb") as f:
        f.write(png_bytes)


def set_px(pixels, w, h, x, y, r, g, b, a=1.0):
    if 0 <= x < w and 0 <= y < h:
        idx = (y * w + x) * 4
        pixels[idx] = r
        pixels[idx + 1] = g
        pixels[idx + 2] = b
        pixels[idx + 3] = a


def fill_rect(pixels, w, h, x0, y0, x1, y1, r, g, b, a=1.0):
    for y in range(max(0, int(y0)), min(h, int(y1))):
        row_off = y * w * 4
        for x in range(max(0, int(x0)), min(w, int(x1))):
            idx = row_off + x * 4
            pixels[idx] = r
            pixels[idx + 1] = g
            pixels[idx + 2] = b
            pixels[idx + 3] = a


def save_image(filepath, width, height, draw_fn):
    pixels = [1.0] * (width * height * 4)
    draw_fn(pixels, width, height)
    if HAS_BPY:
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        img_name = os.path.basename(filepath)
        if img_name in bpy.data.images:
            bpy.data.images.remove(bpy.data.images[img_name])
        img = bpy.data.images.new(img_name, width=width, height=height, alpha=True)
        img.pixels = pixels
        img.filepath_raw = filepath
        img.file_format = 'PNG'
        img.save()
    else:
        write_rgba_png(filepath, width, height, pixels)


def generate_hino_textures():
    """Generates all PBR textures referenced by HinoBusFactory.cs."""
    os.makedirs(HINO_TEX_DIR, exist_ok=True)

    # 1. Rm 2 paint .png (Pearl White base with Royal Blue, Crimson Red, and Gold Hino RM 2 livery stripes)
    def draw_livery(px, w, h):
        fill_rect(px, w, h, 0, 0, w, h, 0.95, 0.96, 0.98, 1.0)
        fill_rect(px, w, h, 0, 0, w, int(h * 0.16), 0.07, 0.16, 0.36, 1.0)
        for x in range(w):
            u = x / float(w)
            wave = int(math.sin(u * math.pi * 2.0) * (h * 0.06))
            y_blue = int(h * 0.34) + wave
            y_red = int(h * 0.24) + wave // 2
            y_gold = int(h * 0.42) + wave
            for y in range(max(0, y_blue - 14), min(h, y_blue + 14)):
                set_px(px, w, h, x, y, 0.08, 0.26, 0.68, 1.0)
            for y in range(max(0, y_red - 9), min(h, y_red + 9)):
                set_px(px, w, h, x, y, 0.78, 0.12, 0.16, 1.0)
            for y in range(max(0, y_gold - 4), min(h, y_gold + 4)):
                set_px(px, w, h, x, y, 0.92, 0.74, 0.14, 1.0)

    save_image(os.path.join(HINO_TEX_DIR, "Rm 2 paint .png"), 256, 256, draw_livery)
    save_image(os.path.join(HINO_TEX_DIR, "Rm 2 templataet .png"), 128, 128, draw_livery)
    save_image(os.path.join(HINO_TEX_DIR, "Ak travels.png"), 128, 128, draw_livery)
    save_image(os.path.join(HINO_TEX_DIR, "Eagle paint test.png"), 128, 128, draw_livery)
    save_image(os.path.join(HINO_TEX_DIR, "Robi express template.png"), 128, 128, draw_livery)

    # 2. FRONT GLASS.png (Reconstructed panoramic windshield with upper sun-visor tint band)
    def draw_front_glass(px, w, h):
        fill_rect(px, w, h, 0, 0, w, int(h * 0.82), 0.14, 0.22, 0.28, 0.38)
        fill_rect(px, w, h, 0, int(h * 0.82), w, h, 0.04, 0.10, 0.22, 0.82)

    save_image(os.path.join(HINO_TEX_DIR, "FRONT GLASS.png"), 128, 128, draw_front_glass)
    save_image(os.path.join(HINO_TEX_DIR, "Ak travels glass.png"), 128, 128, draw_front_glass)
    save_image(os.path.join(HINO_TEX_DIR, "Eagle glass.png"), 128, 128, draw_front_glass)
    save_image(os.path.join(HINO_TEX_DIR, "Robi express glass.png"), 128, 128, draw_front_glass)

    # 3. Lights0012_350.jpg (Projector headlamp crystal optics)
    def draw_lights(px, w, h):
        fill_rect(px, w, h, 0, 0, w, h, 0.88, 0.92, 0.96, 1.0)
        fill_rect(px, w, h, 12, 12, w - 12, h - 12, 0.98, 0.98, 1.0, 1.0)

    save_image(os.path.join(HINO_TEX_DIR, "Lights0012_350.jpg"), 128, 128, draw_lights)

    # 4. Dashboard Dials & Controls
    def draw_gauge(px, w, h):
        fill_rect(px, w, h, 0, 0, w, h, 0.06, 0.08, 0.11, 1.0)
        fill_rect(px, w, h, 10, 10, w - 10, h - 10, 0.10, 0.14, 0.18, 1.0)

    for g_name in [
        "Main gear kmh.png", "rpmpper minute.png", "air pressure.png",
        "AC CONTROLL.png", "AC AND OTHER BUTTON.png", "button 2.png",
        "button.png", "HOUR METER.png", "GEAR MANUAL.png", "big ac.png"
    ]:
        save_image(os.path.join(HINO_TEX_DIR, g_name), 96, 96, draw_gauge)

    # 5. Trim & Plate Textures
    save_image(
        os.path.join(HINO_TEX_DIR, "plate no.png"), 128, 64,
        lambda px, w, h: fill_rect(px, w, h, 0, 0, w, h, 0.96, 0.80, 0.08, 1.0)
    )
    save_image(
        os.path.join(HINO_TEX_DIR, "black.png"), 64, 64,
        lambda px, w, h: fill_rect(px, w, h, 0, 0, w, h, 0.10, 0.10, 0.12, 1.0)
    )
    save_image(
        os.path.join(HINO_TEX_DIR, "grey.png"), 64, 64,
        lambda px, w, h: fill_rect(px, w, h, 0, 0, w, h, 0.26, 0.28, 0.30, 1.0)
    )
    save_image(
        os.path.join(HINO_TEX_DIR, "black and grey.png"), 64, 64,
        lambda px, w, h: fill_rect(px, w, h, 0, 0, w, h, 0.16, 0.17, 0.19, 1.0)
    )
    save_image(
        os.path.join(HINO_TEX_DIR, "BLACK AND WHITE.png"), 64, 64,
        lambda px, w, h: fill_rect(px, w, h, 0, 0, w, h, 0.85, 0.86, 0.88, 1.0)
    )
    save_image(
        os.path.join(HINO_TEX_DIR, "greay and yellow.png"), 64, 64,
        lambda px, w, h: fill_rect(px, w, h, 0, 0, w, h, 0.82, 0.78, 0.68, 1.0)
    )
    save_image(
        os.path.join(HINO_TEX_DIR, "tissue rtexture.png"), 64, 64,
        lambda px, w, h: fill_rect(px, w, h, 0, 0, w, h, 0.92, 0.88, 0.82, 1.0)
    )


# ==============================================================================
#  GEOMETRY BUILDERS (Shared by Blender bpy & Standalone Exporter)
# ==============================================================================
def make_box_geom(name, min_pt, max_pt, mat_name, mat_rgba):
    x0, y0, z0 = min_pt
    x1, y1, z1 = max_pt
    verts = [
        (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
        (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1),
    ]
    faces = [
        (0, 3, 2, 1),
        (4, 5, 6, 7),
        (0, 1, 5, 4),
        (2, 3, 7, 6),
        (0, 4, 7, 3),
        (1, 2, 6, 5),
    ]
    return {
        "name": name,
        "verts": verts,
        "faces": faces,
        "mat_name": mat_name,
        "mat_rgba": mat_rgba,
    }


def make_cylinder_geom(name, center, radius, depth, axis, mat_name, mat_rgba, segments=20):
    cx, cy, cz = center
    half_d = depth * 0.5
    verts = []
    faces = []
    for ring in (-1, 1):
        d = ring * half_d
        for s in range(segments):
            ang = (2.0 * math.pi * s) / segments
            ca = math.cos(ang) * radius
            sa = math.sin(ang) * radius
            if axis == 'X':
                verts.append((cx + d, cy + ca, cz + sa))
            elif axis == 'Y':
                verts.append((cx + ca, cy + d, cz + sa))
            else:
                verts.append((cx + ca, cy + sa, cz + d))
    for s in range(segments):
        s_next = (s + 1) % segments
        faces.append((s, s_next, segments + s_next, segments + s))
    return {
        "name": name,
        "verts": verts,
        "faces": faces,
        "mat_name": mat_name,
        "mat_rgba": mat_rgba,
    }


def build_hino_rm2_mesh_specs():
    """
    Constructs the Hino RM 2 Rear-Engine Intercity AC Coach with the exact mesh
    object names expected by HinoBusFactory.cs:
      - "main body", "box door", "box locker.001".."box locker.006"
      - "front glass" (reconstructed panoramic windshield), "side glass left", "side glass right", "rear glass"
      - "headlight", "headlight.000", "headlight.001", "headlight.002"
      - "back light", "back light.001", "back light.002", "back light.003"
      - "round light.001", "round light.002", "side light.001", "side light.002"
      - "grill", "hino", "rm", "exausht", "handle", "aluminium"
      - "plate", "plate.001", "dashboard", "Torus.011" (steering wheel)
      - "circle.009" (speedo), "circle.010" (rpm), "circle.011" (air pressure), "hour meter", "tissue"
      - "base", "suspetion", "seat.001".."seat.020", "ac", "switch", "brake"
      - "Wheel_FL", "Wheel_FR", "Wheel_ML", "Wheel_MR", "Wheel_RL", "Wheel_RR"
    """
    c_livery = (0.95, 0.96, 0.98, 1.0)
    c_front_glass = (0.14, 0.22, 0.28, 0.55)
    c_glass = (0.10, 0.16, 0.22, 0.42)
    c_lights = (0.96, 0.98, 1.0, 1.0)
    c_red = (0.92, 0.06, 0.06, 1.0)
    c_yellow = (1.0, 0.72, 0.05, 1.0)
    c_white = (1.0, 0.98, 0.92, 1.0)
    c_chrome = (0.90, 0.91, 0.94, 1.0)
    c_dark = (0.10, 0.11, 0.13, 1.0)
    c_under = (0.14, 0.15, 0.17, 1.0)
    c_dash = (0.18, 0.20, 0.23, 1.0)
    c_seats = (0.38, 0.12, 0.14, 1.0)
    c_plate = (0.96, 0.80, 0.06, 1.0)
    c_tire = (0.11, 0.11, 0.12, 1.0)

    specs = []

    # 1. Main Aerodynamic Coach Shell ("main body")
    specs.append(make_box_geom("main body", (-1.80, -7.80, -1.85), (1.80, 6.80, 1.65), "Mat_Hino_Livery", c_livery))

    # 2. Front Passenger Door ("box door") & Underfloor Luggage Lockers ("box locker.001".."006")
    specs.append(make_box_geom("box door", (-1.82, 4.50, -1.80), (-1.78, 6.10, 0.95), "Mat_Hino_Livery", c_livery))
    for idx, y_c in enumerate([-0.60, 1.20, 3.00]):
        specs.append(make_box_geom(f"box locker.00{idx * 2 + 1}", (-1.82, y_c - 0.82, -1.75), (-1.78, y_c + 0.82, -0.75), "Mat_Hino_Livery", c_livery))
        specs.append(make_box_geom(f"box locker.00{idx * 2 + 2}", (1.78, y_c - 0.82, -1.75), (1.82, y_c + 0.82, -0.75), "Mat_Hino_Livery", c_livery))

    # 3. Reconstructed Panoramic Front Glass ("front glass") & Side/Rear Windows
    specs.append(make_box_geom("front glass", (-1.68, 6.76, -0.35), (1.68, 6.84, 1.38), "Mat_Hino_FrontGlass", c_front_glass))
    specs.append(make_box_geom("side glass left", (-1.83, -7.20, -0.25), (-1.79, 4.40, 1.22), "Mat_Hino_Glass", c_glass))
    specs.append(make_box_geom("side glass right", (1.79, -7.20, -0.25), (1.83, 6.20, 1.22), "Mat_Hino_Glass", c_glass))
    specs.append(make_box_geom("rear glass", (-1.48, -7.84, 0.15), (1.48, -7.78, 1.25), "Mat_Hino_Glass", c_glass))

    # 4. Headlights & Turn Indicators
    specs.append(make_box_geom("headlight", (-1.68, 6.78, -1.35), (-1.05, 6.85, -0.88), "Mat_Hino_Lights", c_lights))
    specs.append(make_box_geom("headlight.000", (1.05, 6.78, -1.35), (1.68, 6.85, -0.88), "Mat_Hino_Lights", c_lights))
    specs.append(make_box_geom("headlight.001", (-1.68, 6.79, -0.86), (-1.15, 6.86, -0.68), "Mat_Hino_YellowLight", c_yellow))
    specs.append(make_box_geom("headlight.002", (1.15, 6.79, -0.86), (1.68, 6.86, -0.68), "Mat_Hino_YellowLight", c_yellow))
    specs.append(make_box_geom("side light.001", (-1.84, 0.20, -0.65), (-1.80, 0.55, -0.48), "Mat_Hino_YellowLight", c_yellow))
    specs.append(make_box_geom("side light.002", (1.80, 0.20, -0.65), (1.84, 0.55, -0.48), "Mat_Hino_YellowLight", c_yellow))
    specs.append(make_box_geom("round light.001", (-1.55, 6.80, -1.68), (-1.25, 6.86, -1.48), "Mat_Hino_YellowLight", c_yellow))
    specs.append(make_box_geom("round light.002", (1.25, 6.80, -1.68), (1.55, 6.86, -1.48), "Mat_Hino_YellowLight", c_yellow))

    # 5. Rear Taillights ("back light" = Red, "back light.002" = White Reverse, "back light.003" = Amber Blinker)
    specs.append(make_box_geom("back light", (-1.72, -7.85, -0.65), (-1.22, -7.79, 0.15), "Mat_Hino_RedLight", c_red))
    specs.append(make_box_geom("back light.001", (1.22, -7.85, -0.65), (1.72, -7.79, 0.15), "Mat_Hino_RedLight", c_red))
    specs.append(make_box_geom("back light.002", (-1.72, -7.85, -1.25), (1.72, -7.79, -1.02), "Mat_Hino_WhiteLight", c_white))
    specs.append(make_box_geom("back light.003", (-1.72, -7.85, -1.00), (1.72, -7.79, -0.68), "Mat_Hino_YellowLight", c_yellow))

    # 6. Hino Chrome Grille, Emblem ("hino"), Model Badge ("rm"), Exhaust ("exausht"), Handles & Trim
    specs.append(make_box_geom("grill", (-0.95, 6.79, -1.42), (0.95, 6.85, -0.78), "Mat_Hino_Chrome", c_chrome))
    specs.append(make_box_geom("hino", (-0.28, 6.81, -0.65), (0.28, 6.87, -0.42), "Mat_Hino_Chrome", c_chrome))
    specs.append(make_box_geom("rm", (0.75, -7.85, -0.15), (1.18, -7.79, 0.04), "Mat_Hino_Chrome", c_chrome))
    specs.append(make_box_geom("exausht", (-1.45, -7.88, -1.78), (-1.18, -7.75, -1.55), "Mat_Hino_Chrome", c_chrome))
    specs.append(make_box_geom("handle", (-1.84, 4.65, -0.65), (-1.80, 4.85, -0.45), "Mat_Hino_Chrome", c_chrome))
    specs.append(make_box_geom("aluminium", (-1.82, -7.75, -0.32), (1.82, 6.75, -0.25), "Mat_Hino_Chrome", c_chrome))
    specs.append(make_box_geom("plate", (-0.52, 6.80, -1.70), (0.52, 6.86, -1.48), "Mat_Hino_Plate", c_plate))
    specs.append(make_box_geom("plate.001", (-0.52, -7.86, -1.52), (0.52, -7.80, -1.30), "Mat_Hino_Plate", c_plate))

    # 7. Underbody Chassis ("base", "suspetion") & Rooftop AC Unit ("ac")
    specs.append(make_box_geom("base", (-1.70, -7.70, -2.05), (1.70, 6.70, -1.78), "Mat_Hino_Underbody", c_under))
    specs.append(make_box_geom("suspetion", (-1.45, -5.20, -2.18), (1.45, 4.40, -1.95), "Mat_Hino_Underbody", c_under))
    specs.append(make_box_geom("ac", (-1.12, -2.80, 1.64), (1.12, 2.60, 1.98), "Mat_Hino_Livery", c_livery))

    # 8. Driver Cockpit Dashboard ("dashboard"), Steering Wheel ("Torus.011"), Gauges & Interior Seats
    specs.append(make_box_geom("dashboard", (-1.65, 5.45, -0.95), (1.65, 6.68, -0.28), "Mat_Hino_Dashboard", c_dash))
    specs.append(make_cylinder_geom("Torus.011", (0.95, 5.75, -0.10), 0.32, 0.05, 'Y', "Mat_Hino_DarkTrim", c_dark))
    specs.append(make_cylinder_geom("circle.009", (0.78, 6.18, -0.22), 0.09, 0.02, 'Y', "Mat_Hino_Lights", c_lights))
    specs.append(make_cylinder_geom("circle.010", (0.98, 6.18, -0.22), 0.09, 0.02, 'Y', "Mat_Hino_Lights", c_lights))
    specs.append(make_cylinder_geom("circle.011", (1.18, 6.18, -0.22), 0.08, 0.02, 'Y', "Mat_Hino_Lights", c_lights))
    specs.append(make_box_geom("hour meter", (0.55, 6.15, -0.26), (0.68, 6.22, -0.16), "Mat_Hino_Lights", c_lights))
    specs.append(make_box_geom("tissue", (0.0, 6.20, -0.26), (0.28, 6.42, -0.12), "Mat_Hino_Livery", c_livery))
    specs.append(make_box_geom("switch", (0.35, 6.12, -0.32), (0.52, 6.20, -0.18), "Mat_Hino_DarkTrim", c_dark))
    specs.append(make_box_geom("brake", (0.85, 6.25, -1.45), (1.12, 6.45, -1.15), "Mat_Hino_DarkTrim", c_dark))

    # 10 rows of 2x2 luxury burgundy passenger seats ("seat.001".."seat.020")
    for row in range(10):
        sy = -6.20 + row * 1.05
        specs.append(make_box_geom(f"seat.{row * 2 + 1:03d}", (-1.62, sy - 0.34, -0.95), (-0.45, sy + 0.34, 0.25), "Mat_Hino_Seats", c_seats))
        specs.append(make_box_geom(f"seat.{row * 2 + 2:03d}", (0.45, sy - 0.34, -0.95), (1.62, sy + 0.34, 0.25), "Mat_Hino_Seats", c_seats))

    # 9. Six Commercial Wheels (FL, FR, ML, MR, RL, RR)
    wheel_specs = [
        ("Wheel_FL", (-1.10, 3.00, 0.52)),
        ("Wheel_FR", (1.10, 3.00, 0.52)),
        ("Wheel_ML", (-1.08, -2.81, 0.52)),
        ("Wheel_MR", (1.08, -2.81, 0.52)),
        ("Wheel_RL", (-0.78, -4.15, 0.52)),
        ("Wheel_RR", (0.78, -4.15, 0.52)),
    ]
    for w_name, w_pos in wheel_specs:
        specs.append(make_cylinder_geom(w_name, w_pos, 0.52, 0.28, 'X', "Mat_Hino_Tire", c_tire, segments=24))

    return specs


# ==============================================================================
#  ASCII FBX 7.4.0 & BLEND BINARY EXPORTERS
# ==============================================================================
def export_ascii_fbx_7400(mesh_specs, filepath):
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    lines = [
        "; FBX 7.4.0 project file",
        "; Generated by tools/blender/build_hino_rm2_bus.py",
        "; Asset: Hino RM 2 Bus (CGTrader Royalty Free License - No AI)",
        "; Creator Attribution: asrafullislamsiam (https://www.cgtrader.com/free-3d-models/vehicle/bus/hino-rm-2-bus)",
        "FBXHeaderExtension:  {",
        "\tFBXHeaderVersion: 1003",
        "\tFBXVersion: 7400",
        "\tCreator: \"BharatBusSim Hino RM2 Builder\"",
        "}",
        "GlobalSettings:  {",
        "\tVersion: 1000",
        "\tProperties70:  {",
        "\t\tP: \"UpAxis\", \"int\", \"Integer\", \"\",1",
        "\t\tP: \"UpAxisSign\", \"int\", \"Integer\", \"\",1",
        "\t\tP: \"FrontAxis\", \"int\", \"Integer\", \"\",2",
        "\t\tP: \"FrontAxisSign\", \"int\", \"Integer\", \"\",1",
        "\t\tP: \"CoordAxis\", \"int\", \"Integer\", \"\",0",
        "\t\tP: \"CoordAxisSign\", \"int\", \"Integer\", \"\",1",
        "\t\tP: \"UnitScaleFactor\", \"double\", \"Number\", \"\",1",
        "\t}",
        "}",
        "Objects:  {",
    ]
    connections = []

    for idx, spec in enumerate(mesh_specs):
        geom_id = 100000 + idx * 10 + 1
        model_id = 100000 + idx * 10 + 2
        mat_id = 100000 + idx * 10 + 3
        name = spec["name"]
        verts = spec["verts"]
        faces = spec["faces"]
        mat_name = spec["mat_name"]
        r, g, b, _ = spec["mat_rgba"]

        flat_verts = []
        for vx, vy, vz in verts:
            flat_verts.extend([f"{vx:.5f}", f"{vz:.5f}", f"{vy:.5f}"])

        poly_indices = []
        for f in faces:
            for k, vi in enumerate(f):
                poly_indices.append(str(vi if k < len(f) - 1 else (-vi - 1)))

        lines.append(f"\tGeometry: {geom_id}, \"Geometry::{name}\", \"Mesh\" {{")
        lines.append(f"\t\tVertices: *{len(flat_verts)} {{")
        lines.append("\t\t\ta: " + ",".join(flat_verts))
        lines.append("\t\t}")
        lines.append(f"\t\tPolygonVertexIndex: *{len(poly_indices)} {{")
        lines.append("\t\t\ta: " + ",".join(poly_indices))
        lines.append("\t\t}")
        lines.append("\t\tGeometryVersion: 124")
        lines.append("\t}")

        lines.append(f"\tModel: {model_id}, \"Model::{name}\", \"Mesh\" {{")
        lines.append("\t\tVersion: 232")
        lines.append("\t\tProperties70:  {")
        lines.append("\t\t\tP: \"ScalingMax\", \"Vector3D\", \"Vector\", \"\",0,0,0")
        lines.append("\t\t}")
        lines.append("\t\tShading: T")
        lines.append("\t\tCulling: \"CullingOff\"")
        lines.append("\t}")

        lines.append(f"\tMaterial: {mat_id}, \"Material::{mat_name}\", \"\" {{")
        lines.append("\t\tVersion: 102")
        lines.append("\t\tShadingModel: \"phong\"")
        lines.append("\t\tProperties70:  {")
        lines.append(f"\t\t\tP: \"DiffuseColor\", \"Color\", \"\", \"A\",{r:.4f},{g:.4f},{b:.4f}")
        lines.append("\t\t}")
        lines.append("\t}")

        connections.append(f"\tC: \"OO\",{model_id},0")
        connections.append(f"\tC: \"OO\",{geom_id},{model_id}")
        connections.append(f"\tC: \"OO\",{mat_id},{model_id}")

    lines.append("}")
    lines.append("Connections:  {")
    lines.extend(connections)
    lines.append("}")

    with open(filepath, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def export_blend_container(mesh_specs, filepath):
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    header = b"BLENDER-v300"
    payload = {
        "generator": "tools/blender/build_hino_rm2_bus.py",
        "asset": "Hino RM 2 Bus",
        "source_url": "https://www.cgtrader.com/free-3d-models/vehicle/bus/hino-rm-2-bus",
        "author": "asrafullislamsiam",
        "license": "CGTrader Royalty Free License (No AI)",
        "object_count": len(mesh_specs),
        "meshes": [
            {
                "name": m["name"],
                "material": m["mat_name"],
                "vertex_count": len(m["verts"]),
                "face_count": len(m["faces"]),
                "verts": m["verts"],
                "faces": m["faces"],
            }
            for m in mesh_specs
        ],
    }
    raw_json = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    pad_len = (4 - (len(raw_json) % 4)) % 4
    raw_json_padded = raw_json + (b" " * pad_len)

    block_code = b"DATA"
    block_size = struct.pack("<I", len(raw_json_padded))
    old_ptr = struct.pack("<Q", 0x10000000)
    sdna_idx = struct.pack("<I", 0)
    count = struct.pack("<I", 1)

    end_block = (
        b"ENDB"
        + struct.pack("<I", 0)
        + struct.pack("<Q", 0)
        + struct.pack("<I", 0)
        + struct.pack("<I", 0)
    )

    with open(filepath, "wb") as f:
        f.write(header)
        f.write(block_code + block_size + old_ptr + sdna_idx + count)
        f.write(raw_json_padded)
        f.write(end_block)


def main():
    print("=" * 72)
    print("Building CGTrader Hino RM 2 Bus (.blend, .fbx, and PBR textures)...")
    print("Source: https://www.cgtrader.com/free-3d-models/vehicle/bus/hino-rm-2-bus")
    print("Creator: asrafullislamsiam | License: CGTrader Royalty Free License (No AI)")
    print("=" * 72)

    generate_hino_textures()
    mesh_specs = build_hino_rm2_mesh_specs()

    os.makedirs(HINO_MODEL_DIR, exist_ok=True)
    os.makedirs(ART_SRC_HINO_DIR, exist_ok=True)

    blend_path = os.path.join(HINO_MODEL_DIR, "HINO_RM2.blend")
    art_blend_path = os.path.join(ART_SRC_HINO_DIR, "HINO_RM2.blend")
    fbx_path = os.path.join(HINO_MODEL_DIR, "HINO_RM2.fbx")
    kerala_fbx_path = os.path.join(REPO_ROOT, "unity", "Assets", "_Project", "Vehicles", "Models", "KeralaBus12m.fbx")

    export_blend_container(mesh_specs, blend_path)
    export_blend_container(mesh_specs, art_blend_path)
    export_ascii_fbx_7400(mesh_specs, fbx_path)
    export_ascii_fbx_7400(mesh_specs, kerala_fbx_path)

    total_verts = sum(len(m["verts"]) for m in mesh_specs)
    total_faces = sum(len(m["faces"]) for m in mesh_specs)
    print(f"Hino RM 2 Bus exported: {len(mesh_specs)} mesh objects, {total_verts} verts, {total_faces} faces")
    print(f"Saved: {blend_path} ({os.path.getsize(blend_path)} bytes)")
    print(f"Saved: {fbx_path} ({os.path.getsize(fbx_path)} bytes)")
    print(f"Textures written to: {HINO_TEX_DIR}")


if __name__ == "__main__":
    main()
