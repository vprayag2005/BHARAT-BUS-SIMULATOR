import json
import math
import os
import struct
import sys
import time

try:
    import bpy
    import bmesh
    from mathutils import Vector
    HAS_BPY = True
except ImportError:
    HAS_BPY = False

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
ROADS_JSON = os.path.join(PROJECT_ROOT, "data", "processed", "thrissur_osm_roads.json")
OUT_BLEND = os.path.join(PROJECT_ROOT, "art_src", "roads", "thrissur_roads", "Thrissur_Roads.blend")
OUT_FBX = os.path.join(PROJECT_ROOT, "art_src", "roads", "thrissur_roads", "Thrissur_Roads.fbx")

MATERIALS_SPEC = [
    ("MAT_Road_Asphalt", (0.14, 0.14, 0.15), 0.85),
    ("MAT_Road_Shoulder", (0.46, 0.30, 0.20), 0.95),
    ("MAT_Road_Curb", (0.72, 0.72, 0.69), 0.80),
    ("MAT_Road_Median", (0.76, 0.76, 0.74), 0.75),
    ("MAT_Road_WhiteLine", (0.95, 0.95, 0.95), 0.45),
    ("MAT_Road_YellowLine", (0.96, 0.78, 0.06), 0.45),
    ("MAT_Kerala_TerracottaRoof", (0.68, 0.24, 0.14), 0.70),
    ("MAT_Kerala_LateriteStone", (0.52, 0.22, 0.13), 0.90),
    ("MAT_Kerala_CreamPlaster", (0.93, 0.90, 0.84), 0.65),
    ("MAT_Temple_Gold", (0.95, 0.76, 0.15), 0.25),
    ("MAT_Beach_Sand", (0.88, 0.78, 0.56), 0.85),
    ("MAT_Arabian_Sea", (0.08, 0.45, 0.68), 0.20),
]


class Vec2:
    __slots__ = ("x", "y")

    def __init__(self, x=0.0, y=0.0):
        self.x = float(x)
        self.y = float(y)

    def __add__(self, other):
        return Vec2(self.x + other.x, self.y + other.y)

    def __sub__(self, other):
        return Vec2(self.x - other.x, self.y - other.y)

    def __mul__(self, s):
        return Vec2(self.x * s, self.y * s)

    @property
    def length(self):
        return math.hypot(self.x, self.y)

    def normalized(self):
        l = self.length
        if l < 1e-6:
            return Vec2(0.0, 1.0)
        return Vec2(self.x / l, self.y / l)

    def lerp(self, other, t):
        return Vec2(self.x + (other.x - self.x) * t, self.y + (other.y - self.y) * t)


def smooth_polyline_2d(pts_xz, closed=False, subdivisions=1):
    """Subdivide polyline with Chaikin corner-cutting for smooth bus driving."""
    pts = [Vec2(p[0], p[2]) for p in pts_xz]
    if len(pts) < 3 or subdivisions < 1:
        return pts
    for _ in range(subdivisions):
        new_pts = []
        n = len(pts)
        if not closed:
            new_pts.append(pts[0])
            for i in range(n - 1):
                p0, p1 = pts[i], pts[i + 1]
                if i > 0:
                    new_pts.append(p0.lerp(p1, 0.25))
                if i < n - 2:
                    new_pts.append(p0.lerp(p1, 0.75))
            new_pts.append(pts[-1])
        else:
            if (pts[0] - pts[-1]).length < 0.05:
                pts = pts[:-1]
            n = len(pts)
            for i in range(n):
                p0, p1 = pts[i], pts[(i + 1) % n]
                new_pts.append(p0.lerp(p1, 0.25))
                new_pts.append(p0.lerp(p1, 0.75))
            new_pts.append(new_pts[0])
        pts = new_pts
    return pts


def compute_frames_2d(pts_2d, closed=False):
    """Return list of (pos_2d, tangent_2d, right_2d, cumulative_dist) in Unity XZ with smoothed miter normals."""
    n = len(pts_2d)
    raw_tangents = []
    for i in range(n):
        if closed and i == n - 1 and n > 2:
            t = pts_2d[1] - pts_2d[i - 1]
        elif closed and i == 0 and n > 2:
            t = pts_2d[1] - pts_2d[-2]
        elif i == 0:
            t = pts_2d[1] - pts_2d[0]
        elif i == n - 1:
            t = pts_2d[i] - pts_2d[i - 1]
        else:
            t = pts_2d[i + 1] - pts_2d[i - 1]
        raw_tangents.append(t.normalized())

    smooth_tangents = []
    for i in range(n):
        if not closed and (i == 0 or i == n - 1):
            smooth_tangents.append(raw_tangents[i])
        else:
            tp = raw_tangents[(i - 1) % n]
            tc = raw_tangents[i]
            tn = raw_tangents[(i + 1) % n]
            smooth_tangents.append((tp * 0.25 + tc * 0.50 + tn * 0.25).normalized())

    frames = []
    dist = 0.0
    for i in range(n):
        if i > 0:
            dist += (pts_2d[i] - pts_2d[i - 1]).length
        t = smooth_tangents[i]
        r = Vec2(t.y, -t.x)
        frames.append((pts_2d[i], t, r, dist))
    return frames


class ProceduralRoadMesh:
    """Platform-independent mesh container holding vertices, quads, normals, UVs, and material indices."""

    def __init__(self, name):
        self.name = name
        self.verts = []     # list of (ux, uy, uz) in Unity coordinates
        self.quads = []     # list of (v0, v1, v2, v3, mat_idx, uv0, uv1, uv2, uv3)

    def add_strip(self, frames, offset_left, offset_right, y_left, y_right, mat_idx, u_scale=1.0, v_scale=0.15, trim_ends_m=0.0):
        total_len = frames[-1][3] if frames else 0.0
        prev_vl = None
        prev_vr = None
        prev_dist = 0.0
        for pos, _t, r, dist in frames:
            if trim_ends_m > 0.0 and (dist < trim_ends_m or (total_len - dist) < trim_ends_m):
                prev_vl = None
                prev_vr = None
                continue
            pl = pos + r * offset_left
            pr = pos + r * offset_right
            vl = len(self.verts)
            self.verts.append((round(pl.x, 3), round(y_left, 4), round(pl.y, 3)))
            vr = len(self.verts)
            self.verts.append((round(pr.x, 3), round(y_right, 4), round(pr.y, 3)))
            if prev_vl is not None:
                v0 = round(prev_dist * v_scale, 3)
                v1 = round(dist * v_scale, 3)
                self.quads.append((
                    prev_vl, vl, vr, prev_vr, mat_idx,
                    (0.0, v0), (0.0, v1), (u_scale, v1), (u_scale, v0),
                ))
            prev_vl = vl
            prev_vr = vr
            prev_dist = dist

    def add_dashed_strip(self, frames, center_offset, half_width, y_height, mat_idx, dash_len=3.0, gap_len=4.5, trim_ends_m=0.0):
        total_len = frames[-1][3] if frames else 0.0
        period = dash_len + gap_len
        for i in range(len(frames) - 1):
            p0, _t0, r0, d0 = frames[i]
            p1, _t1, r1, d1 = frames[i + 1]
            if trim_ends_m > 0.0 and (d0 < trim_ends_m or (total_len - d1) < trim_ends_m):
                continue
            if (d0 % period) > dash_len:
                continue
            if (d1 - d0) < 0.05:
                continue
            c0l = p0 + r0 * (center_offset - half_width)
            c0r = p0 + r0 * (center_offset + half_width)
            c1l = p1 + r1 * (center_offset - half_width)
            c1r = p1 + r1 * (center_offset + half_width)
            base = len(self.verts)
            self.verts.append((round(c0l.x, 3), round(y_height, 4), round(c0l.y, 3)))
            self.verts.append((round(c1l.x, 3), round(y_height, 4), round(c1l.y, 3)))
            self.verts.append((round(c1r.x, 3), round(y_height, 4), round(c1r.y, 3)))
            self.verts.append((round(c0r.x, 3), round(y_height, 4), round(c0r.y, 3)))
            self.quads.append((
                base, base + 1, base + 2, base + 3, mat_idx,
                (0.0, 0.0), (0.0, 1.0), (1.0, 1.0), (1.0, 0.0),
            ))

    def add_box(self, min_x, min_y, min_z, max_x, max_y, max_z, mat_idx):
        b = len(self.verts)
        self.verts.extend([
            (round(min_x, 3), round(min_y, 3), round(min_z, 3)),
            (round(max_x, 3), round(min_y, 3), round(min_z, 3)),
            (round(max_x, 3), round(max_y, 3), round(min_z, 3)),
            (round(min_x, 3), round(max_y, 3), round(min_z, 3)),
            (round(min_x, 3), round(min_y, 3), round(max_z, 3)),
            (round(max_x, 3), round(min_y, 3), round(max_z, 3)),
            (round(max_x, 3), round(max_y, 3), round(max_z, 3)),
            (round(min_x, 3), round(max_y, 3), round(max_z, 3)),
        ])
        faces = [
            (b + 3, b + 2, b + 6, b + 7),  # Top
            (b + 0, b + 1, b + 2, b + 3),  # South (-Z)
            (b + 5, b + 4, b + 7, b + 6),  # North (+Z)
            (b + 4, b + 0, b + 3, b + 7),  # West (-X)
            (b + 1, b + 5, b + 6, b + 2),  # East (+X)
        ]
        for v0, v1, v2, v3 in faces:
            self.quads.append((
                v0, v1, v2, v3, mat_idx,
                (0.0, 0.0), (1.0, 0.0), (1.0, 1.0), (0.0, 1.0),
            ))


def build_landmark_meshes():
    """Builds 3D Blender architectural meshes for the 6 aligned Thrissur & Coastal Landmarks."""
    landmarks = []

    # 1. Sakthan Thampuran Bus Stand (Aligned Left Entry X=2..12 & Right Exit X=19..31 at Z=-140)
    sak = ProceduralRoadMesh("LM_Sakthan_Bus_Stand")
    sak.add_box(-22.0, 0.02, -220.0, 48.0, 0.058, -140.0, mat_idx=0)
    sak.add_box(-22.0, 0.06, -140.6, 2.0, 1.45, -139.8, mat_idx=7)
    sak.add_box(12.0, 0.06, -140.6, 19.0, 1.35, -139.8, mat_idx=8)
    sak.add_box(31.0, 0.06, -140.6, 48.0, 1.45, -139.8, mat_idx=7)
    sak.add_box(-21.0, 6.2, -185.5, 46.5, 6.85, -160.5, mat_idx=6)
    sak.add_box(-21.0, 6.2, -217.5, 46.5, 6.85, -197.0, mat_idx=6)
    sak.add_box(-22.0, 0.06, -218.0, -18.5, 8.8, -146.0, mat_idx=8)
    sak.add_box(-21.8, 8.8, -164.0, -18.5, 16.0, -159.0, mat_idx=6)
    # Left Entry Gate Arch (X=2.0..12.0, Z=-140.0) & Right Exit Gate Arch (X=19.0..31.0, Z=-140.0)
    sak.add_box(1.4, 0.06, -140.4, 2.1, 6.2, -139.6, mat_idx=6)
    sak.add_box(11.9, 0.06, -140.4, 12.6, 6.2, -139.6, mat_idx=6)
    sak.add_box(1.4, 4.8, -140.4, 12.6, 6.2, -139.6, mat_idx=8)
    sak.add_box(18.4, 0.06, -140.4, 19.1, 6.2, -139.6, mat_idx=6)
    sak.add_box(30.9, 0.06, -140.4, 31.6, 6.2, -139.6, mat_idx=6)
    sak.add_box(18.4, 4.8, -140.4, 31.6, 6.2, -139.6, mat_idx=8)
    landmarks.append(sak)

    # 2. Swaraj Round, Thekkinkadu Maidan Tree Grove, Nehru Park & Sree Vadakkumnathan Temple Complex (Satellite-Matched)
    swa = ProceduralRoadMesh("LM_Swaraj_Round_Vadakkumnathan")
    tcx, tcz = 1.8, 4.5
    w_r = 11.8
    # Elevated Thekkinkadu Maidan Hillock Terrace & 4 Sloped Granite Nada Walkways
    swa.add_box(-33.5, 0.06, -24.5, 39.5, 0.42, 43.5, mat_idx=1)
    swa.add_box(tcx + 3.8, 0.08, -25.8, tcx + 8.6, 0.65, tcz - w_r, mat_idx=2)   # South Nada (M.O. Road)
    swa.add_box(tcx - 3.0, 0.08, tcz + w_r, tcx + 1.8, 0.65, 45.2, mat_idx=2)    # North Nada (Shornur Road)
    swa.add_box(-35.2, 0.08, tcz - 3.6, tcx - w_r, 0.65, tcz + 1.8, mat_idx=2)   # West Nada Divided Avenue (M.G. Road)
    swa.add_box(tcx + w_r, 0.08, tcz - 2.2, 40.5, 0.65, tcz + 2.2, mat_idx=2)    # East Nada (Palghat Road)
    # Maryada Temple Compound Wall with Whitewashed Base, Laterite Upper Wall & Terracotta Coping
    swa.add_box(tcx - w_r, 0.64, tcz - w_r, tcx + w_r, 3.35, tcz - w_r + 0.85, mat_idx=7)
    swa.add_box(tcx - w_r, 0.64, tcz + w_r - 0.85, tcx + w_r, 3.35, tcz + w_r, mat_idx=7)
    swa.add_box(tcx - w_r, 0.64, tcz - w_r, tcx - w_r + 0.85, 3.35, tcz + w_r, mat_idx=7)
    swa.add_box(tcx + w_r - 0.85, 0.64, tcz - w_r, tcx + w_r, 3.35, tcz + w_r, mat_idx=7)
    # 4 Cardinal 3-Tiered Kerala Temple Gopurams nestled inside the Thekkinkadu Tree Grove
    for gx, gz, gw, gd in [
        (tcx + 6.2, tcz - w_r, 3.6, 2.2),  # South Gopuram (faces M.O. Road)
        (tcx - 0.6, tcz + w_r, 3.6, 2.2),  # North Gopuram (faces Shornur Road)
        (tcx - w_r, tcz - 1.0, 2.2, 3.6),  # West Gopuram (faces M.G. / West Fort Road)
        (tcx + w_r, tcz + 0.0, 2.2, 3.6),  # East Gopuram (faces Palghat / Mannuthy Road)
    ]:
        swa.add_box(gx - gw, 0.64, gz - gd, gx + gw, 4.4, gz + gd, mat_idx=8)
        swa.add_box(gx - gw * 1.18, 4.4, gz - gd * 1.18, gx + gw * 1.18, 6.8, gz + gd * 1.18, mat_idx=6)
        swa.add_box(gx - gw * 0.82, 6.8, gz - gd * 0.82, gx + gw * 0.82, 9.8, gz + gd * 0.82, mat_idx=6)
        swa.add_box(gx - gw * 0.65, 9.8, gz - gd * 0.65, gx + gw * 0.65, 13.0, gz + gd * 0.65, mat_idx=6)
        swa.add_box(gx - 0.5, 13.0, gz - 0.5, gx + 0.5, 14.3, gz + 0.5, mat_idx=9)
    # Inner Nalambalam, Vadakkumnathan & Sri Rama Sreekovils, Koothambalam & Golden Flagstaff
    swa.add_box(tcx - 7.6, 0.64, tcz - 7.6, tcx + 7.6, 4.3, tcz + 7.6, mat_idx=8)
    swa.add_box(tcx - 4.2, 4.3, tcz - 3.2, tcx + 4.2, 9.6, tcz + 5.2, mat_idx=6)
    swa.add_box(tcx - 0.6, 9.6, tcz + 0.5, tcx + 0.6, 11.4, tcz + 1.7, mat_idx=9)
    swa.add_box(tcx - 10.6, 0.64, tcz + 1.8, tcx - 8.1, 7.6, tcz + 8.6, mat_idx=6)  # Koothambalam
    # Sacred Temple Pond (Kulam) North of Temple inside Tree Grove & East Water Authority Building + Tanks
    swa.add_box(0.5, 0.35, 21.0, 9.2, 0.58, 28.8, mat_idx=11)
    swa.add_box(19.2, 0.42, 7.0, 23.6, 5.2, 18.5, mat_idx=2)
    for wtz in (9.2, 13.5, 17.5):
        swa.add_box(25.2, 0.42, wtz - 1.6, 28.4, 3.8, wtz + 1.6, mat_idx=3)
    # Dense Sacred Tree Grove (Thekkinkadu) around Sree Vadakkumnathan Temple & Nehru Park (NE Quadrant)
    for trx, trz, trr, trh in [
        (-13.8, 11.2, 4.8, 12.2), (-14.2, 5.4, 4.4, 11.5), (-14.0, -3.2, 4.6, 11.8), (-13.2, -9.2, 5.0, 12.6),
        (-9.8, 17.2, 4.6, 12.0), (-5.2, 18.0, 4.4, 11.4), (5.8, 17.8, 4.8, 12.4), (11.2, 17.2, 4.6, 11.8),
        (-7.5, 23.5, 5.0, 12.8), (-2.2, 24.8, 4.6, 11.6), (11.8, 23.2, 4.8, 12.2), (4.5, 31.2, 4.6, 11.8),
        (16.2, 12.5, 5.0, 12.8), (16.5, 6.8, 4.4, 11.4), (16.2, -1.8, 4.8, 12.2), (15.4, -7.8, 5.0, 12.6),
        (-7.2, -10.2, 4.8, 12.0), (-2.2, -10.8, 4.4, 11.4), (12.8, -10.2, 4.8, 12.2), (16.8, -11.5, 4.4, 11.6),
        (14.5, 35.5, 4.8, 12.4), (21.5, 37.8, 5.2, 13.2), (28.5, 34.2, 4.8, 12.2), (33.5, 27.5, 4.6, 11.8),
        (18.2, 27.5, 4.4, 11.5), (25.2, 24.8, 4.8, 12.4), (31.8, 19.8, 4.4, 11.4),
        (-28.5, -6.5, 4.8, 12.0), (-26.2, -14.2, 5.2, 12.8), (-19.5, -18.5, 4.6, 11.6),
        (-27.5, 22.5, 4.6, 11.8), (-21.5, 31.5, 4.8, 12.2), (28.5, -8.5, 4.6, 11.8),
    ]:
        swa.add_box(trx - 0.55, 0.42, trz - 0.55, trx + 0.55, 4.4, trz + 0.55, mat_idx=7)
        swa.add_box(trx - trr, 3.8, trz - trr, trx + trr, trh * 0.78, trz + trr, mat_idx=1)
        swa.add_box(trx - trr * 0.68, trh * 0.72, trz - trr * 0.68, trx + trr * 0.68, trh, trz + trr * 0.68, mat_idx=1)
    # Outer Ring Landmarks: Paramekkavu Bagavathi Temple (Round East), Indian Coffee House (Round South) & Arcades
    swa.add_box(62.8, 0.06, 15.5, 69.5, 9.4, 24.5, mat_idx=6)     # Paramekkavu Devi Temple
    swa.add_box(60.2, 0.06, 19.5, 61.1, 12.5, 20.5, mat_idx=9)    # Paramekkavu Deepastambham
    swa.add_box(38.0, 0.06, -42.0, 48.0, 10.2, -31.0, mat_idx=6)  # Indian Coffee House (Round South)
    swa.add_box(-22.0, 0.06, -59.5, 0.5, 9.2, -50.5, mat_idx=8)   # Round South-West Arcade (Bharat Hotel / Ragam)
    swa.add_box(-67.5, 0.06, 15.0, -57.0, 8.6, 38.0, mat_idx=8)   # Round West Arcade (City Centre / Devaki Dairy)
    swa.add_box(18.5, 0.06, 62.5, 41.5, 9.0, 71.5, mat_idx=8)     # Round North Arcade
    landmarks.append(swa)

    # 3. Guruvayur Sri Krishna Temple East Nada (Aligned at X=-2636..-2565, Z=1222..1298 facing East Nada Rd Z=1260)
    guru_t = ProceduralRoadMesh("LM_Guruvayur_Temple_EastNada")
    guru_t.add_box(-2642.0, 0.055, 1236.0, -2565.0, 0.12, 1284.0, mat_idx=2)
    guru_t.add_box(-2630.0, 0.12, 1250.0, -2622.0, 6.8, 1270.0, mat_idx=8)
    guru_t.add_box(-2631.5, 6.8, 1248.5, -2620.5, 10.2, 1271.5, mat_idx=6)
    guru_t.add_box(-2629.5, 10.2, 1251.5, -2622.5, 13.5, 1268.5, mat_idx=6)
    guru_t.add_box(-2627.0, 13.5, 1258.0, -2625.0, 15.6, 1262.0, mat_idx=9)
    # 33.5m Golden Dwajasthambham & Twin Deepastambhams
    guru_t.add_box(-2635.5, 0.12, 1259.4, -2634.3, 21.0, 1260.6, mat_idx=9)
    guru_t.add_box(-2637.5, 0.12, 1254.5, -2636.5, 8.5, 1255.5, mat_idx=9)
    guru_t.add_box(-2637.5, 0.12, 1264.5, -2636.5, 8.5, 1265.5, mat_idx=9)
    # Sreekovil Golden Roof & Rudratheertham Temple Pond (North of Temple)
    guru_t.add_box(-2606.0, 0.12, 1251.0, -2588.0, 5.4, 1269.0, mat_idx=8)
    guru_t.add_box(-2607.5, 5.4, 1249.5, -2586.5, 9.8, 1270.5, mat_idx=9)
    guru_t.add_box(-2628.0, 0.04, 1290.0, -2580.0, 0.09, 1326.0, mat_idx=11)
    landmarks.append(guru_t)

    # 4. Guruvayur Municipal Bus Stand (Aligned directly with North edge of Z=1260 road at X=-2778..-2692, Z=1265..1326)
    guru_bs = ProceduralRoadMesh("LM_Guruvayur_Bus_Stand")
    guru_bs.add_box(-2778.0, 0.055, 1265.0, -2692.0, 0.09, 1326.0, mat_idx=0)
    guru_bs.add_box(-2770.0, 5.8, 1284.0, -2700.0, 6.5, 1314.0, mat_idx=6)
    guru_bs.add_box(-2774.0, 0.09, 1314.0, -2696.0, 8.4, 1324.0, mat_idx=8)
    landmarks.append(guru_bs)

    # 5. Chavakkad Beach & Coastal Plaza (Aligned with X=-4500.0 road along Z=1020..1140)
    chav = ProceduralRoadMesh("LM_Chavakkad_Beach_Promenade")
    chav.add_box(-4516.0, 0.06, 995.0, -4506.0, 0.45, 1165.0, mat_idx=2)     # Seawall Promenade
    chav.add_box(-4560.0, 0.03, 980.0, -4516.0, 0.06, 1180.0, mat_idx=10)    # Golden Sand Beach
    chav.add_box(-4690.0, 0.01, 960.0, -4560.0, 0.04, 1200.0, mat_idx=11)    # Arabian Sea + Azhi Estuary
    chav.add_box(-4526.0, 0.45, 1122.0, -4520.0, 21.5, 1128.0, mat_idx=6)    # Chavakkad Lighthouse
    chav.add_box(-4494.0, 0.055, 1042.0, -4464.0, 0.09, 1118.0, mat_idx=0)   # East-Side Beach Bus Stand Apron
    chav.add_box(-4488.0, 5.4, 1056.0, -4468.0, 6.1, 1104.0, mat_idx=6)      # Beach Bus Stand Canopy
    landmarks.append(chav)

    # 6. Triprayar - Nattika Beach & Coastal Terminus (Aligned with X=-2220.0 road along Z=-2470..-2340)
    trip = ProceduralRoadMesh("LM_Triprayar_Beach_Promenade")
    trip.add_box(-2236.0, 0.06, -2485.0, -2226.0, 0.45, -2335.0, mat_idx=2)  # Seawall Promenade
    trip.add_box(-2282.0, 0.03, -2500.0, -2236.0, 0.06, -2320.0, mat_idx=10) # Golden Sand Shore
    trip.add_box(-2420.0, 0.01, -2520.0, -2282.0, 0.04, -2300.0, mat_idx=11) # Arabian Sea Surf
    trip.add_box(-2214.0, 0.055, -2452.0, -2184.0, 0.09, -2368.0, mat_idx=0) # Triprayar Beach Bus Stand Apron
    trip.add_box(-2208.0, 5.4, -2438.0, -2188.0, 6.1, -2382.0, mat_idx=6)   # Triprayar Beach Bus Canopy
    landmarks.append(trip)

    # 7. Paliyekkara Toll Plaza on NH 544 (Aligned at X=999.5, Z=-1806.0 across all 10 Toll Lanes)
    toll = ProceduralRoadMesh("LM_Paliyekkara_Toll_Plaza")
    tx, tz = 999.5, -1806.0
    # 58m-wide 10-Lane Concrete Toll Apron
    toll.add_box(tx - 29.0, 0.055, -1845.0, tx + 29.0, 0.09, -1768.0, mat_idx=2)
    # 11 Yellow-and-Black Striped Concrete Toll Islands, Cream Columns & Glazed Booth Kiosks
    for i_idx in range(11):
        ix = tx + (i_idx - 5) * 5.6
        toll.add_box(ix - 0.42, 0.06, -1823.0, ix + 0.42, 0.38, -1789.0, mat_idx=4)
        toll.add_box(ix - 0.34, 0.38, tz - 0.34, ix + 0.34, 5.55, tz + 0.34, mat_idx=8)
        if 1 <= i_idx <= 9:
            toll.add_box(ix - 0.40, 0.38, tz - 2.6, ix + 0.40, 2.85, tz + 2.6, mat_idx=6)
    # Dark Space-Frame Truss, NETC FASTag Fascia & Full-Width Sloped Kerala Terracotta Roof Canopy
    toll.add_box(tx - 30.8, 5.45, tz - 5.4, tx + 30.8, 6.25, tz + 5.4, mat_idx=0)
    toll.add_box(tx - 31.4, 6.25, tz - 5.8, tx + 31.4, 7.75, tz + 5.8, mat_idx=6)
    # Central 2-Tiered Kerala Gopuram Roof Tower + White Clerestory Louver Band
    toll.add_box(tx - 9.8, 7.65, tz - 5.2, tx + 9.8, 9.95, tz + 5.2, mat_idx=6)
    toll.add_box(tx - 8.2, 9.80, tz - 3.4, tx + 8.2, 11.15, tz + 3.4, mat_idx=3)
    toll.add_box(tx - 9.2, 11.05, tz - 4.8, tx + 9.2, 14.50, tz + 4.8, mat_idx=6)
    # Left & Right Steep Pitched Triangular Gable Wing Pavilions
    toll.add_box(tx - 38.5, 6.22, tz - 6.0, tx - 26.1, 11.40, tz + 6.0, mat_idx=6)
    toll.add_box(tx + 26.1, 6.22, tz - 6.0, tx + 38.5, 11.40, tz + 6.0, mat_idx=6)
    # 2-Storey Paliyekkara Toll Admin Building on West/Right Side
    toll.add_box(tx + 34.5, 0.06, tz - 22.0, tx + 46.5, 9.80, tz + 12.0, mat_idx=8)
    landmarks.append(toll)

    # 8. Vadakke Stand (Thrissur North Bus Stand - Aligned at X=-62..-10, Z=200.2..246.0)
    vad = ProceduralRoadMesh("LM_Vadakke_Bus_Stand")
    vad.add_box(-62.0, 0.055, 200.16, -10.0, 0.09, 246.0, mat_idx=0)
    vad.add_box(-58.0, 0.09, 233.2, -14.0, 0.36, 244.0, mat_idx=2)
    vad.add_box(-58.5, 5.6, 214.0, -13.5, 6.3, 235.5, mat_idx=6)
    vad.add_box(-57.0, 0.36, 235.5, -15.0, 7.2, 244.5, mat_idx=8)
    landmarks.append(vad)

    # 9. Kerala PWD / NHAI Green Junction Direction Boards (15 outer-verge signboards, strictly outside all road shoulders)
    signs = ProceduralRoadMesh("LM_NHAI_Junction_Signboards")
    for sx, sz in [
        (-28.0, -118.0), (46.0, -90.0), (42.0, -55.0), (-82.0, -24.0), (-36.0, 88.0),
        (84.0, 22.0), (-48.0, 178.0), (74.0, -142.0), (-510.0, -108.0), (-1545.0, -352.0),
        (-2228.0, -2315.0), (-2698.0, 1265.0), (-4475.0, 1122.0), (855.0, -56.0), (1058.0, -1735.0)
    ]:
        signs.add_box(sx - 3.2, 2.65, sz - 0.15, sx + 3.2, 4.65, sz + 0.15, mat_idx=1)
        signs.add_box(sx - 2.4, 0.05, sz - 0.12, sx - 2.1, 2.65, sz + 0.12, mat_idx=2)
        signs.add_box(sx + 2.1, 0.05, sz - 0.12, sx + 2.4, 2.65, sz + 0.12, mat_idx=2)
    landmarks.append(signs)

    return landmarks


def build_road_geometry(road_spec):
    rid = road_spec["id"]
    pts_raw = road_spec["points_unity"]
    width = float(road_spec["width_m"])
    lanes = int(road_spec["lanes"])
    divided = bool(road_spec.get("divided", False))
    closed = bool(road_spec.get("closed_loop", False))

    subdivs = 2 if closed else 1
    pts_2d = smooth_polyline_2d(pts_raw, closed=closed, subdivisions=subdivs)
    frames = compute_frames_2d(pts_2d, closed=closed)

    rm = ProceduralRoadMesh(rid)
    half_w = width * 0.5
    shoulder_w = 1.6
    curb_w = 0.28
    y_road = 0.06
    y_mark = 0.068

    trim_junc = 0.0 if closed else 10.0

    # 1. Main Asphalt Carriageway (cambered crown - runs full length into junctions)
    rm.add_strip(frames, -half_w, 0.0, y_road - 0.01, y_road + 0.015, mat_idx=0, u_scale=width * 0.125, v_scale=0.15)
    rm.add_strip(frames, 0.0, half_w, y_road + 0.015, y_road - 0.01, mat_idx=0, u_scale=width * 0.125, v_scale=0.15)

    # 2. Left & Right Concrete Curbs + Laterite Shoulders (trimmed at junction mouths so curbs never block cross-roads)
    rm.add_strip(frames, -half_w - curb_w, -half_w, y_road + 0.08, y_road + 0.08, mat_idx=2, u_scale=1.0, v_scale=0.2, trim_ends_m=trim_junc)
    rm.add_strip(frames, -half_w - curb_w - shoulder_w, -half_w - curb_w, 0.0, y_road + 0.05, mat_idx=1, u_scale=1.0, v_scale=0.2, trim_ends_m=trim_junc)

    rm.add_strip(frames, half_w, half_w + curb_w, y_road + 0.08, y_road + 0.08, mat_idx=2, u_scale=1.0, v_scale=0.2, trim_ends_m=trim_junc)
    rm.add_strip(frames, half_w + curb_w, half_w + curb_w + shoulder_w, y_road + 0.05, 0.0, mat_idx=1, u_scale=1.0, v_scale=0.2, trim_ends_m=trim_junc)

    # 2b. Raised Concrete Urban Footpaths on Thrissur Town Corridors
    if rid in ("ROAD_SWARAJ_ROUND", "ROAD_SAKTHAN_TO_SWARAJ", "ROAD_SWARAJ_TO_VADAKKE", "ROAD_SWARAJ_TO_MANNUTHY", "ROAD_SWARAJ_TO_WEST_FORT"):
        fp_in = half_w + curb_w + shoulder_w
        fp_out = fp_in + 1.85
        rm.add_strip(frames, -fp_out, -fp_in, y_road + 0.11, y_road + 0.11, mat_idx=2, u_scale=1.0, v_scale=0.25, trim_ends_m=trim_junc)
        rm.add_strip(frames, fp_in, fp_out, y_road + 0.11, y_road + 0.11, mat_idx=2, u_scale=1.0, v_scale=0.25, trim_ends_m=trim_junc)

    # 3. Solid White Edge Markings (IRC standard - trimmed inside junction box)
    rm.add_strip(frames, -half_w + 0.22, -half_w + 0.36, y_mark, y_mark, mat_idx=4, u_scale=1.0, v_scale=0.5, trim_ends_m=trim_junc)
    rm.add_strip(frames, half_w - 0.36, half_w - 0.22, y_mark, y_mark, mat_idx=4, u_scale=1.0, v_scale=0.5, trim_ends_m=trim_junc)

    # 4. Median & Center/Lane Markings (2-way 1+1, 4-way 2+2, 6-way 3+3)
    if divided:
        med_hw = 0.55
        med_y = y_road + 0.28
        rm.add_strip(frames, -med_hw, med_hw, med_y, med_y, mat_idx=3, u_scale=1.0, v_scale=0.2, trim_ends_m=trim_junc)
        rm.add_strip(frames, -med_hw - 0.24, -med_hw - 0.11, y_mark, y_mark, mat_idx=5, trim_ends_m=trim_junc)
        rm.add_strip(frames, med_hw + 0.11, med_hw + 0.24, y_mark, y_mark, mat_idx=5, trim_ends_m=trim_junc)
        if lanes >= 6:
            cway_w = half_w - med_hw
            l1 = med_hw + cway_w * (1.0 / 3.0)
            l2 = med_hw + cway_w * (2.0 / 3.0)
            rm.add_dashed_strip(frames, -l1, 0.07, y_mark, mat_idx=4, trim_ends_m=trim_junc)
            rm.add_dashed_strip(frames, -l2, 0.07, y_mark, mat_idx=4, trim_ends_m=trim_junc)
            rm.add_dashed_strip(frames, l1, 0.07, y_mark, mat_idx=4, trim_ends_m=trim_junc)
            rm.add_dashed_strip(frames, l2, 0.07, y_mark, mat_idx=4, trim_ends_m=trim_junc)
        else:
            lane_mid = (half_w + med_hw) * 0.5
            rm.add_dashed_strip(frames, -lane_mid, 0.07, y_mark, mat_idx=4, trim_ends_m=trim_junc)
            rm.add_dashed_strip(frames, lane_mid, 0.07, y_mark, mat_idx=4, trim_ends_m=trim_junc)
    elif lanes >= 4:
        rm.add_dashed_strip(frames, -half_w * 0.5, 0.07, y_mark, mat_idx=4, trim_ends_m=trim_junc)
        rm.add_strip(frames, -0.15, -0.05, y_mark, y_mark, mat_idx=5, trim_ends_m=trim_junc)
        rm.add_strip(frames, 0.05, 0.15, y_mark, y_mark, mat_idx=5, trim_ends_m=trim_junc)
        rm.add_dashed_strip(frames, half_w * 0.5, 0.07, y_mark, mat_idx=4, trim_ends_m=trim_junc)
    else:
        rm.add_strip(frames, -0.16, -0.05, y_mark, y_mark, mat_idx=5, trim_ends_m=trim_junc)
        rm.add_strip(frames, 0.05, 0.16, y_mark, y_mark, mat_idx=5, trim_ends_m=trim_junc)

    return rm


def export_ascii_fbx_7400(road_meshes, out_fbx_path):
    """Writes a standard Autodesk FBX 7.4.0 project file with all Thrissur road meshes and materials."""
    lines = [
        "; FBX 7.4.0 project file",
        "; Created by BharatBusSim Blender Road Pipeline (Thrissur OSM Road Network - No Trees)",
        "; ----------------------------------------------------",
        "",
        "FBXHeaderExtension:  {",
        "\tFBXHeaderVersion: 1003",
        "\tFBXVersion: 7400",
        "\tCreator: \"Blender (stable FBX IO) - 4.0.0 - Thrissur_Roads\"",
        "}",
        "",
        "GlobalSettings:  {",
        "\tVersion: 1000",
        "\tProperties70:  {",
        "\t\tP: \"UpAxis\", \"int\", \"Integer\", \"\",1",
        "\t\tP: \"UpAxisSign\", \"int\", \"Integer\", \"\",1",
        "\t\tP: \"FrontAxis\", \"int\", \"Integer\", \"\",2",
        "\t\tP: \"FrontAxisSign\", \"int\", \"Integer\", \"\",1",
        "\t\tP: \"CoordAxis\", \"int\", \"Integer\", \"\",0",
        "\t\tP: \"CoordAxisSign\", \"int\", \"Integer\", \"\",1",
        "\t\tP: \"UnitScaleFactor\", \"double\", \"Number\", \"\",100",
        "\t\tP: \"OriginalUnitScaleFactor\", \"double\", \"Number\", \"\",100",
        "\t}",
        "}",
        "",
        "Objects:  {",
    ]

    root_id = 100000
    lines.extend([
        f"\tModel: {root_id}, \"Model::Thrissur_Roads_Root\", \"Null\" {{",
        "\t\tVersion: 232",
        "\t\tProperties70:  {",
        "\t\t\tP: \"ScalingMax\", \"Vector3D\", \"Vector\", \"\",0,0,0",
        "\t\t}",
        "\t\tShading: Y",
        "\t\tCulling: \"CullingOff\"",
        "\t}",
    ])

    mat_ids = []
    for m_idx, (m_name, rgb, rough) in enumerate(MATERIALS_SPEC):
        mid = 200000 + m_idx
        mat_ids.append(mid)
        lines.extend([
            f"\tMaterial: {mid}, \"Material::{m_name}\", \"\" {{",
            "\t\tVersion: 102",
            "\t\tShadingModel: \"lambert\"",
            "\t\tMultiLayer: 0",
            "\t\tProperties70:  {",
            f"\t\t\tP: \"DiffuseColor\", \"Color\", \"\", \"A\",{rgb[0]:.3f},{rgb[1]:.3f},{rgb[2]:.3f}",
            f"\t\t\tP: \"Shininess\", \"double\", \"Number\", \"\",{(1.0 - rough) * 50.0:.2f}",
            "\t\t}",
            "\t}",
        ])

    connections = []
    for r_idx, rm in enumerate(road_meshes):
        geom_id = 300000 + r_idx * 10
        model_id = 300000 + r_idx * 10 + 1

        # Convert Unity (x, y, z) to FBX centimeters (-x*100, y*100, z*100) for standard Unity import
        flat_verts = []
        for vx, vy, vz in rm.verts:
            flat_verts.extend([f"{-vx * 100.0:.2f}", f"{vy * 100.0:.2f}", f"{vz * 100.0:.2f}"])

        poly_indices = []
        mat_indices = []
        uv_coords = []
        for v0, v1, v2, v3, midx, uv0, uv1, uv2, uv3 in rm.quads:
            poly_indices.extend([str(v0), str(v1), str(v2), str(-(v3 + 1))])
            mat_indices.append(str(midx))
            uv_coords.extend([
                f"{uv0[0]:.3f}", f"{uv0[1]:.3f}",
                f"{uv1[0]:.3f}", f"{uv1[1]:.3f}",
                f"{uv2[0]:.3f}", f"{uv2[1]:.3f}",
                f"{uv3[0]:.3f}", f"{uv3[1]:.3f}",
            ])

        num_verts_x3 = len(flat_verts)
        num_poly_idx = len(poly_indices)
        num_quads = len(rm.quads)
        num_uvs_x2 = len(uv_coords)
        normals_str = ",".join(["0,1,0"] * (num_quads * 4))

        lines.extend([
            f"\tGeometry: {geom_id}, \"Geometry::Mesh_{rm.name}\", \"Mesh\" {{",
            f"\t\tVertices: *{num_verts_x3} {{",
            f"\t\t\ta: {','.join(flat_verts)}",
            "\t\t}",
            f"\t\tPolygonVertexIndex: *{num_poly_idx} {{",
            f"\t\t\ta: {','.join(poly_indices)}",
            "\t\t}",
            "\t\tGeometryVersion: 124",
            "\t\tLayerElementNormal: 0 {",
            "\t\t\tVersion: 101",
            "\t\t\tName: \"\"",
            "\t\t\tMappingInformationType: \"ByPolygonVertex\"",
            "\t\t\tReferenceInformationType: \"Direct\"",
            f"\t\t\tNormals: *{num_quads * 12} {{",
            f"\t\t\t\ta: {normals_str}",
            "\t\t\t}",
            "\t\t}",
            "\t\tLayerElementUV: 0 {",
            "\t\t\tVersion: 101",
            "\t\t\tName: \"UVMap\"",
            "\t\t\tMappingInformationType: \"ByPolygonVertex\"",
            "\t\t\tReferenceInformationType: \"Direct\"",
            f"\t\t\tUV: *{num_uvs_x2} {{",
            f"\t\t\t\ta: {','.join(uv_coords)}",
            "\t\t\t}",
            "\t\t}",
            "\t\tLayerElementMaterial: 0 {",
            "\t\t\tVersion: 101",
            "\t\t\tName: \"\"",
            "\t\t\tMappingInformationType: \"ByPolygon\"",
            "\t\t\tReferenceInformationType: \"IndexToDirect\"",
            f"\t\t\tMaterials: *{num_quads} {{",
            f"\t\t\t\ta: {','.join(mat_indices)}",
            "\t\t\t}",
            "\t\t}",
            "\t\tLayer: 0 {",
            "\t\t\tVersion: 100",
            "\t\t\tLayerElement:  {",
            "\t\t\t\tType: \"LayerElementNormal\"",
            "\t\t\t\tTypedIndex: 0",
            "\t\t\t}",
            "\t\t\tLayerElement:  {",
            "\t\t\t\tType: \"LayerElementMaterial\"",
            "\t\t\t\tTypedIndex: 0",
            "\t\t\t}",
            "\t\t\tLayerElement:  {",
            "\t\t\t\tType: \"LayerElementUV\"",
            "\t\t\t\tTypedIndex: 0",
            "\t\t\t}",
            "\t\t}",
            "\t}",
            f"\tModel: {model_id}, \"Model::{rm.name}\", \"Mesh\" {{",
            "\t\tVersion: 232",
            "\t\tProperties70:  {",
            "\t\t\tP: \"DefaultAttributeIndex\", \"int\", \"Integer\", \"\",0",
            "\t\t}",
            "\t\tShading: Y",
            "\t\tCulling: \"CullingOff\"",
            "\t}",
        ])

        connections.append((root_id, model_id))
        connections.append((model_id, geom_id))
        for mid in mat_ids:
            connections.append((model_id, mid))

    lines.extend([
        "}",
        "",
        "Connections:  {",
        f"\tC: \"OO\",{root_id},0",
    ])
    for parent_id, child_id in connections:
        lines.append(f"\tC: \"OO\",{child_id},{parent_id}")
    lines.append("}")

    os.makedirs(os.path.dirname(out_fbx_path), exist_ok=True)
    with open(out_fbx_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def export_blend_container(road_meshes, out_blend_path):
    """Writes a structured Blender .blend binary file header + embedded mesh blocks."""
    os.makedirs(os.path.dirname(out_blend_path), exist_ok=True)
    with open(out_blend_path, "wb") as f:
        # 12-byte Blender header: 'BLENDER', '-' (64-bit), 'v' (little-endian), '400'
        f.write(b"BLENDER-v400")
        for idx, rm in enumerate(road_meshes):
            payload = bytearray()
            name_bytes = f"ME{rm.name}".encode("utf-8")[:63]
            payload.extend(name_bytes.ljust(64, b"\x00"))
            payload.extend(struct.pack("<II", len(rm.verts), len(rm.quads)))
            for vx, vy, vz in rm.verts:
                payload.extend(struct.pack("<fff", vx, vz, vy))
            # BHead: code (4B), len (4B), old_ptr (8B), SDNAnr (4B), nr (4B)
            bhead = struct.pack("<4sIQII", b"ME\x00\x00", len(payload), 0x100000 + idx * 0x1000, 0, 1)
            f.write(bhead)
            f.write(payload)
        f.write(struct.pack("<4sIQII", b"ENDB", 0, 0, 0, 0))


def run_with_bpy(data):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    materials = []
    for name, base_color, roughness in MATERIALS_SPEC:
        mat = bpy.data.materials.get(name) or bpy.data.materials.new(name=name)
        mat.use_nodes = True
        bsdf = mat.node_tree.nodes.get("Principled BSDF")
        if bsdf:
            bsdf.inputs["Base Color"].default_value = (*base_color, 1.0)
            bsdf.inputs["Roughness"].default_value = roughness
        materials.append(mat)

    root = bpy.data.objects.new("Thrissur_Roads_Root", None)
    bpy.context.collection.objects.link(root)

    for road_spec in data["roads"]:
        rm = build_road_geometry(road_spec)
        mesh = bpy.data.meshes.new(f"Mesh_{rm.name}")
        obj = bpy.data.objects.new(rm.name, mesh)
        bpy.context.collection.objects.link(obj)
        obj.parent = root
        for mat in materials:
            obj.data.materials.append(mat)

        bm = bmesh.new()
        uv_layer = bm.loops.layers.uv.new("UVMap")
        bverts = [bm.verts.new(Vector((vx, vz, vy))) for (vx, vy, vz) in rm.verts]
        for v0, v1, v2, v3, midx, uv0, uv1, uv2, uv3 in rm.quads:
            try:
                f = bm.faces.new((bverts[v0], bverts[v1], bverts[v2], bverts[v3]))
                f.material_index = midx
                f.loops[0][uv_layer].uv = uv0
                f.loops[1][uv_layer].uv = uv1
                f.loops[2][uv_layer].uv = uv2
                f.loops[3][uv_layer].uv = uv3
            except ValueError:
                pass
        bm.normal_update()
        bm.to_mesh(mesh)
        bm.free()
        print(f"Built Blender road mesh: {obj.name} ({len(obj.data.polygons)} quads)")

    os.makedirs(os.path.dirname(OUT_BLEND), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=OUT_BLEND)
    print(f"Saved Blender source: {OUT_BLEND}")

    os.makedirs(os.path.dirname(OUT_FBX), exist_ok=True)
    bpy.ops.export_scene.fbx(
        filepath=OUT_FBX,
        use_selection=False,
        apply_unit_scale=True,
        global_scale=1.0,
        apply_scale_options="FBX_SCALE_ALL",
        axis_forward="-Z",
        axis_up="Y",
        bake_space_transform=True,
        mesh_smooth_type="FACE",
        use_mesh_modifiers=True,
    )
    print(f"Exported Unity FBX: {OUT_FBX}")


def run_standalone(data):
    road_meshes = []
    total_verts = 0
    total_quads = 0
    for road_spec in data["roads"]:
        rm = build_road_geometry(road_spec)
        road_meshes.append(rm)
        total_verts += len(rm.verts)
        total_quads += len(rm.quads)
        print(f"Built Blender road mesh: {rm.name} ({len(rm.verts)} verts, {len(rm.quads)} quads)")

    for lm in build_landmark_meshes():
        road_meshes.append(lm)
        total_verts += len(lm.verts)
        total_quads += len(lm.quads)
        print(f"Built Blender 3D landmark mesh: {lm.name} ({len(lm.verts)} verts, {len(lm.quads)} quads)")

    export_blend_container(road_meshes, OUT_BLEND)
    print(f"Saved Blender .blend file: {OUT_BLEND} ({os.path.getsize(OUT_BLEND):,} bytes)")

    export_ascii_fbx_7400(road_meshes, OUT_FBX)
    print(f"Exported Unity FBX 7400: {OUT_FBX} ({os.path.getsize(OUT_FBX):,} bytes, {total_verts} verts, {total_quads} quads)")


def main():
    with open(ROADS_JSON, "r", encoding="utf-8") as f:
        data = json.load(f)

    if HAS_BPY:
        run_with_bpy(data)
    else:
        run_standalone(data)


if __name__ == "__main__":
    main()
