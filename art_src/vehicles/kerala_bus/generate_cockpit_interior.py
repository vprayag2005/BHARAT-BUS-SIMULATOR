"""
===============================================================================
  BHARAT BUS SIMULATOR — REALISTIC COCKPIT INTERIOR GENERATOR
  Blender Python Script (Compatible with Blender 3.x, 4.x, and 5.x)
===============================================================================
  Matches the exact reference cockpit from BUS SIMULATOR INDIA:
    • Sculpted Curved Dashboard with Dark Vinyl & Polished Teak Wood Panel
    • 4-Spoke Modern Steering Wheel with Center Horn Pad & Emblem Badge
    • Dual Analog Instrument Cluster (Speedometer & Tachometer) + Needles
    • Center GPS Route Navigation LCD Screen (Emissive)
    • 12-Slot Rocker Switch Bank (Hazards, Wipers, Lights, Doors)
    • Windshield Framing A-Pillars (Left & Right)
    • Passenger Door Frame with Clear Glass (Angled side view through door)
    • Dual Upper Convex Wing Mirrors (Left & Right with Reflective Glass)
    • Air Conditioning / Defroster Slotted Vents & Chrome Trim Accents
    • Floor-Mounted Gear Shifter with Leather Boot & Ergonomic Knob
    • Driver Eyepoint Camera Target (Turned slightly towards doors)

  Output:
    • cockpit_interior.blend
    • cockpit_interior.fbx
    • Textures/ folder with PBR textures (Wood, Gauges, GPS, Badge, Switches)
===============================================================================
"""

import bpy
import bmesh
import math
import os
import shutil
from mathutils import Vector, Euler

# ─────────────────────────────────────────────────────────────────────────────
# CONFIGURATION & PATHS
# ─────────────────────────────────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__)) if '__file__' in globals() else r"C:\Users\praya\Downloads"
OUTPUT_DIR = SCRIPT_DIR
TEX_DIR = os.path.join(OUTPUT_DIR, "Cockpit_Textures")
FBX_OUTPUT = os.path.join(OUTPUT_DIR, "cockpit_interior.fbx")
BLEND_OUTPUT = os.path.join(OUTPUT_DIR, "cockpit_interior.blend")

# Bus dimensions & Cockpit Reference (matches 12m Coach BUS_ECOLINE space)
BUS_HALF_WIDTH = 1.25     # +/- 1.25m
DASH_Y_FRONT = -5.65      # Forward towards front bumper
DASH_Y_BACK = -5.05       # Backward towards driver seat
DASH_Z_BOTTOM = 0.45      # Lower floor level
DASH_Z_TOP = 1.38         # Upper dash surface
ROOF_Z = 3.15             # Bus roof level
FLOOR_Z = 0.38            # Bus floor level

# Driver seat location (Matches BUS_ECOLINE driver seat X = -0.70m, Left Hand Drive / Center-Left)
DRIVER_X = -0.68
DRIVER_EYE_Y = -4.50
DRIVER_EYE_Z = 1.62

# Steering wheel position
STEER_X = DRIVER_X
STEER_Y = -5.16
STEER_Z = 1.08
STEER_RADIUS = 0.22
STEER_TILT_DEG = 24.0     # Tilted back towards driver

# Instrument cluster location
CLUSTER_X = DRIVER_X
CLUSTER_Y = DASH_Y_FRONT + 0.12
CLUSTER_Z = 1.05
GAUGE_RADIUS = 0.082
GAUGE_SPACING = 0.22

# Passenger door on the right side
DOOR_X = BUS_HALF_WIDTH - 0.02
DOOR_Y_FRONT = -5.35
DOOR_Y_BACK = -4.10
DOOR_Z_TOP = 2.50

ALL_OBJECTS = []

# ─────────────────────────────────────────────────────────────────────────────
# UTILITY FUNCTIONS
# ─────────────────────────────────────────────────────────────────────────────
def clear_scene():
    """Removes all existing meshes, curves, cameras, and lights from the scene."""
    if bpy.ops.object.mode_set.poll():
        bpy.ops.object.mode_set(mode='OBJECT')
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)

    for block in (bpy.data.meshes, bpy.data.materials, bpy.data.textures, bpy.data.images):
        for item in list(block):
            if item.users == 0:
                block.remove(item)

def link_obj(obj):
    if obj.name not in bpy.context.collection.objects:
        bpy.context.collection.objects.link(obj)
    if obj not in ALL_OBJECTS:
        ALL_OBJECTS.append(obj)
    return obj

def deselect_all():
    bpy.ops.object.select_all(action='DESELECT')

def set_active(obj):
    deselect_all()
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)

def apply_transforms(obj, loc=False, rot=False, sc=False):
    set_active(obj)
    bpy.ops.object.transform_apply(location=loc, rotation=rot, scale=sc)
    obj.select_set(False)

def assign_material(obj, mat):
    if obj.data and hasattr(obj.data, 'materials'):
        if len(obj.data.materials) == 0:
            obj.data.materials.append(mat)
        else:
            obj.data.materials[0] = mat

def smart_uv(obj):
    set_active(obj)
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=66.0, island_margin=0.02)
    bpy.ops.object.mode_set(mode='OBJECT')
    obj.select_set(False)

def create_box(name, loc, dim, mat=None):
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=loc)
    obj = bpy.context.active_object
    obj.name = name
    obj.scale = (dim[0] / 2.0, dim[1] / 2.0, dim[2] / 2.0)
    apply_transforms(obj, sc=True)
    if mat:
        assign_material(obj, mat)
    obj.select_set(False)
    ALL_OBJECTS.append(obj)
    return obj

# ─────────────────────────────────────────────────────────────────────────────
# PROCEDURAL TEXTURE GENERATION
# ─────────────────────────────────────────────────────────────────────────────
class SimpleCanvas:
    """Fast, dependency-free RGBA buffer for creating high-res game textures."""
    def __init__(self, size):
        self.size = size
        self.px = [0.0] * (size * size * 4)

    def set_pixel(self, x, y, r, g, b, a=1.0):
        if 0 <= x < self.size and 0 <= y < self.size:
            idx = (y * self.size + x) * 4
            self.px[idx] = r
            self.px[idx + 1] = g
            self.px[idx + 2] = b
            self.px[idx + 3] = a

    def fill(self, r, g, b, a=1.0):
        for i in range(0, len(self.px), 4):
            self.px[i] = r
            self.px[i + 1] = g
            self.px[i + 2] = b
            self.px[i + 3] = a

    def draw_circle(self, cx, cy, radius, r, g, b, thickness=2):
        r_inner = radius - thickness
        r_outer = radius + thickness
        r_outer_sq = r_outer * r_outer
        r_inner_sq = max(0, r_inner * r_inner)
        for y in range(max(0, int(cy - r_outer - 1)), min(self.size, int(cy + r_outer + 2))):
            dy = y - cy
            dy2 = dy * dy
            for x in range(max(0, int(cx - r_outer - 1)), min(self.size, int(cx + r_outer + 2))):
                dx = x - cx
                d2 = dx * dx + dy2
                if r_inner_sq <= d2 <= r_outer_sq:
                    self.set_pixel(x, y, r, g, b)

    def draw_disc(self, cx, cy, radius, r, g, b):
        r2 = radius * radius
        for y in range(max(0, int(cy - radius - 1)), min(self.size, int(cy + radius + 2))):
            dy = y - cy
            dy2 = dy * dy
            for x in range(max(0, int(cx - radius - 1)), min(self.size, int(cx + radius + 2))):
                dx = x - cx
                if dx * dx + dy2 <= r2:
                    self.set_pixel(x, y, r, g, b)

    def draw_line(self, x1, y1, x2, y2, r, g, b, thickness=1):
        dist = math.hypot(x2 - x1, y2 - y1)
        if dist < 0.1:
            return
        dx, dy = (x2 - x1) / dist, (y2 - y1) / dist
        steps = int(dist * 2.5) + 1
        for s in range(steps):
            t = s / 2.5
            px = x1 + dx * t
            py = y1 + dy * t
            for off in range(-thickness, thickness + 1):
                self.set_pixel(int(px - dy * off), int(py + dx * off), r, g, b)

    def draw_tick(self, cx, cy, angle_deg, r_in, r_out, r, g, b, thickness=1):
        rad = math.radians(angle_deg)
        cos_a = math.cos(rad)
        sin_a = math.sin(rad)
        self.draw_line(cx + r_in * cos_a, cy + r_in * sin_a,
                       cx + r_out * cos_a, cy + r_out * sin_a, r, g, b, thickness)

    def save_as_blender_image(self, name, filepath):
        img = bpy.data.images.new(name, self.size, self.size, alpha=True)
        img.pixels = self.px
        img.pack()
        img.filepath_raw = filepath
        img.file_format = 'PNG'
        img.save()
        return img

def create_wood_texture(filepath, size=1024):
    """Generates rich Indian Teak / Walnut wood fascia texture."""
    canvas = SimpleCanvas(size)
    print("    • Generating rich teak wood texture...")
    for y in range(size):
        v = y / size * 8.0
        for x in range(size):
            u = x / size * 8.0
            # Layered wood grain equation
            wave = (math.sin(v * 28.0 + math.sin(u * 3.5 + v * 2.2) * 4.2
                    + math.sin(u * 9.1 + v * 5.4) * 1.6
                    + math.sin(u * 17.0) * 0.7) + 1.0) * 0.5
            fine = math.sin(u * 12.0 + v * 1.5) * 0.05 + math.sin(u * 32.0 - v * 4.0) * 0.02
            grain = max(0.0, min(1.0, wave + fine))
            # Deep warm golden-amber teak tones
            r = 0.22 + grain * 0.38
            g = 0.11 + grain * 0.21
            b = 0.04 + grain * 0.09
            canvas.set_pixel(x, y, r, g, b)
    return canvas.save_as_blender_image("T_Dashboard_Wood", filepath)

def create_gauge_dial(name, filepath, max_val, unit_str, major_step, minor_step, danger_val, size=512):
    """Draws realistic bus speedometer / tachometer dial face."""
    canvas = SimpleCanvas(size)
    cx, cy = size // 2, size // 2
    r_outer = size * 0.44

    # Outer bezel & dark face
    canvas.fill(0.03, 0.03, 0.04)
    canvas.draw_circle(cx, cy, r_outer + 4, 0.65, 0.65, 0.70, thickness=3) # Outer chrome ring
    canvas.draw_circle(cx, cy, r_outer, 0.20, 0.20, 0.22, thickness=2)     # Inner bevel
    canvas.draw_disc(cx, cy, r_outer - 2, 0.04, 0.04, 0.05)                # Black face

    start_angle = 225.0
    end_angle = -45.0
    sweep = start_angle - end_angle

    # Minor ticks
    val = 0
    while val <= max_val:
        if val % major_step != 0:
            ang = start_angle - (val / max_val) * sweep
            canvas.draw_tick(cx, cy, ang, r_outer * 0.82, r_outer * 0.88, 0.62, 0.62, 0.65, thickness=1)
        val += minor_step

    # Major ticks & indicator dots
    val = 0
    while val <= max_val:
        ang = start_angle - (val / max_val) * sweep
        color = (0.92, 0.15, 0.10) if val >= danger_val else (0.95, 0.95, 0.96)
        canvas.draw_tick(cx, cy, ang, r_outer * 0.72, r_outer * 0.90, *color, thickness=2)

        # Number position dots
        dot_r = r_outer * 0.62
        rad = math.radians(ang)
        canvas.draw_disc(cx + dot_r * math.cos(rad), cy + dot_r * math.sin(rad), 3.5, *color)
        val += major_step

    # Green driving range arc & Redline arc
    step_ang = 0.4
    ang = start_angle
    while ang >= end_angle:
        cur_val = (start_angle - ang) / sweep * max_val
        arc_color = (0.90, 0.12, 0.08) if cur_val >= danger_val else (0.10, 0.68, 0.20)
        arc_r = r_outer * 0.92
        rad = math.radians(ang)
        ax = cx + arc_r * math.cos(rad)
        ay = cy + arc_r * math.sin(rad)
        canvas.set_pixel(int(ax), int(ay), *arc_color)
        canvas.set_pixel(int(ax) + 1, int(ay), *arc_color)
        canvas.set_pixel(int(ax), int(ay) + 1, *arc_color)
        canvas.set_pixel(int(ax) + 1, int(ay) + 1, *arc_color)
        ang -= step_ang

    # Center hub
    canvas.draw_disc(cx, cy, size * 0.08, 0.12, 0.12, 0.14)
    canvas.draw_circle(cx, cy, size * 0.08, 0.45, 0.45, 0.48, thickness=2)
    canvas.draw_disc(cx, cy, size * 0.03, 0.25, 0.25, 0.28)

    return canvas.save_as_blender_image(name, filepath)

def create_gps_screen_texture(filepath, size=512):
    """Draws realistic GPS / Navigation screen matching reference image."""
    canvas = SimpleCanvas(size)
    cx, cy = size // 2, size // 2
    canvas.fill(0.04, 0.08, 0.12) # Dark navy road map base

    # Route roads (light gray and dark blue water background)
    for y in range(size):
        for x in range(size):
            if abs(y - 120) < 6 or abs(x - 380) < 8:
                canvas.set_pixel(x, y, 0.18, 0.22, 0.26)

    # Glowing Cyan / Blue Navigation Route curve
    for t_step in range(size):
        t = t_step / size
        # S-curve path
        rx = int(80 + t * 350 + math.sin(t * 3.14) * 40)
        ry = int(60 + t * 400)
        for w in range(-7, 8):
            canvas.set_pixel(rx + w, ry, 0.10, 0.55, 0.95)
            if abs(w) <= 3:
                canvas.set_pixel(rx + w, ry, 0.85, 0.95, 1.0) # White center path

    # Red/Orange Navigation Arrow (current vehicle position)
    ax, ay = int(80 + 0.35 * 350 + math.sin(0.35 * 3.14) * 40), int(60 + 0.35 * 400)
    canvas.draw_disc(ax, ay, 16, 0.95, 0.30, 0.10)
    canvas.draw_circle(ax, ay, 18, 1.0, 1.0, 1.0, thickness=2)

    # Compass ring in corner
    canvas.draw_circle(size - 60, size - 60, 36, 0.40, 0.70, 0.90, thickness=2)
    canvas.draw_line(size - 60, size - 60, size - 60, size - 30, 0.95, 0.15, 0.10, thickness=3) # North

    return canvas.save_as_blender_image("T_Nav_Screen", filepath)

def create_steering_badge_texture(filepath, size=512):
    """Draws 'BUS SIMULATOR INDIA' emblem with wings & Indian flag colors."""
    canvas = SimpleCanvas(size)
    cx, cy = size // 2, size // 2
    canvas.fill(0.06, 0.06, 0.08) # Black badge background

    # Outer Shield Border
    canvas.draw_circle(cx, cy, size * 0.42, 0.75, 0.75, 0.78, thickness=4)
    canvas.draw_circle(cx, cy, size * 0.39, 0.25, 0.25, 0.28, thickness=2)

    # Indian Tricolor Band (Saffron, White, Green)
    band_r = size * 0.36
    for y in range(int(cy - 40), int(cy + 40)):
        for x in range(int(cx - band_r), int(cx + band_r)):
            if math.hypot(x - cx, y - cy) <= band_r:
                if y > cy + 12:
                    canvas.set_pixel(x, y, 1.0, 0.48, 0.12) # Saffron
                elif y < cy - 12:
                    canvas.set_pixel(x, y, 0.08, 0.58, 0.18) # Green
                else:
                    canvas.set_pixel(x, y, 0.95, 0.95, 0.95) # White

    # Ashoka Chakra center circle
    canvas.draw_circle(cx, cy, 14, 0.02, 0.15, 0.55, thickness=2)
    canvas.draw_disc(cx, cy, 4, 0.02, 0.15, 0.55)

    # Wing banners (left & right wings)
    for wing_side in (-1, 1):
        for i in range(5):
            wx1 = cx + wing_side * (band_r + 5 + i * 16)
            wy1 = cy + 20 - i * 6
            wx2 = cx + wing_side * (band_r + 28 + i * 16)
            wy2 = cy + 45 - i * 4
            canvas.draw_line(wx1, wy1, wx2, wy2, 0.78, 0.78, 0.82, thickness=3)

    return canvas.save_as_blender_image("T_Steering_Badge", filepath)

# ─────────────────────────────────────────────────────────────────────────────
# MATERIAL CREATION
# ─────────────────────────────────────────────────────────────────────────────
def create_pbr_material(name, base_rgb, roughness=0.5, metallic=0.0, alpha=1.0, emission=None):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    bsdf = nodes.get("Principled BSDF")

    bsdf.inputs["Base Color"].default_value = (*base_rgb, 1.0)
    bsdf.inputs["Roughness"].default_value = roughness
    bsdf.inputs["Metallic"].default_value = metallic

    if alpha < 1.0:
        if hasattr(mat, "surface_render_method"):
            mat.surface_render_method = 'BLENDED'
        bsdf.inputs["Alpha"].default_value = alpha

    if emission:
        try:
            bsdf.inputs["Emission Color"].default_value = (*emission[:3], 1.0)
            bsdf.inputs["Emission Strength"].default_value = emission[3] if len(emission) > 3 else 2.5
        except KeyError:
            bsdf.inputs["Emission"].default_value = (*emission[:3], 1.0)

    return mat

def create_textured_material(name, image, roughness=0.4, metallic=0.0):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    links = mat.node_tree.links
    bsdf = nodes.get("Principled BSDF")

    tex_node = nodes.new("ShaderNodeTexImage")
    tex_node.image = image
    tex_node.location = (-350, 300)
    links.new(tex_node.outputs["Color"], bsdf.inputs["Base Color"])

    bsdf.inputs["Roughness"].default_value = roughness
    bsdf.inputs["Metallic"].default_value = metallic
    return mat

def setup_all_materials(images):
    M = {}
    M["vinyl_dark"] = create_pbr_material("M_Cockpit_VinylDark", (0.05, 0.05, 0.06), roughness=0.82)
    M["vinyl_grey"] = create_pbr_material("M_Cockpit_VinylGrey", (0.12, 0.12, 0.13), roughness=0.75)
    M["wood_fascia"] = create_textured_material("M_Cockpit_TeakWood", images["wood"], roughness=0.28)
    M["chrome"] = create_pbr_material("M_Cockpit_Chrome", (0.75, 0.76, 0.78), roughness=0.15, metallic=0.95)
    M["steering_grip"] = create_pbr_material("M_Cockpit_SteeringGrip", (0.03, 0.03, 0.03), roughness=0.85)
    M["steering_badge"] = create_textured_material("M_Cockpit_Badge", images["badge"], roughness=0.35)
    M["switch_plastic"] = create_pbr_material("M_Cockpit_Switch", (0.08, 0.08, 0.09), roughness=0.65)
    M["needle_red"] = create_pbr_material("M_Cockpit_NeedleRed", (0.92, 0.12, 0.08), roughness=0.45,
                                          emission=(0.92, 0.12, 0.08, 1.2))
    M["speedo_face"] = create_textured_material("M_Cockpit_SpeedoFace", images["speedo"], roughness=0.55)
    M["tacho_face"] = create_textured_material("M_Cockpit_TachoFace", images["tacho"], roughness=0.55)
    M["gps_screen"] = create_textured_material("M_Cockpit_GpsScreen", images["gps"], roughness=0.20)
    # Add emission to GPS screen
    nodes = M["gps_screen"].node_tree.nodes
    bsdf = nodes.get("Principled BSDF")
    tex_node = [n for n in nodes if n.type == 'TEX_IMAGE'][0]
    try:
        M["gps_screen"].node_tree.links.new(tex_node.outputs["Color"], bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = 1.4
    except KeyError:
        M["gps_screen"].node_tree.links.new(tex_node.outputs["Color"], bsdf.inputs["Emission"])

    M["window_glass"] = create_pbr_material("M_Cockpit_Glass", (0.15, 0.20, 0.22), roughness=0.03, alpha=0.32)
    M["mirror_reflective"] = create_pbr_material("M_Cockpit_MirrorGlass", (0.80, 0.85, 0.90), roughness=0.02, metallic=0.98)
    M["pillar_dark"] = create_pbr_material("M_Cockpit_Pillar", (0.06, 0.06, 0.07), roughness=0.88)
    M["floor_rubber"] = create_pbr_material("M_Cockpit_FloorRubber", (0.04, 0.04, 0.04), roughness=0.92)
    return M

# ─────────────────────────────────────────────────────────────────────────────
# 3D MODEL GEOMETRY GENERATORS
# ─────────────────────────────────────────────────────────────────────────────

def build_dashboard(M):
    """Molds the full curved ergonomic bus dashboard spanning the entire front."""
    print("    • Modeling sculpted bus dashboard...")
    mesh = bpy.data.meshes.new("Dashboard_Main_Mesh")
    bm = bmesh.new()

    # 10 cross-sectional profiles across bus width from -1.22m to +1.22m
    x_slices = [-1.22, -1.05, -0.75, -0.45, -0.15, 0.15, 0.45, 0.75, 1.05, 1.22]
    profiles = []

    for x in x_slices:
        # Side curvature profile (8 vertices per slice)
        # Driver side has raised cluster cowl, passenger side slopes gently
        cowl_lift = 0.09 if (-0.95 <= x <= -0.40) else 0.0
        door_recess = 0.08 if x > 0.85 else 0.0

        v0 = bm.verts.new((x, DASH_Y_BACK + door_recess, DASH_Z_BOTTOM))
        v1 = bm.verts.new((x, DASH_Y_FRONT, DASH_Z_BOTTOM))
        v2 = bm.verts.new((x, DASH_Y_FRONT - 0.02, DASH_Z_TOP - 0.18 + cowl_lift))
        v3 = bm.verts.new((x, DASH_Y_FRONT + 0.12, DASH_Z_TOP + cowl_lift))
        v4 = bm.verts.new((x, DASH_Y_FRONT + 0.28, DASH_Z_TOP + 0.02 + cowl_lift))
        v5 = bm.verts.new((x, DASH_Y_BACK - 0.12 + door_recess, DASH_Z_TOP - 0.05 + cowl_lift * 0.5))
        v6 = bm.verts.new((x, DASH_Y_BACK + door_recess, DASH_Z_TOP - 0.22))
        v7 = bm.verts.new((x, DASH_Y_BACK + door_recess, DASH_Z_BOTTOM + 0.15))
        profiles.append([v0, v1, v2, v3, v4, v5, v6, v7])

    # Connect slices with quad faces
    for i in range(len(profiles) - 1):
        p1 = profiles[i]
        p2 = profiles[i + 1]
        for j in range(len(p1)):
            j_next = (j + 1) % len(p1)
            try:
                bm.faces.new([p1[j], p2[j], p2[j_next], p1[j_next]])
            except ValueError:
                pass

    # End caps
    try: bm.faces.new(profiles[0])
    except ValueError: pass
    try: bm.faces.new(list(reversed(profiles[-1])))
    except ValueError: pass

    bm.normal_update()
    bm.to_mesh(mesh)
    bm.free()

    obj = link_obj(bpy.data.objects.new("Dashboard_Main", mesh))
    assign_material(obj, M["vinyl_dark"])
    smart_uv(obj)
    return obj

def build_wood_fascia_panel(M):
    """Teak wood fascia center panel matching the reference screenshot."""
    print("    • Modeling teak wood fascia panel...")
    # Center wood panel sits on dashboard face
    panel = create_box("Dashboard_TeakWoodPanel",
                       loc=(DRIVER_X + 0.28, DASH_Y_FRONT + 0.04, (DASH_Z_BOTTOM + DASH_Z_TOP) / 2.0 + 0.08),
                       dim=(0.95, 0.03, DASH_Z_TOP - DASH_Z_BOTTOM - 0.28),
                       mat=M["wood_fascia"])
    smart_uv(panel)

    # Polished Chrome Bezel around wood fascia
    bezel = create_box("Dashboard_WoodChromeBezel",
                       loc=(DRIVER_X + 0.28, DASH_Y_FRONT + 0.02, (DASH_Z_BOTTOM + DASH_Z_TOP) / 2.0 + 0.08),
                       dim=(0.97, 0.015, DASH_Z_TOP - DASH_Z_BOTTOM - 0.26),
                       mat=M["chrome"])
    return panel, bezel

def build_instrument_cluster(M):
    """Recessed instrument pod housing the speedometer, tachometer, and animated needles."""
    print("    • Modeling instrument cluster & animated needles...")
    # Cluster pod hood
    hood = create_box("Instrument_Cluster_Hood",
                      loc=(CLUSTER_X, CLUSTER_Y + 0.04, CLUSTER_Z + 0.14),
                      dim=(0.54, 0.16, 0.05),
                      mat=M["vinyl_grey"])

    # Recessed instrument housing backplate
    backplate = create_box("Instrument_Cluster_Backplate",
                           loc=(CLUSTER_X, CLUSTER_Y, CLUSTER_Z),
                           dim=(0.52, 0.08, 0.26),
                           mat=M["vinyl_dark"])

    # Speedometer Dial (Left gauge)
    bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=GAUGE_RADIUS, depth=0.005,
                                        location=(CLUSTER_X - GAUGE_SPACING / 2.0, CLUSTER_Y - 0.042, CLUSTER_Z))
    speedo_face = bpy.context.active_object
    speedo_face.name = "Speedometer_DialFace"
    speedo_face.rotation_euler.x = math.radians(90.0)
    apply_transforms(speedo_face, rot=True)
    assign_material(speedo_face, M["speedo_face"])
    link_obj(speedo_face)

    # Speedometer Needle (Separate pivot object for Unity sync)
    mesh_sn = bpy.data.meshes.new("SpeedoNeedle_Mesh")
    bm_sn = bmesh.new()
    v0 = bm_sn.verts.new((0.0, 0.0, 0.0))
    v1 = bm_sn.verts.new((0.004, -0.065, 0.0))
    v2 = bm_sn.verts.new((-0.004, -0.065, 0.0))
    v3 = bm_sn.verts.new((0.006, 0.015, 0.0))
    v4 = bm_sn.verts.new((-0.006, 0.015, 0.0))
    bm_sn.faces.new([v0, v2, v1])
    bm_sn.faces.new([v0, v3, v4])
    bm_sn.to_mesh(mesh_sn)
    bm_sn.free()

    speedo_needle = link_obj(bpy.data.objects.new("SpeedoNeedle", mesh_sn))
    speedo_needle.location = (CLUSTER_X - GAUGE_SPACING / 2.0, CLUSTER_Y - 0.046, CLUSTER_Z)
    speedo_needle.rotation_euler.x = math.radians(90.0)
    assign_material(speedo_needle, M["needle_red"])

    # Tachometer Dial (Right gauge)
    bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=GAUGE_RADIUS, depth=0.005,
                                        location=(CLUSTER_X + GAUGE_SPACING / 2.0, CLUSTER_Y - 0.042, CLUSTER_Z))
    tacho_face = bpy.context.active_object
    tacho_face.name = "Tachometer_DialFace"
    tacho_face.rotation_euler.x = math.radians(90.0)
    apply_transforms(tacho_face, rot=True)
    assign_material(tacho_face, M["tacho_face"])
    link_obj(tacho_face)

    # Tachometer Needle (Separate pivot object for Unity sync)
    mesh_tn = bpy.data.meshes.new("TachoNeedle_Mesh")
    bm_tn = bmesh.new()
    v0 = bm_tn.verts.new((0.0, 0.0, 0.0))
    v1 = bm_tn.verts.new((0.004, -0.065, 0.0))
    v2 = bm_tn.verts.new((-0.004, -0.065, 0.0))
    v3 = bm_tn.verts.new((0.006, 0.015, 0.0))
    v4 = bm_tn.verts.new((-0.006, 0.015, 0.0))
    bm_tn.faces.new([v0, v2, v1])
    bm_tn.faces.new([v0, v3, v4])
    bm_tn.to_mesh(mesh_tn)
    bm_tn.free()

    tacho_needle = link_obj(bpy.data.objects.new("TachoNeedle", mesh_tn))
    tacho_needle.location = (CLUSTER_X + GAUGE_SPACING / 2.0, CLUSTER_Y - 0.046, CLUSTER_Z)
    tacho_needle.rotation_euler.x = math.radians(90.0)
    assign_material(tacho_needle, M["needle_red"])

    # Chrome trim bezels around dials
    for gx in (CLUSTER_X - GAUGE_SPACING / 2.0, CLUSTER_X + GAUGE_SPACING / 2.0):
        bpy.ops.mesh.primitive_torus_add(major_radius=GAUGE_RADIUS + 0.003, minor_radius=0.004,
                                         major_segments=32, minor_segments=8,
                                         location=(gx, CLUSTER_Y - 0.044, CLUSTER_Z))
        bz = bpy.context.active_object
        bz.name = f"Gauge_Bezel_{gx:.2f}"
        bz.rotation_euler.x = math.radians(90.0)
        apply_transforms(bz, rot=True)
        assign_material(bz, M["chrome"])
        link_obj(bz)

    return speedo_face, speedo_needle, tacho_face, tacho_needle

def build_gps_screen(M):
    """GPS Route Navigation Display."""
    print("    • Modeling center GPS navigation display...")
    # Bezel frame
    frame = create_box("GPS_Screen_Frame",
                       loc=(DRIVER_X + 0.42, DASH_Y_FRONT + 0.03, CLUSTER_Z - 0.02),
                       dim=(0.18, 0.03, 0.14),
                       mat=M["vinyl_grey"])

    # Emissive screen face
    screen = create_box("GPS_Screen_Display",
                        loc=(DRIVER_X + 0.42, DASH_Y_FRONT + 0.014, CLUSTER_Z - 0.02),
                        dim=(0.16, 0.006, 0.12),
                        mat=M["gps_screen"])
    smart_uv(screen)
    return frame, screen

def build_switch_bank(M):
    """12 Rocker switches array for realistic bus controls."""
    print("    • Modeling 12-slot rocker switch bank...")
    base_x = DRIVER_X + 0.68
    base_y = DASH_Y_FRONT + 0.04
    base_z = CLUSTER_Z + 0.04

    console = create_box("Switch_Bank_Console",
                         loc=(base_x, base_y, base_z),
                         dim=(0.26, 0.025, 0.22),
                         mat=M["vinyl_grey"])

    for row in range(4):
        for col in range(3):
            sx = base_x - 0.075 + col * 0.075
            sz = base_z + 0.070 - row * 0.046
            sw = create_box(f"Switch_R{row}_C{col}",
                            loc=(sx, base_y - 0.014, sz),
                            dim=(0.024, 0.012, 0.028),
                            mat=M["switch_plastic"])
            # Slight rocker angle
            sw.rotation_euler.x = math.radians(10.0 if (row + col) % 2 == 0 else -10.0)
    return console

def build_steering_wheel(M):
    """Realistic 4-spoke bus steering wheel with center horn pad & emblem badge."""
    print("    • Modeling 4-spoke bus steering wheel & column...")
    # Steering column shaft
    col_y_mid = (DASH_Y_BACK + STEER_Y) / 2.0
    col_z_mid = STEER_Z - 0.16
    bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=0.042, depth=0.55,
                                        location=(STEER_X, col_y_mid, col_z_mid))
    col = bpy.context.active_object
    col.name = "SteeringColumn"
    col.rotation_euler.x = math.radians(90.0 - STEER_TILT_DEG)
    apply_transforms(col, rot=True)
    assign_material(col, M["vinyl_dark"])
    link_obj(col)

    # Assemble steering wheel at origin, then position & tilt
    wheel_parts = []

    # 1. Outer Grip Rim
    bpy.ops.mesh.primitive_torus_add(major_radius=STEER_RADIUS, minor_radius=0.016,
                                     major_segments=36, minor_segments=10,
                                     location=(0, 0, 0))
    rim = bpy.context.active_object
    rim.name = "_temp_rim"
    assign_material(rim, M["steering_grip"])
    wheel_parts.append(rim)

    # 2. Central Hub / Horn Pad
    bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=0.075, depth=0.035,
                                        location=(0, 0, 0))
    hub = bpy.context.active_object
    hub.name = "_temp_hub"
    assign_material(hub, M["vinyl_dark"])
    wheel_parts.append(hub)

    # 3. Emblem Badge on horn pad
    bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=0.048, depth=0.004,
                                        location=(0, 0, 0.019))
    badge = bpy.context.active_object
    badge.name = "_temp_badge"
    assign_material(badge, M["steering_badge"])
    wheel_parts.append(badge)

    # 4. Chrome Ring around horn pad
    bpy.ops.mesh.primitive_torus_add(major_radius=0.076, minor_radius=0.003,
                                     major_segments=28, minor_segments=6,
                                     location=(0, 0, 0.018))
    ch_ring = bpy.context.active_object
    ch_ring.name = "_temp_ch_ring"
    assign_material(ch_ring, M["chrome"])
    wheel_parts.append(ch_ring)

    # 5. 4 Spokes (Left, Right, Bottom-Left, Bottom-Right)
    spoke_angles = [0.0, 180.0, 235.0, 305.0]
    spoke_len = STEER_RADIUS - 0.075
    for idx, ang_deg in enumerate(spoke_angles):
        rad = math.radians(ang_deg)
        mid_d = 0.075 + spoke_len / 2.0
        bpy.ops.mesh.primitive_cube_add(size=1.0,
                                        location=(mid_d * math.cos(rad), mid_d * math.sin(rad), 0.0))
        spoke = bpy.context.active_object
        spoke.name = f"_temp_spoke_{idx}"
        spoke.scale = (spoke_len / 2.0, 0.014, 0.018)
        spoke.rotation_euler.z = rad
        apply_transforms(spoke, sc=True, rot=True)
        assign_material(spoke, M["vinyl_dark"])
        wheel_parts.append(spoke)

    # Join into single SteeringWheel mesh object
    deselect_all()
    for p in wheel_parts:
        p.select_set(True)
    bpy.context.view_layer.objects.active = wheel_parts[0]
    bpy.ops.object.join()

    wheel = bpy.context.active_object
    wheel.name = "SteeringWheel"
    wheel.location = (STEER_X, STEER_Y, STEER_Z)
    wheel.rotation_euler.x = math.radians(90.0 - STEER_TILT_DEG)
    wheel.select_set(False)
    ALL_OBJECTS.append(wheel)
    return wheel

def build_gear_shifter(M):
    """Floor-mounted transmission gear shifter with leather gaiter."""
    print("    • Modeling gear shifter & leather gaiter...")
    gx, gy = DRIVER_X + 0.40, DASH_Y_BACK + 0.10

    # Leather boot base (truncated cone)
    bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=0.07, radius2=0.02, depth=0.10,
                                    location=(gx, gy, FLOOR_Z + 0.05))
    boot = bpy.context.active_object
    boot.name = "GearShifter_Boot"
    assign_material(boot, M["vinyl_dark"])
    link_obj(boot)

    # Steel shaft
    bpy.ops.mesh.primitive_cylinder_add(vertices=12, radius=0.015, depth=0.45,
                                        location=(gx, gy, FLOOR_Z + 0.28))
    shaft = bpy.context.active_object
    shaft.name = "GearShifter_Shaft"
    assign_material(shaft, M["chrome"])
    link_obj(shaft)

    # Ergonomic Shift Knob
    bpy.ops.mesh.primitive_uv_sphere_add(segments=14, ring_count=10, radius=0.032,
                                         location=(gx, gy, FLOOR_Z + 0.50))
    knob = bpy.context.active_object
    knob.name = "GearShifter_Knob"
    assign_material(knob, M["steering_grip"])
    link_obj(knob)

def build_a_pillars(M):
    """Windshield frame A-pillars on left and right sides framing driver view."""
    print("    • Modeling windshield framing A-pillars...")
    pil_height = ROOF_Z - DASH_Z_TOP - 0.05
    z_mid = DASH_Z_TOP + pil_height / 2.0
    y_pos = DASH_Y_FRONT + 0.25

    # Left A-Pillar (Driver side)
    left_pil = create_box("A_Pillar_Left",
                          loc=(-BUS_HALF_WIDTH + 0.05, y_pos, z_mid),
                          dim=(0.12, 0.10, pil_height),
                          mat=M["pillar_dark"])
    left_pil.rotation_euler.x = math.radians(-10.0)

    # Right A-Pillar (Door side)
    right_pil = create_box("A_Pillar_Right",
                           loc=(BUS_HALF_WIDTH - 0.05, y_pos, z_mid),
                           dim=(0.12, 0.10, pil_height),
                           mat=M["pillar_dark"])
    right_pil.rotation_euler.x = math.radians(-10.0)
    return left_pil, right_pil

def build_side_mirrors(M):
    """Convex aerodynamic side mirrors in upper corners matching reference."""
    print("    • Modeling left & right upper side wing mirrors...")
    mirror_y = DASH_Y_FRONT + 0.65
    mirror_z = 2.22

    for side, sign in (("Left", -1.0), ("Right", 1.0)):
        x_base = sign * BUS_HALF_WIDTH
        x_tip = sign * (BUS_HALF_WIDTH + 0.26)

        # Mirror curved stalk bracket
        bracket = create_box(f"Mirror_Stalk_{side}",
                             loc=((x_base + x_tip) / 2.0, mirror_y, mirror_z + 0.08),
                             dim=(abs(x_tip - x_base), 0.032, 0.032),
                             mat=M["chrome"])

        # Aerodynamic housing (high-visibility green/black casing)
        housing = create_box(f"Mirror_Housing_{side}",
                             loc=(x_tip, mirror_y, mirror_z),
                             dim=(0.08, 0.24, 0.38),
                             mat=M["vinyl_dark"])

        # Reflective glass face (angled inward towards driver eye)
        face_x = x_tip - sign * 0.042
        glass = create_box(f"Mirror_GlassFace_{side}",
                           loc=(face_x, mirror_y, mirror_z),
                           dim=(0.008, 0.21, 0.34),
                           mat=M["mirror_reflective"])
        glass.rotation_euler.z = math.radians(sign * 18.0)

def build_passenger_door_area(M):
    """Passenger door frame with clear glass so camera sees outside through the door."""
    print("    • Modeling passenger door frame & transparent glass window...")
    y_mid = (DOOR_Y_FRONT + DOOR_Y_BACK) / 2.0
    door_height = DOOR_Z_TOP - FLOOR_Z

    # Forward Door Pillar
    create_box("Door_Pillar_Front",
               loc=(DOOR_X, DOOR_Y_FRONT, (FLOOR_Z + DOOR_Z_TOP) / 2.0),
               dim=(0.06, 0.06, door_height),
               mat=M["pillar_dark"])

    # Rear Door Pillar
    create_box("Door_Pillar_Rear",
               loc=(DOOR_X, DOOR_Y_BACK, (FLOOR_Z + DOOR_Z_TOP) / 2.0),
               dim=(0.06, 0.06, door_height),
               mat=M["pillar_dark"])

    # Top Door Header Beam
    create_box("Door_Header_Beam",
               loc=(DOOR_X, y_mid, DOOR_Z_TOP),
               dim=(0.06, DOOR_Y_BACK - DOOR_Y_FRONT, 0.06),
               mat=M["pillar_dark"])

    # Bottom Door Sill
    create_box("Door_Bottom_Sill",
               loc=(DOOR_X, y_mid, FLOOR_Z + 0.03),
               dim=(0.06, DOOR_Y_BACK - DOOR_Y_FRONT, 0.06),
               mat=M["pillar_dark"])

    # Large Clear Door Glass Window
    glass = create_box("Door_Glass_Window",
                       loc=(DOOR_X + 0.01, y_mid, (FLOOR_Z + 0.28 + DOOR_Z_TOP) / 2.0),
                       dim=(0.010, (DOOR_Y_BACK - DOOR_Y_FRONT) - 0.14, door_height - 0.32),
                       mat=M["window_glass"])
    smart_uv(glass)

    # Door entry grab rail (yellow/chrome safety bar)
    grab_rail = create_box("Door_GrabRail",
                           loc=(DOOR_X - 0.06, DOOR_Y_FRONT + 0.06, (FLOOR_Z + DOOR_Z_TOP) / 2.0),
                           dim=(0.03, 0.03, door_height * 0.85),
                           mat=M["chrome"])
    return glass

def build_overhead_and_floor(M):
    """Overhead sun visor, windshield tint strip, and ribbed rubber floor mat."""
    print("    • Modeling overhead sun visor & ribbed floor mat...")
    # Sun Visor (driver side)
    visor = create_box("Sun_Visor",
                       loc=(DRIVER_X, DASH_Y_FRONT + 0.15, ROOF_Z - 0.22),
                       dim=(0.58, 0.22, 0.014),
                       mat=M["vinyl_grey"])
    visor.rotation_euler.x = math.radians(-14.0)

    # Upper Windshield Tint Strip
    tint = create_box("Windshield_TopTint",
                      loc=(0.0, DASH_Y_FRONT - 0.01, ROOF_Z - 0.26),
                      dim=(BUS_HALF_WIDTH * 2.0 - 0.15, 0.008, 0.32),
                      mat=M["window_glass"])

    # Defroster / AC Vents along dash cowl
    for i in range(6):
        vx = -0.65 + i * 0.26
        create_box(f"Defrost_Vent_{i}",
                   loc=(vx, DASH_Y_FRONT + 0.24, DASH_Z_TOP + 0.02),
                   dim=(0.14, 0.045, 0.012),
                   mat=M["switch_plastic"])

    # Floor Mat under pedals & driver area
    floor = create_box("Cockpit_FloorMat",
                       loc=(0.0, (DASH_Y_FRONT + DASH_Y_BACK) / 2.0 + 0.25, FLOOR_Z + 0.005),
                       dim=(BUS_HALF_WIDTH * 1.9, 1.10, 0.01),
                       mat=M["floor_rubber"])

def setup_camera_and_hierarchy():
    """Sets up the Cockpit Camera Marker turned towards the door side."""
    print("    • Creating CockpitCameraEye marker (turned slightly towards doors)...")
    # Empty marker for driver eye view
    cam_marker = bpy.data.objects.new("CockpitCameraEye", None)
    bpy.context.collection.objects.link(cam_marker)
    cam_marker.empty_display_type = 'CONE'
    cam_marker.empty_display_size = 0.25
    cam_marker.location = (DRIVER_X, DRIVER_EYE_Y, DRIVER_EYE_Z)
    # Turned slightly towards the right passenger door (+7 deg yaw) and down (-8.5 deg pitch)
    cam_marker.rotation_euler = Euler((math.radians(-8.5), 0.0, math.radians(7.0)), 'XYZ')
    ALL_OBJECTS.append(cam_marker)

    # Root empty for clean Unity import
    root = bpy.data.objects.new("Cockpit_Interior_Root", None)
    bpy.context.collection.objects.link(root)
    root.empty_display_type = 'PLAIN_AXES'
    root.empty_display_size = 0.50

    for obj in ALL_OBJECTS:
        if obj != root and obj.parent is None:
            obj.parent = root

    return root, cam_marker

# ─────────────────────────────────────────────────────────────────────────────
# EXPORT ROUTINES
# ─────────────────────────────────────────────────────────────────────────────
def export_assets():
    print(f"\n[5/5] Exporting assets...")

    # Save Blender file
    try:
        bpy.ops.wm.save_as_mainfile(filepath=BLEND_OUTPUT)
        print(f"  ✓ Saved .blend: {BLEND_OUTPUT}")
    except Exception as e:
        print(f"  Warning saving .blend: {e}")

    # Export FBX
    deselect_all()
    bpy.ops.object.select_all(action='SELECT')
    try:
        bpy.ops.export_scene.fbx(
            filepath=FBX_OUTPUT,
            use_selection=True,
            global_scale=1.0,
            apply_unit_scale=True,
            apply_scale_options='FBX_SCALE_ALL',
            object_types={'MESH', 'EMPTY'},
            use_mesh_modifiers=True,
            mesh_smooth_type='FACE',
            path_mode='COPY',
            embed_textures=True,
            axis_forward='-Z',
            axis_up='Y'
        )
        print(f"  ✓ Exported FBX: {FBX_OUTPUT}")
    except Exception as e:
        print(f"  Warning exporting FBX: {e}")

# ─────────────────────────────────────────────────────────────────────────────
# MAIN EXECUTION ENTRY POINT
# ─────────────────────────────────────────────────────────────────────────────
def main():
    print("=" * 76)
    print("  BHARAT BUS SIMULATOR — COCKPIT INTERIOR GENERATOR")
    print("=" * 76)

    os.makedirs(TEX_DIR, exist_ok=True)

    print("\n[1/5] Clearing Blender scene...")
    clear_scene()

    print("\n[2/5] Generating procedural textures in Cockpit_Textures/...")
    images = {
        "wood": create_wood_texture(os.path.join(TEX_DIR, "T_Dashboard_Wood.png")),
        "speedo": create_gauge_dial("T_Gauge_Speedo", os.path.join(TEX_DIR, "T_Gauge_Speedo.png"),
                                    max_val=140, unit_str="km/h", major_step=20, minor_step=10, danger_val=100),
        "tacho": create_gauge_dial("T_Gauge_Tacho", os.path.join(TEX_DIR, "T_Gauge_Tacho.png"),
                                   max_val=3000, unit_str="RPM", major_step=500, minor_step=250, danger_val=2400),
        "gps": create_gps_screen_texture(os.path.join(TEX_DIR, "T_Nav_Screen.png")),
        "badge": create_steering_badge_texture(os.path.join(TEX_DIR, "T_Steering_Badge.png")),
    }

    print("\n[3/5] Setting up PBR & textured materials...")
    materials = setup_all_materials(images)

    print("\n[4/5] Building 3D Cockpit geometry...")
    build_dashboard(materials)
    build_wood_fascia_panel(materials)
    build_instrument_cluster(materials)
    build_gps_screen(materials)
    build_switch_bank(materials)
    build_steering_wheel(materials)
    build_gear_shifter(materials)
    build_a_pillars(materials)
    build_side_mirrors(materials)
    build_passenger_door_area(materials)
    build_overhead_and_floor(materials)
    setup_camera_and_hierarchy()

    export_assets()

    total_verts = sum(len(o.data.vertices) for o in ALL_OBJECTS if o.type == 'MESH' and o.data)
    total_meshes = sum(1 for o in ALL_OBJECTS if o.type == 'MESH')

    print("\n" + "=" * 76)
    print("  COCKPIT GENERATION COMPLETE!")
    print(f"  • Meshes Generated : {total_meshes}")
    print(f"  • Total Vertices   : {total_verts:,}")
    print(f"  • Output Directory : {OUTPUT_DIR}")
    print("=" * 76 + "\n")

if __name__ == "__main__":
    main()
