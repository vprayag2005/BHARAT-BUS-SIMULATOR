import json
import math
import os
import sys

import bpy
import bmesh
from mathutils import Vector

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
ROADS_JSON = os.path.join(PROJECT_ROOT, "data", "processed", "thrissur_osm_roads.json")
OUT_BLEND = os.path.join(PROJECT_ROOT, "art_src", "roads", "thrissur_roads", "Thrissur_Roads.blend")
OUT_FBX = os.path.join(PROJECT_ROOT, "unity", "Assets", "_Project", "Models", "ThrissurRoads", "Thrissur_Roads.fbx")


def clear_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def get_or_create_material(name, base_color, roughness=0.8):
    mat = bpy.data.materials.get(name)
    if mat is None:
        mat = bpy.data.materials.new(name=name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    if bsdf:
        bsdf.inputs["Base Color"].default_value = (*base_color, 1.0)
        bsdf.inputs["Roughness"].default_value = roughness
    return mat


def smooth_polyline(pts_xz, closed=False, subdivisions=2):
    """Subdivide polyline with Chaikin corner-cutting for smooth bus driving."""
    pts = [Vector((p[0], p[2])) for p in pts_xz]
    if len(pts) < 3:
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
            # Closed loop: avoid duplicate last point if same as first
            if (pts[0] - pts[-1]).length < 0.01:
                pts = pts[:-1]
            n = len(pts)
            for i in range(n):
                p0, p1 = pts[i], pts[(i + 1) % n]
                new_pts.append(p0.lerp(p1, 0.25))
                new_pts.append(p0.lerp(p1, 0.75))
            new_pts.append(new_pts[0])
        pts = new_pts
    return pts


def compute_frames(pts_2d, closed=False):
    """Return list of (pos_2d, tangent_2d, right_2d, cumulative_dist) in Unity XZ."""
    n = len(pts_2d)
    frames = []
    dist = 0.0
    for i in range(n):
        if i > 0:
            dist += (pts_2d[i] - pts_2d[i - 1]).length
        if closed and i == n - 1 and n > 2:
            t = (pts_2d[1] - pts_2d[i - 1])
        elif closed and i == 0 and n > 2:
            t = (pts_2d[1] - pts_2d[-2])
        elif i == 0:
            t = pts_2d[1] - pts_2d[0]
        elif i == n - 1:
            t = pts_2d[i] - pts_2d[i - 1]
        else:
            t = pts_2d[i + 1] - pts_2d[i - 1]
        if t.length < 1e-5:
            t = Vector((0.0, 1.0))
        else:
            t.normalize()
        # In Unity XZ (x right, z forward): right = (t.y, -t.x)
        r = Vector((t.y, -t.x))
        frames.append((pts_2d[i], t, r, dist))
    return frames


def to_blender(ux, uy, uz):
    """Map Unity (x, y_up, z_fwd) -> Blender (x, y_fwd, z_up) so FBX export (-Z fwd, Y up) or (Y fwd, Z up) aligns."""
    # Note: In Blender, X = ux, Y = uz, Z = uy.
    # When exported with axis_forward='Y', axis_up='Z' (or apply_transform), Unity receives (ux, uy, uz).
    return Vector((ux, uz, uy))


def add_strip(bm, uv_layer, frames, offset_left, offset_right, y_left, y_right, mat_idx, u_scale=1.0, v_scale=0.15):
    """Lofts a continuous quad strip along frames."""
    prev_vl = None
    prev_vr = None
    for idx, (pos, _t, r, dist) in enumerate(frames):
        pl = pos + r * offset_left
        pr = pos + r * offset_right
        vl = bm.verts.new(to_blender(pl.x, y_left, pl.y))
        vr = bm.verts.new(to_blender(pr.x, y_right, pr.y))
        if prev_vl is not None:
            try:
                f = bm.faces.new((prev_vl, vl, vr, prev_vr))
                f.material_index = mat_idx
                f.smooth = False
                v0 = prev_dist * v_scale
                v1 = dist * v_scale
                f.loops[0][uv_layer].uv = (0.0, v0)
                f.loops[1][uv_layer].uv = (0.0, v1)
                f.loops[2][uv_layer].uv = (u_scale, v1)
                f.loops[3][uv_layer].uv = (u_scale, v0)
            except ValueError:
                pass
        prev_vl = vl
        prev_vr = vr
        prev_dist = dist


def add_dashed_strip(bm, uv_layer, frames, center_offset, half_width, y_height, mat_idx, dash_len=3.0, gap_len=4.0):
    """Adds dashed road markings along frames."""
    period = dash_len + gap_len
    for i in range(len(frames) - 1):
        p0, _t0, r0, d0 = frames[i]
        p1, _t1, r1, d1 = frames[i + 1]
        if (d0 % period) > dash_len:
            continue
        seg_len = d1 - d0
        if seg_len < 0.05:
            continue
        c0l = p0 + r0 * (center_offset - half_width)
        c0r = p0 + r0 * (center_offset + half_width)
        c1l = p1 + r1 * (center_offset - half_width)
        c1r = p1 + r1 * (center_offset + half_width)
        v0l = bm.verts.new(to_blender(c0l.x, y_height, c0l.y))
        v0r = bm.verts.new(to_blender(c0r.x, y_height, c0r.y))
        v1l = bm.verts.new(to_blender(c1l.x, y_height, c1l.y))
        v1r = bm.verts.new(to_blender(c1r.x, y_height, c1r.y))
        try:
            f = bm.faces.new((v0l, v1l, v1r, v0r))
            f.material_index = mat_idx
            f.loops[0][uv_layer].uv = (0.0, 0.0)
            f.loops[1][uv_layer].uv = (0.0, 1.0)
            f.loops[2][uv_layer].uv = (1.0, 1.0)
            f.loops[3][uv_layer].uv = (1.0, 0.0)
        except ValueError:
            pass


def build_road_object(road_spec, materials):
    rid = road_spec["id"]
    pts_raw = road_spec["points_unity"]
    width = float(road_spec["width_m"])
    lanes = int(road_spec["lanes"])
    divided = bool(road_spec.get("divided", False))
    closed = bool(road_spec.get("closed_loop", False))

    pts_2d = smooth_polyline(pts_raw, closed=closed, subdivisions=2)
    frames = compute_frames(pts_2d, closed=closed)

    mesh = bpy.data.meshes.new(f"Mesh_{rid}")
    obj = bpy.data.objects.new(rid, mesh)
    bpy.context.collection.objects.link(obj)

    for mat in materials:
        obj.data.materials.append(mat)

    # Material indices:
    # 0: MAT_Road_Asphalt
    # 1: MAT_Road_Shoulder
    # 2: MAT_Road_Curb
    # 3: MAT_Road_Median
    # 4: MAT_Road_WhiteLine
    # 5: MAT_Road_YellowLine

    bm = bmesh.new()
    uv_layer = bm.loops.layers.uv.new("UVMap")

    half_w = width * 0.5
    shoulder_w = 1.8
    curb_w = 0.28
    y_road = 0.05
    y_mark = 0.058

    # 1. Main Asphalt Carriageway
    add_strip(bm, uv_layer, frames, -half_w, half_w, y_road, y_road, mat_idx=0, u_scale=width * 0.25, v_scale=0.15)

    # 2. Left & Right Curbs + Shoulders
    # Left curb
    add_strip(bm, uv_layer, frames, -half_w - curb_w, -half_w, y_road + 0.08, y_road + 0.08, mat_idx=2, u_scale=1.0, v_scale=0.2)
    add_strip(bm, uv_layer, frames, -half_w, -half_w, y_road + 0.08, y_road, mat_idx=2, u_scale=0.2, v_scale=0.2)
    # Left shoulder
    add_strip(bm, uv_layer, frames, -half_w - curb_w - shoulder_w, -half_w - curb_w, 0.0, y_road + 0.06, mat_idx=1, u_scale=1.0, v_scale=0.2)

    # Right curb
    add_strip(bm, uv_layer, frames, half_w, half_w + curb_w, y_road + 0.08, y_road + 0.08, mat_idx=2, u_scale=1.0, v_scale=0.2)
    add_strip(bm, uv_layer, frames, half_w, half_w, y_road, y_road + 0.08, mat_idx=2, u_scale=0.2, v_scale=0.2)
    # Right shoulder
    add_strip(bm, uv_layer, frames, half_w + curb_w, half_w + curb_w + shoulder_w, y_road + 0.06, 0.0, mat_idx=1, u_scale=1.0, v_scale=0.2)

    # 3. Edge Markings (solid white)
    add_strip(bm, uv_layer, frames, -half_w + 0.20, -half_w + 0.35, y_mark, y_mark, mat_idx=4, u_scale=1.0, v_scale=0.5)
    add_strip(bm, uv_layer, frames, half_w - 0.35, half_w - 0.20, y_mark, y_mark, mat_idx=4, u_scale=1.0, v_scale=0.5)

    # 4. Median & Center/Lane Markings
    if divided:
        # Central raised concrete median (1.2m wide, 0.32m high)
        med_hw = 0.60
        med_y = y_road + 0.30
        add_strip(bm, uv_layer, frames, -med_hw, med_hw, med_y, med_y, mat_idx=3, u_scale=1.0, v_scale=0.2)
        add_strip(bm, uv_layer, frames, -med_hw, -med_hw, y_road, med_y, mat_idx=3, u_scale=0.3, v_scale=0.2)
        add_strip(bm, uv_layer, frames, med_hw, med_hw, med_y, y_road, mat_idx=3, u_scale=0.3, v_scale=0.2)
        # Yellow lines beside median
        add_strip(bm, uv_layer, frames, -med_hw - 0.25, -med_hw - 0.12, y_mark, y_mark, mat_idx=5)
        add_strip(bm, uv_layer, frames, med_hw + 0.12, med_hw + 0.25, y_mark, y_mark, mat_idx=5)
        # Dashed white lane dividers on each carriageway
        lane_mid = (half_w + med_hw) * 0.5
        add_dashed_strip(bm, uv_layer, frames, -lane_mid, 0.07, y_mark, mat_idx=4)
        add_dashed_strip(bm, uv_layer, frames, lane_mid, 0.07, y_mark, mat_idx=4)
    elif lanes >= 4:
        # Undivided 4-lane (e.g. Swaraj Round)
        add_dashed_strip(bm, uv_layer, frames, -half_w * 0.5, 0.07, y_mark, mat_idx=4)
        add_dashed_strip(bm, uv_layer, frames, 0.0, 0.07, y_mark, mat_idx=4)
        add_dashed_strip(bm, uv_layer, frames, half_w * 0.5, 0.07, y_mark, mat_idx=4)
    else:
        # 2-lane arterial: double yellow center line
        add_strip(bm, uv_layer, frames, -0.18, -0.06, y_mark, y_mark, mat_idx=5)
        add_strip(bm, uv_layer, frames, 0.06, 0.18, y_mark, y_mark, mat_idx=5)

    bm.normal_update()
    bm.to_mesh(mesh)
    bm.free()
    return obj


def main():
    clear_scene()
    with open(ROADS_JSON, "r", encoding="utf-8") as f:
        data = json.load(f)

    materials = [
        get_or_create_material("MAT_Road_Asphalt", (0.13, 0.13, 0.14), roughness=0.85),
        get_or_create_material("MAT_Road_Shoulder", (0.45, 0.30, 0.20), roughness=0.95),
        get_or_create_material("MAT_Road_Curb", (0.68, 0.68, 0.65), roughness=0.80),
        get_or_create_material("MAT_Road_Median", (0.72, 0.72, 0.70), roughness=0.75),
        get_or_create_material("MAT_Road_WhiteLine", (0.95, 0.95, 0.95), roughness=0.45),
        get_or_create_material("MAT_Road_YellowLine", (0.95, 0.75, 0.05), roughness=0.45),
    ]

    root = bpy.data.objects.new("Thrissur_Roads_Root", None)
    bpy.context.collection.objects.link(root)

    for road_spec in data["roads"]:
        obj = build_road_object(road_spec, materials)
        obj.parent = root
        print(f"Built Blender road mesh: {obj.name} ({len(obj.data.polygons)} tris/quads)")

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


if __name__ == "__main__":
    main()
