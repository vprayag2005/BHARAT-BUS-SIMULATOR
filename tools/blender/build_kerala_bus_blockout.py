import bpy
import bmesh
import math
from mathutils import Vector, Euler, Matrix
import os

def setup_scene():
    """Reset scene, configure metric units, and set up high-contrast Workbench rendering."""
    bpy.ops.wm.read_factory_settings(use_empty=True)
    
    scene = bpy.context.scene
    scene.unit_settings.system = 'METRIC'
    scene.unit_settings.scale_length = 1.0
    scene.unit_settings.length_unit = 'METERS'
    
    # Configure workbench render settings for crisp, professional automotive visualization
    scene.render.engine = 'BLENDER_WORKBENCH'
    scene.display.shading.light = 'STUDIO'
    scene.display.shading.color_type = 'MATERIAL'
    scene.display.shading.show_cavity = True
    scene.display.shading.cavity_type = 'BOTH'
    scene.display.shading.cavity_ridge_factor = 1.8
    scene.display.shading.cavity_valley_factor = 1.4
    scene.display.shading.show_object_outline = True
    scene.display.shading.object_outline_color = (0.05, 0.05, 0.05)
    
    scene.render.resolution_x = 1920
    scene.render.resolution_y = 1080
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'

def get_or_create_material(name, color, roughness=0.3, metallic=0.0):
    """Create or retrieve a material with viewport and Principled BSDF support."""
    mat = bpy.data.materials.get(name)
    if mat is None:
        mat = bpy.data.materials.new(name=name)
        mat.diffuse_color = (color[0], color[1], color[2], color[3] if len(color) > 3 else 1.0)
        mat.roughness = roughness
        mat.metallic = metallic
        if hasattr(mat, "use_nodes"):
            try:
                mat.use_nodes = True
                nodes = mat.node_tree.nodes
                bsdf = nodes.get("Principled BSDF")
                if bsdf:
                    bsdf.inputs['Base Color'].default_value = (color[0], color[1], color[2], color[3] if len(color) > 3 else 1.0)
                    if 'Roughness' in bsdf.inputs:
                        bsdf.inputs['Roughness'].default_value = roughness
                    if 'Metallic' in bsdf.inputs:
                        bsdf.inputs['Metallic'].default_value = metallic
            except Exception:
                pass
    return mat

def create_bus_materials():
    """Defines the reference-matching Indian luxury tourist coach palette."""
    return {
        'paint_white':     get_or_create_material("M_Bus_Paint_White",     (0.96, 0.96, 0.97, 1.0), roughness=0.18, metallic=0.08), # Pearl White Base
        'paint_turquoise': get_or_create_material("M_Bus_Paint_Turquoise", (0.00, 0.76, 0.85, 1.0), roughness=0.20, metallic=0.20), # Vivid Cyan / Turquoise Wave
        'paint_purple':    get_or_create_material("M_Bus_Paint_Purple",    (0.42, 0.12, 0.70, 1.0), roughness=0.20, metallic=0.25), # Royal Purple Ribbon
        'paint_gold':      get_or_create_material("M_Bus_Paint_Gold",      (0.88, 0.70, 0.18, 1.0), roughness=0.25, metallic=0.65), # Metallic Gold Accent Ribbon
        'glass_tinted':    get_or_create_material("M_Bus_Glass_Tinted",    (0.10, 0.13, 0.18, 1.0), roughness=0.05, metallic=0.25), # Panoramic Smoked Glass
        'curtains_gold':   get_or_create_material("M_Bus_Curtains_Gold",   (0.85, 0.67, 0.22, 1.0), roughness=0.85, metallic=0.05), # Pleated Gold Silk Curtains
        'interior_seats':  get_or_create_material("M_Bus_Interior_Seats",  (0.22, 0.16, 0.15, 1.0), roughness=0.75, metallic=0.00), # Luxury High-Back Passenger Seats
        'trim_black':      get_or_create_material("M_Bus_Trim_Black",      (0.08, 0.08, 0.09, 1.0), roughness=0.65, metallic=0.05), # Window Pillars, Rubber Seals, Grilles
        'chrome':          get_or_create_material("M_Bus_Chrome",          (0.90, 0.91, 0.93, 1.0), roughness=0.10, metallic=0.95), # Polished Chrome Grille, Emblems, Rims
        'wheel_dish':      get_or_create_material("M_Bus_Wheel_Dish",      (0.68, 0.70, 0.72, 1.0), roughness=0.28, metallic=0.75), # Commercial Wheel Rim Steel
        'lights_headlamp': get_or_create_material("M_Bus_Lights_Headlamp", (0.98, 0.99, 1.00, 1.0), roughness=0.05, metallic=0.90), # Crystal Projector LED Headlamps
        'lights_amber':    get_or_create_material("M_Bus_Lights_Amber",    (0.98, 0.58, 0.02, 1.0), roughness=0.15, metallic=0.40), # Amber Turn Signals & Side Markers
        'lights_tail':     get_or_create_material("M_Bus_Lights_Tail",     (0.90, 0.04, 0.04, 1.0), roughness=0.15, metallic=0.40), # Red LED Taillights & 3rd Brake Light
        'route_display':   get_or_create_material("M_Bus_Route_Display",   (0.08, 0.92, 0.96, 1.0), roughness=0.10, metallic=0.10), # Illuminated Destination Board ("KERALA")
        'plate_yellow':    get_or_create_material("M_Bus_Plate_Yellow",    (0.95, 0.82, 0.04, 1.0), roughness=0.40, metallic=0.00), # Kerala Commercial Registration Plate
        'tyre':            get_or_create_material("M_Bus_Tyre",            (0.13, 0.13, 0.14, 1.0), roughness=0.90, metallic=0.00), # Commercial Tyre Rubber
        'decal_text':      get_or_create_material("M_Bus_Decal_Text",      (0.15, 0.18, 0.28, 1.0), roughness=0.35, metallic=0.10)  # "TOURIST" Typography
    }

def add_box(bm, min_c, max_c, mat_idx=0):
    """Adds an axis-aligned 3D box to the bmesh with outward normals."""
    x0, y0, z0 = min_c
    x1, y1, z1 = max_c
    v = [
        bm.verts.new((x0, y0, z0)), # 0
        bm.verts.new((x1, y0, z0)), # 1
        bm.verts.new((x1, y1, z0)), # 2
        bm.verts.new((x0, y1, z0)), # 3
        bm.verts.new((x0, y0, z1)), # 4
        bm.verts.new((x1, y0, z1)), # 5
        bm.verts.new((x1, y1, z1)), # 6
        bm.verts.new((x0, y1, z1))  # 7
    ]
    faces = [
        (v[0], v[3], v[2], v[1]), # Bottom
        (v[4], v[5], v[6], v[7]), # Top
        (v[0], v[1], v[5], v[4]), # Front (-Y)
        (v[2], v[3], v[7], v[6]), # Back (+Y)
        (v[0], v[4], v[7], v[3]), # Left (-X)
        (v[1], v[2], v[6], v[5])  # Right (+X)
    ]
    for face in faces:
        f = bm.faces.new(face)
        f.material_index = mat_idx
    return v

def build_commercial_wheel(name, is_front=True):
    """
    Builds a detailed commercial bus wheel (295/80 R22.5 tyre + 10-stud commercial rim).
    Tyre radius = 0.52m (diameter 1.04m), width = 0.26m.
    Local origin centered at (0, 0, 0).
    Local X is axle direction (+X = outer face, -X = inner chassis face).
    """
    mesh = bpy.data.meshes.new(name)
    bm = bmesh.new()
    
    mats = create_bus_materials()
    mat_list = [
        mats['tyre'],        # 0: Tread & sidewall
        mats['chrome'],      # 1: Polished rim lip & nuts
        mats['wheel_dish'],  # 2: Wheel dish / hub center
        mats['trim_black']   # 3: Backplate
    ]
    for m in mat_list:
        mesh.materials.append(m)
    
    num_seg = 32
    tyre_r = 0.52
    shoulder_r = 0.49
    bead_r = 0.32
    dish_r = 0.26
    hub_r = 0.12
    
    rings = [
        (0.12 if is_front else 0.02, 0.0, 2),             # Central Hub Tip
        (0.12 if is_front else 0.02, hub_r * 0.7, 2),      # Central Hub Body
        (0.09 if is_front else -0.02, hub_r, 2),           # Hub Base Ring
        (0.05 if is_front else -0.04, dish_r, 2),          # Recessed Rim Dish
        (0.08, dish_r + 0.03, 1),                          # Bolt Circle Ridge
        (0.09, bead_r, 1),                                 # Outer Rim Lip
        (0.11, shoulder_r, 0),                             # Outer Tyre Sidewall / Shoulder
        (0.13, tyre_r - 0.015, 0),                         # Outer Tread Shoulder
        (0.05, tyre_r, 0),                                 # Outer Tread Crown
        (-0.05, tyre_r, 0),                                # Inner Tread Crown
        (-0.13, tyre_r - 0.015, 0),                        # Inner Tread Shoulder
        (-0.11, shoulder_r, 0),                            # Inner Tyre Sidewall
        (-0.09, bead_r, 1),                                # Inner Rim Lip
        (-0.06, dish_r, 2),                                # Inner Rim Dish
        (-0.06, 0.0, 3)                                    # Inner Backplate Closure
    ]
    
    ring_verts = []
    for x, r, _ in rings:
        if r == 0.0:
            v = bm.verts.new((x, 0.0, 0.0))
            ring_verts.append([v])
        else:
            verts = []
            for i in range(num_seg):
                th = 2.0 * math.pi * i / num_seg
                y = r * math.cos(th)
                z = r * math.sin(th)
                verts.append(bm.verts.new((x, y, z)))
            ring_verts.append(verts)
            
    for idx in range(len(rings) - 1):
        x1, r1, m1 = rings[idx]
        x2, r2, m2 = rings[idx + 1]
        v1_list = ring_verts[idx]
        v2_list = ring_verts[idx + 1]
        mat_i = m1 if m1 == m2 else (0 if (m1 == 0 or m2 == 0) else 1)
        
        if r1 == 0.0:
            cv = v1_list[0]
            for i in range(num_seg):
                inxt = (i + 1) % num_seg
                f = bm.faces.new((cv, v2_list[i], v2_list[inxt]))
                f.material_index = mat_i
        elif r2 == 0.0:
            cv = v2_list[0]
            for i in range(num_seg):
                inxt = (i + 1) % num_seg
                f = bm.faces.new((v1_list[i], cv, v1_list[inxt]))
                f.material_index = mat_i
        else:
            for i in range(num_seg):
                inxt = (i + 1) % num_seg
                f = bm.faces.new((v1_list[i], v1_list[inxt], v2_list[inxt], v2_list[i]))
                f.material_index = mat_i
                
    # 10-stud commercial lug nuts on bolt circle
    bolt_circle_r = 0.19
    bolt_x = 0.085 if is_front else -0.01
    for i in range(10):
        angle = 2.0 * math.pi * i / 10
        bx = bolt_x
        by = bolt_circle_r * math.cos(angle)
        bz = bolt_circle_r * math.sin(angle)
        
        nut_verts = []
        for j in range(6):
            nth = 2.0 * math.pi * j / 6
            ny = by + 0.016 * math.cos(nth)
            nz = bz + 0.016 * math.sin(nth)
            nut_verts.append(bm.verts.new((bx + 0.022, ny, nz)))
        nut_center = bm.verts.new((bx + 0.026, by, bz))
        for j in range(6):
            jnxt = (j + 1) % 6
            f = bm.faces.new((nut_center, nut_verts[j], nut_verts[jnxt]))
            f.material_index = 1
            
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    
    for poly in mesh.polygons:
        poly.use_smooth = True
        
    mod = obj.modifiers.new(name="EdgeSplit", type='EDGE_SPLIT')
    mod.split_angle = math.radians(35)
    
    return obj

def build_12m_tourist_coach():
    """
    Constructs the 12.0m three-axle Indian luxury tourist coach exterior model.
    POLISHED version with genuine flowing vector wave livery, sculpted front fascia,
    zero-z-fighting rear engine hatch, refined panoramic windows, and aerodynamic mirrors.
    """
    mesh = bpy.data.meshes.new("Bus_Body")
    bm = bmesh.new()
    
    mats = create_bus_materials()
    mat_keys = [
        'paint_white',     # 0: Pure Brilliant White Base
        'paint_turquoise', # 1: Vivid Cyan / Turquoise Dynamic Wave
        'paint_purple',    # 2: Royal Purple Ribbon
        'paint_gold',      # 3: Metallic Gold Accent Ribbon
        'glass_tinted',    # 4: Smoked Panoramic Glass
        'curtains_gold',   # 5: Gold Pleated Curtains
        'interior_seats',  # 6: Luxury Seats
        'trim_black',      # 7: Satin Black Trim / Pillars / Grilles
        'chrome',          # 8: Polished Chrome
        'wheel_dish',      # 9: Wheel Dish Steel
        'lights_headlamp', # 10: LED Headlamp
        'lights_amber',    # 11: Amber Indicator
        'lights_tail',     # 12: Red LED Taillight
        'route_display',   # 13: Cyan Route Matrix
        'plate_yellow',    # 14: Kerala Yellow Plate
        'decal_text'       # 15: "TOURIST" Typography
    ]
    for k in mat_keys:
        mesh.materials.append(mats[k])
        
    def add_quad(v1, v2, v3, v4, mat_idx=0):
        verts = [v1, v2, v3, v4]
        if len(set(verts)) < 4:
            return None
        try:
            f = bm.faces.new((v1, v2, v3, v4))
            f.material_index = mat_idx
            return f
        except ValueError:
            return None

    def add_tri(v1, v2, v3, mat_idx=0):
        verts = [v1, v2, v3]
        if len(set(verts)) < 3:
            return None
        try:
            f = bm.faces.new((v1, v2, v3))
            f.material_index = mat_idx
            return f
        except ValueError:
            return None

    # =====================================================================
    # STATION COORDINATES ALONG VEHICLE LENGTH (Y: -6.0m to +6.0m)
    # =====================================================================
    y_stations = [
        -5.90,  # 0:  Rear body wall / D-pillar
        -5.00,  # 1:  Rear engine flank
        -4.25,  # 2:  Rear tag wheel arch start
        -3.60,  # 3:  Rear tag axle center (Axle 3)
        -2.95,  # 4:  Tandem bogie junction
        -2.30,  # 5:  Mid drive axle center (Axle 2)
        -1.65,  # 6:  Mid drive wheel arch end
        -0.80,  # 7:  Passenger bay 1
         0.05,  # 8:  Passenger bay 2 (Midpoint)
         0.90,  # 9:  Passenger bay 3
         1.75,  # 10: Passenger bay 4
         2.60,  # 11: Passenger bay 5
         2.85,  # 12: Front wheel arch start
         3.50,  # 13: Front steer axle center (Axle 1)
         4.15,  # 14: Front wheel arch end
         4.75,  # 15: Front passenger entrance door header
         5.35   # 16: Windshield top header & A-pillar base
    ]
    
    # Elevation levels
    z_skirt    = 0.35   # Lower body skirt & ground clearance
    z_floor    = 1.40   # High-decker passenger floor
    z_waist    = 1.85   # Window sill line
    z_cantrail = 3.35   # Window cantrail / roof edge
    z_crown    = 3.80   # Arched coach roof crown apex
    
    # Half-widths with subtle tumblehome
    w_skirt    = 1.24   # Skirt half-width
    w_floor    = 1.23   # Floor half-width
    w_waist    = 1.25   # Body waistline half-width (widest point)
    w_cantrail = 1.18   # Aerodynamic inward tumblehome
    
    # Arched Roof Profile (Smooth 6-point automotive coach curvature)
    roof_profile_r = [
        (1.18, 3.35),  # 0: Cantrail
        (1.15, 3.50),  # 1: Cantrail Bevel
        (1.04, 3.63),  # 2: Roof Shoulder
        (0.78, 3.73),  # 3: Mid-roof Arc
        (0.42, 3.78),  # 4: Crown Flank
        (0.00, 3.80)   # 5: Roof Crown Apex
    ]
    
    st = {}
    
    for y in y_stations:
        v = {}
        
        # Arch heights
        sk_z_l = z_skirt
        sk_z_r = z_skirt
        
        # Front Wheel Arch (Axle 1: Y = +3.50m, R = 0.65m)
        if 2.85 <= y <= 4.15:
            dy = abs(y - 3.50)
            if dy < 0.65:
                arch_h = math.sqrt(0.65**2 - dy**2) + 0.52
                sk_z_l = max(z_skirt, arch_h)
                sk_z_r = max(z_skirt, arch_h)
                
        # Mid Drive Wheel Arch (Axle 2: Y = -2.30m, R = 0.65m)
        elif -2.95 <= y <= -1.65:
            dy = abs(y - (-2.30))
            if dy < 0.65:
                arch_h = math.sqrt(0.65**2 - dy**2) + 0.52
                sk_z_l = max(z_skirt, arch_h)
                sk_z_r = max(z_skirt, arch_h)
                
        # Rear Tag Wheel Arch (Axle 3: Y = -3.60m, R = 0.65m)
        elif -4.25 <= y < -2.95:
            dy = abs(y - (-3.60))
            if dy < 0.65:
                arch_h = math.sqrt(0.65**2 - dy**2) + 0.52
                sk_z_l = max(z_skirt, arch_h)
                sk_z_r = max(z_skirt, arch_h)
                
        v['L_skirt'] = bm.verts.new((-w_skirt, y, sk_z_l))
        v['L_floor'] = bm.verts.new((-w_floor, y, z_floor))
        v['L_waist'] = bm.verts.new((-w_waist, y, z_waist))
        
        v['R_skirt'] = bm.verts.new((w_skirt, y, sk_z_r))
        v['R_floor'] = bm.verts.new((w_floor, y, z_floor))
        v['R_waist'] = bm.verts.new((w_waist, y, z_waist))
        
        v['roof_r'] = [bm.verts.new((rx, y, rz)) for rx, rz in roof_profile_r]
        v['roof_l'] = [bm.verts.new((-rx, y, rz)) for rx, rz in roof_profile_r]
        
        v['Bot_L'] = bm.verts.new((-w_skirt + 0.15, y, z_skirt))
        v['Bot_R'] = bm.verts.new((w_skirt - 0.15, y, z_skirt))
        
        st[y] = v
        
    # Connect body shell along vehicle length
    for idx in range(len(y_stations) - 1):
        y0 = y_stations[idx]
        y1 = y_stations[idx + 1]
        s0 = st[y0]
        s1 = st[y1]
        
        is_front_door = (4.74 <= y0 and y1 <= 5.36)
        
        # 1. Smooth Arched Roof Panels (Pure Pearl White)
        for ri in range(len(roof_profile_r) - 1):
            add_quad(s0['roof_l'][ri], s0['roof_l'][ri+1], s1['roof_l'][ri+1], s1['roof_l'][ri], mat_idx=0)
            add_quad(s0['roof_r'][ri+1], s0['roof_r'][ri], s1['roof_r'][ri], s1['roof_r'][ri+1], mat_idx=0)
            
        # Cantrail Gold Accent Ribbon
        add_quad(s0['roof_l'][0], s0['roof_l'][1], s1['roof_l'][1], s1['roof_l'][0], mat_idx=3)
        add_quad(s0['roof_r'][1], s0['roof_r'][0], s1['roof_r'][0], s1['roof_r'][1], mat_idx=3)
        
        # 2. Continuous Panoramic Smoked Glass Window Band
        cant_l0 = s0['roof_l'][0]
        cant_l1 = s1['roof_l'][0]
        cant_r0 = s0['roof_r'][0]
        cant_r1 = s1['roof_r'][0]
        
        if not is_front_door:
            add_quad(s0['L_waist'], cant_l0, cant_l1, s1['L_waist'], mat_idx=4)
        add_quad(cant_r0, s0['R_waist'], s1['R_waist'], cant_r1, mat_idx=4)
        
        # 3. Structural Black Window Pillars (Slim 30mm)
        p_w = 0.03
        if not is_front_door and idx not in (0, len(y_stations) - 2):
            v_p_b0 = bm.verts.new((-w_waist - 0.005, y0 - p_w, z_waist))
            v_p_b1 = bm.verts.new((-w_waist - 0.005, y0 + p_w, z_waist))
            v_p_t0 = bm.verts.new((-w_cantrail - 0.005, y0 - p_w, z_cantrail))
            v_p_t1 = bm.verts.new((-w_cantrail - 0.005, y0 + p_w, z_cantrail))
            add_quad(v_p_b0, v_p_t0, v_p_t1, v_p_b1, mat_idx=7)
            
        if idx not in (0, len(y_stations) - 2):
            v_pr_b0 = bm.verts.new((w_waist + 0.005, y0 - p_w, z_waist))
            v_pr_b1 = bm.verts.new((w_waist + 0.005, y0 + p_w, z_waist))
            v_pr_t0 = bm.verts.new((w_cantrail + 0.005, y0 - p_w, z_cantrail))
            v_pr_t1 = bm.verts.new((w_cantrail + 0.005, y0 + p_w, z_cantrail))
            add_quad(v_pr_b1, v_pr_t1, v_pr_t0, v_pr_b0, mat_idx=7)
            
        # 4. Interior Detailing: Gold Silk Curtains & High-Back Passenger Seats
        if not is_front_door and idx not in (0, len(y_stations) - 2):
            add_box(bm, (-w_waist + 0.03, y0 - 0.09, z_waist + 0.16), (-w_waist + 0.055, y0 + 0.09, z_cantrail - 0.08), mat_idx=5)
            seat_y = (y0 + y1) * 0.5
            add_box(bm, (-w_waist + 0.08, seat_y - 0.25, z_floor + 0.05), (-w_waist + 0.52, seat_y + 0.25, z_waist + 0.48), mat_idx=6)
            
        if idx not in (0, len(y_stations) - 2):
            add_box(bm, (w_waist - 0.055, y0 - 0.09, z_waist + 0.16), (w_waist - 0.03, y0 + 0.09, z_cantrail - 0.08), mat_idx=5)
            seat_y = (y0 + y1) * 0.5
            add_box(bm, (w_waist - 0.52, seat_y - 0.25, z_floor + 0.05), (w_waist - 0.08, seat_y + 0.25, z_waist + 0.48), mat_idx=6)
            
        # 5. Base White Panels (Floor to Waist) - Pure White Base Canvas for Decals
        if not is_front_door:
            add_quad(s0['L_floor'], s0['L_waist'], s1['L_waist'], s1['L_floor'], mat_idx=0)
        add_quad(s0['R_waist'], s0['R_floor'], s1['R_floor'], s1['R_waist'], mat_idx=0)
        
        # Waistline Gold Accent Pinstripe
        if not is_front_door:
            v_gp_l0 = bm.verts.new((-w_waist - 0.002, y0, z_waist - 0.04))
            v_gp_l1 = bm.verts.new((-w_waist - 0.002, y1, z_waist - 0.04))
            add_quad(v_gp_l0, s0['L_waist'], s1['L_waist'], v_gp_l1, mat_idx=3)
        v_gp_r0 = bm.verts.new((w_waist + 0.002, y0, z_waist - 0.04))
        v_gp_r1 = bm.verts.new((w_waist + 0.002, y1, z_waist - 0.04))
        add_quad(s0['R_waist'], v_gp_r0, v_gp_r1, s1['R_waist'], mat_idx=3)
        
        # 6. Lower Luggage Bay Skirts (Pure White Base)
        if not is_front_door:
            add_quad(s0['L_skirt'], s0['L_floor'], s1['L_floor'], s1['L_skirt'], mat_idx=0)
        add_quad(s0['R_floor'], s0['R_skirt'], s1['R_skirt'], s0['R_floor'], mat_idx=0)
        
        # Luggage locker chrome door handles
        if not is_front_door and (-1.5 <= y0 <= 2.5):
            mid_y_h = (y0 + y1) * 0.5
            add_box(bm, (-w_waist - 0.004, mid_y_h - 0.06, 0.85), (-w_waist - 0.001, mid_y_h + 0.06, 0.90), mat_idx=8)
            add_box(bm, ( w_waist + 0.001, mid_y_h - 0.06, 0.85), ( w_waist + 0.004, mid_y_h + 0.06, 0.90), mat_idx=8)
            
        # 7. Bottom Chassis & Underbelly Skirt (Satin Black)
        add_quad(s0['Bot_L'], s1['Bot_L'], s1['Bot_R'], s0['Bot_R'], mat_idx=7)
        add_quad(s0['L_skirt'], s1['L_skirt'], s1['Bot_L'], s0['Bot_L'], mat_idx=7)
        add_quad(s0['Bot_R'], s1['Bot_R'], s1['R_skirt'], s0['R_skirt'], mat_idx=7)
        
        # 8. Front Passenger Plug Entrance Door (Left side at Y = 4.75 to 5.35)
        if is_front_door:
            v_d_ceil0 = bm.verts.new((-w_waist + 0.40, y0, z_cantrail))
            v_d_ceil1 = bm.verts.new((-w_waist + 0.40, y1, z_cantrail))
            v_d_fl0   = bm.verts.new((-w_waist + 0.40, y0, z_floor))
            v_d_fl1   = bm.verts.new((-w_waist + 0.40, y1, z_floor))
            
            add_quad(cant_l0, v_d_ceil0, v_d_ceil1, cant_l1, mat_idx=7)
            add_quad(v_d_ceil0, v_d_fl0, v_d_fl1, v_d_ceil1, mat_idx=7)
            add_quad(s0['L_waist'], cant_l0, v_d_ceil0, v_d_fl0, mat_idx=7)
            add_quad(v_d_ceil1, cant_l1, s1['L_waist'], v_d_fl1, mat_idx=7)
            
            add_box(bm, (-w_waist - 0.005, y0 + 0.05, z_waist + 0.10), (-w_waist + 0.005, y1 - 0.05, z_cantrail - 0.05), mat_idx=4)
            add_box(bm, (-w_waist - 0.005, y0 + 0.05, 0.45), (-w_waist + 0.005, y1 - 0.05, z_waist + 0.05), mat_idx=0)
            mid_y_d = (y0 + y1) * 0.5
            add_box(bm, (-w_waist + 0.15, mid_y_d - 0.015, 0.45), (-w_waist + 0.18, mid_y_d + 0.015, z_cantrail - 0.10), mat_idx=8)

    # =========================================================================
    # GENUINE FLOWING 3D LIVERY GRAPHICS (MATCHING REFERENCE BLUEPRINT)
    # =========================================================================
    # We construct smooth polygonal ribbon strips attached directly to the bus body
    # with outward normals and tapered tips for a true luxury coach paint job.
    def add_curved_ribbon(points_top, points_bot, mat_idx, side='both', offset=0.003):
        """Builds a continuous flowing curved graphic ribbon on left and/or right sides."""
        sides = []
        if side in ('both', 'left'):
            sides.append((-1, -w_waist - offset))
        if side in ('both', 'right'):
            sides.append((1, w_waist + offset))
            
        for x_sign, x_coord in sides:
            n_pts = len(points_top)
            v_t = [bm.verts.new((x_coord, pt[0], pt[1])) for pt in points_top]
            v_b = [bm.verts.new((x_coord, pt[0], pt[1])) for pt in points_bot]
            
            for i in range(n_pts - 1):
                if x_sign < 0: # Left side (-X): outward normal is -X
                    add_quad(v_b[i], v_t[i], v_t[i+1], v_b[i+1], mat_idx=mat_idx)
                else:          # Right side (+X): outward normal is +X
                    add_quad(v_t[i], v_b[i], v_b[i+1], v_t[i+1], mat_idx=mat_idx)

    # 1. PRIMARY SWEEPING CYAN / TURQUOISE WAVE (Front-to-Rear Luxury Swoosh)
    # Rises from behind front wheel, arches over mid body, sweeps up over tandem bogie
    cyan_wave_top = [
        (4.40, 0.55), (3.80, 0.95), (3.20, 1.25), (2.40, 1.55),
        (1.50, 1.72), (0.40, 1.75), (-0.80, 1.70), (-1.80, 1.60),
        (-2.80, 1.45), (-3.80, 1.30), (-4.60, 1.15), (-5.40, 0.95)
    ]
    cyan_wave_bot = [
        (4.40, 0.55), (3.80, 0.65), (3.00, 0.85), (2.20, 1.10),
        (1.40, 1.25), (0.30, 1.30), (-0.80, 1.20), (-1.80, 1.05),
        (-2.80, 0.85), (-3.80, 0.65), (-4.60, 0.50), (-5.40, 0.95)
    ]
    add_curved_ribbon(cyan_wave_top, cyan_wave_bot, mat_idx=1, side='both', offset=0.003)

    # 2. UPPER TURQUOISE FORWARD WING (Sweeping forward above wheel arch)
    cyan_wing_top = [
        (3.60, 1.25), (2.80, 1.65), (2.00, 1.82), (1.10, 1.84), (0.20, 1.76)
    ]
    cyan_wing_bot = [
        (3.60, 1.25), (2.80, 1.45), (2.00, 1.62), (1.10, 1.68), (0.20, 1.76)
    ]
    add_curved_ribbon(cyan_wing_top, cyan_wing_bot, mat_idx=1, side='both', offset=0.0035)

    # 3. METALLIC GOLD LEADING PINSTRIPES (Bordering the Cyan Wave)
    gold_top_edge = [
        (4.40, 0.56), (3.80, 0.98), (3.20, 1.28), (2.40, 1.58),
        (1.50, 1.75), (0.40, 1.78), (-0.80, 1.73), (-1.80, 1.63),
        (-2.80, 1.48), (-3.80, 1.33), (-4.60, 1.18), (-5.40, 0.98)
    ]
    add_curved_ribbon(gold_top_edge, cyan_wave_top, mat_idx=3, side='both', offset=0.004)

    gold_bot_edge = [
        (4.40, 0.54), (3.80, 0.63), (3.00, 0.83), (2.20, 1.07),
        (1.40, 1.22), (0.30, 1.27), (-0.80, 1.17), (-1.80, 1.02),
        (-2.80, 0.82), (-3.80, 0.62), (-4.60, 0.47), (-5.40, 0.93)
    ]
    add_curved_ribbon(cyan_wave_bot, gold_bot_edge, mat_idx=3, side='both', offset=0.004)

    # 4. ROYAL PURPLE DYNAMIC CHEVRONS & RIBBONS (Layered beneath & trailing)
    # Primary Purple Ribbon (Sweeps through the midriff and tapers into sharp chevrons)
    purple_ribbon_top = [
        (1.80, 1.15), (0.80, 1.28), (-0.20, 1.25), (-1.20, 1.12),
        (-2.20, 0.95), (-3.20, 0.78), (-4.20, 0.62), (-5.20, 0.48)
    ]
    purple_ribbon_bot = [
        (1.80, 1.15), (0.90, 0.85), (0.00, 0.80), (-1.00, 0.75),
        (-2.00, 0.65), (-3.00, 0.52), (-4.00, 0.42), (-5.20, 0.48)
    ]
    add_curved_ribbon(purple_ribbon_top, purple_ribbon_bot, mat_idx=2, side='both', offset=0.0045)

    # Upper Purple Accent Chevrons (Shooting towards the rear roof pillar)
    purple_wing_top = [
        (-0.50, 1.68), (-1.50, 1.82), (-2.50, 2.05), (-3.50, 2.38),
        (-4.50, 2.75), (-5.20, 3.10), (-5.60, 3.32)
    ]
    purple_wing_bot = [
        (-0.50, 1.68), (-1.40, 1.65), (-2.40, 1.80), (-3.30, 2.05),
        (-4.30, 2.35), (-5.00, 2.65), (-5.60, 3.32)
    ]
    add_curved_ribbon(purple_wing_top, purple_wing_bot, mat_idx=2, side='both', offset=0.0045)

    # Gold Pinstripe along the Upper Purple Wing
    gold_wing_top = [
        (-0.50, 1.70), (-1.50, 1.85), (-2.50, 2.08), (-3.50, 2.41),
        (-4.50, 2.78), (-5.20, 3.13), (-5.60, 3.34)
    ]
    add_curved_ribbon(gold_wing_top, purple_wing_top, mat_idx=3, side='both', offset=0.005)

    # 5. METALLIC GOLD DYNAMIC CHEVRON SWOOSH (Between rear axles)
    gold_swoosh_top = [
        (-1.20, 1.10), (-2.00, 1.30), (-2.80, 1.40), (-3.50, 1.35), (-4.40, 1.10)
    ]
    gold_swoosh_bot = [
        (-1.20, 1.10), (-2.00, 1.18), (-2.80, 1.25), (-3.50, 1.18), (-4.40, 1.10)
    ]
    add_curved_ribbon(gold_swoosh_top, gold_swoosh_bot, mat_idx=3, side='both', offset=0.0055)

    # 6. "TOURIST" TYPOGRAPHY DECAL STRIP (Reference Blueprint Feature)
    # Positioned along mid waistline (Y = +0.60m to +2.40m, Z = 1.86m to 1.96m)
    for s_x in [-w_waist - 0.004, w_waist + 0.004]:
        v_t0 = bm.verts.new((s_x, 0.60, 1.87))
        v_t1 = bm.verts.new((s_x, 2.40, 1.87))
        v_t2 = bm.verts.new((s_x, 2.40, 1.95))
        v_t3 = bm.verts.new((s_x, 0.60, 1.95))
        if s_x < 0:
            add_quad(v_t0, v_t3, v_t2, v_t1, mat_idx=15)
        else:
            add_quad(v_t0, v_t1, v_t2, v_t3, mat_idx=15)

    # =========================================================================
    # SCULPTED FRONT FASCIA (AERODYNAMIC CURVATURE & INTEGRATED LIGHTING)
    # =========================================================================
    s_cowl = st[5.35]
    
    # 1. Aerodynamic Curved Panoramic Windshield
    v_wb_l  = bm.verts.new((-w_waist, 5.75, z_waist))
    v_wb_ml = bm.verts.new((-0.60,    5.86, z_waist))
    v_wb_c  = bm.verts.new(( 0.0,     5.92, z_waist))
    v_wb_mr = bm.verts.new(( 0.60,    5.86, z_waist))
    v_wb_r  = bm.verts.new(( w_waist, 5.75, z_waist))
    
    v_wt_l  = s_cowl['roof_l'][0]
    v_wt_ml = bm.verts.new((-0.55, 5.38, z_cantrail))
    v_wt_c  = bm.verts.new(( 0.0,  5.42, z_cantrail))
    v_wt_mr = bm.verts.new(( 0.55, 5.38, z_cantrail))
    v_wt_r  = s_cowl['roof_r'][0]
    
    add_quad(v_wb_l,  v_wt_l,  v_wt_ml, v_wb_ml, mat_idx=4)
    add_quad(v_wb_ml, v_wt_ml, v_wt_c,  v_wb_c,  mat_idx=4)
    add_quad(v_wb_c,  v_wt_c,  v_wt_mr, v_wb_mr, mat_idx=4)
    add_quad(v_wb_mr, v_wt_mr, v_wt_r,  v_wb_r,  mat_idx=4)
    
    # Dual Pantograph Wipers
    add_box(bm, (-0.55, 5.82, z_waist + 0.05), (-0.50, 5.86, z_waist + 0.70), mat_idx=7)
    add_box(bm, ( 0.20, 5.82, z_waist + 0.05), ( 0.25, 5.86, z_waist + 0.70), mat_idx=7)
    
    # Driver Cabin through windshield
    add_box(bm, (0.42, 5.35, z_floor + 0.05), (0.78, 5.70, z_floor + 0.95), mat_idx=6)
    add_box(bm, (0.55, 5.65, z_waist + 0.15), (0.65, 5.75, z_waist + 0.40), mat_idx=7)
    add_box(bm, (-0.85, 5.35, z_floor + 0.05), (-0.45, 5.70, z_floor + 0.95), mat_idx=6)
    
    # 2. Integrated Upper Destination Board & Visor Canopy ("KERALA")
    v_vs_l  = bm.verts.new((-w_cantrail, 5.48, 3.42))
    v_vs_ml = bm.verts.new((-0.60,       5.56, 3.45))
    v_vs_c  = bm.verts.new(( 0.0,        5.62, 3.48))
    v_vs_mr = bm.verts.new(( 0.60,       5.56, 3.45))
    v_vs_r  = bm.verts.new(( w_cantrail, 5.48, 3.42))
    
    # Metallic Gold Visor Brow Lip
    add_quad(v_wt_l,  v_vs_l,  v_vs_ml, v_wt_ml, mat_idx=3)
    add_quad(v_wt_ml, v_vs_ml, v_vs_c,  v_wt_c,  mat_idx=3)
    add_quad(v_wt_c,  v_vs_c,  v_vs_mr, v_wt_mr, mat_idx=3)
    add_quad(v_wt_mr, v_vs_mr, v_vs_r,  v_wt_r,  mat_idx=3)
    
    # Visor Under-shade (Satin Black)
    v_vu_l  = bm.verts.new((-w_cantrail, 5.44, 3.38))
    v_vu_ml = bm.verts.new((-0.60,       5.52, 3.41))
    v_vu_c  = bm.verts.new(( 0.0,        5.58, 3.44))
    v_vu_mr = bm.verts.new(( 0.60,       5.52, 3.41))
    v_vu_r  = bm.verts.new(( w_cantrail, 5.44, 3.38))
    add_quad(v_vu_l,  v_vs_l,  v_vs_ml, v_vu_ml, mat_idx=7)
    add_quad(v_vu_ml, v_vs_ml, v_vs_c,  v_vu_c,  mat_idx=7)
    add_quad(v_vu_c,  v_vs_c,  v_vs_mr, v_vu_mr, mat_idx=7)
    add_quad(v_vu_mr, v_vs_mr, v_vs_r,  v_vu_r,  mat_idx=7)
    
    # Glowing Cyan Route Display Matrix ("KERALA")
    rt_hw = 0.90
    v_rt_bl = bm.verts.new((-rt_hw, 5.46, 3.52))
    v_rt_bc = bm.verts.new(( 0.0,   5.50, 3.52))
    v_rt_br = bm.verts.new(( rt_hw, 5.46, 3.52))
    v_rt_tl = bm.verts.new((-rt_hw, 5.46, 3.75))
    v_rt_tc = bm.verts.new(( 0.0,   5.50, 3.75))
    v_rt_tr = bm.verts.new(( rt_hw, 5.46, 3.75))
    
    add_quad(v_rt_bl, v_rt_tl, v_rt_tc, v_rt_bc, mat_idx=13)
    add_quad(v_rt_bc, v_rt_tc, v_rt_tr, v_rt_br, mat_idx=13)
    
    # Visor Crown Surround
    add_quad(v_vs_l,  s_cowl['roof_l'][1], s_cowl['roof_l'][3], v_rt_tl, mat_idx=0)
    add_quad(v_vs_l,  v_rt_tl, v_rt_bl, v_vs_ml, mat_idx=0)
    add_quad(v_vs_ml, v_rt_bl, v_rt_bc, v_vs_c,  mat_idx=0)
    add_quad(v_vs_c,  v_rt_bc, v_rt_br, v_vs_mr, mat_idx=0)
    add_quad(v_vs_mr, v_rt_br, v_rt_tr, v_vs_r,  mat_idx=0)
    add_quad(v_rt_tr, s_cowl['roof_r'][3], s_cowl['roof_r'][1], v_vs_r, mat_idx=0)
    add_quad(v_rt_tl, s_cowl['roof_l'][3], s_cowl['roof_l'][5], v_rt_tc, mat_idx=0)
    add_quad(v_rt_tc, s_cowl['roof_r'][5], s_cowl['roof_r'][3], v_rt_tr, mat_idx=0)
    
    # 3. Sculpted Front Bonnet Cowl (Smooth Pearl White Base with Sweeping Chevrons)
    v_fc_l  = bm.verts.new((-w_waist, 5.82, z_floor))
    v_fc_ml = bm.verts.new((-0.65,    5.92, z_floor))
    v_fc_c  = bm.verts.new(( 0.0,     5.98, z_floor))
    v_fc_mr = bm.verts.new(( 0.65,    5.92, z_floor))
    v_fc_r  = bm.verts.new(( w_waist, 5.82, z_floor))
    
    # Front Cowl Panels (Pure Pearl White)
    add_quad(v_fc_l,  v_fc_ml, v_wb_ml, v_wb_l,  mat_idx=0)
    add_quad(v_fc_ml, v_fc_c,  v_wb_c,  v_wb_ml, mat_idx=0)
    add_quad(v_fc_c,  v_fc_mr, v_wb_mr, v_wb_c,  mat_idx=0)
    add_quad(v_fc_mr, v_fc_r,  v_wb_r,  v_wb_mr, mat_idx=0)
    
    add_quad(s_cowl['L_floor'], s_cowl['L_waist'], v_wb_l, v_fc_l, mat_idx=0)
    add_quad(v_fc_r, v_wb_r, s_cowl['R_waist'], s_cowl['R_floor'], mat_idx=0)
    
    # Front Cowl Sweeping Graphic Chevrons (V-shaped Turquoise & Purple chevrons from reference)
    # Left Cowl Turquoise Chevron
    v_cv_l0 = bm.verts.new((-1.15, 5.80, 1.45))
    v_cv_l1 = bm.verts.new((-0.25, 5.94, 1.55))
    v_cv_l2 = bm.verts.new((-0.25, 5.94, 1.68))
    v_cv_l3 = bm.verts.new((-1.15, 5.80, 1.58))
    add_quad(v_cv_l0, v_cv_l1, v_cv_l2, v_cv_l3, mat_idx=1) # Cyan Left Chevron
    
    # Right Cowl Royal Purple Chevron
    v_cv_r0 = bm.verts.new(( 0.25, 5.94, 1.55))
    v_cv_r1 = bm.verts.new(( 1.15, 5.80, 1.45))
    v_cv_r2 = bm.verts.new(( 1.15, 5.80, 1.58))
    v_cv_r3 = bm.verts.new(( 0.25, 5.94, 1.68))
    add_quad(v_cv_r0, v_cv_r1, v_cv_r2, v_cv_r3, mat_idx=2) # Purple Right Chevron
    
    # Metallic Gold Accent Chevron outline
    v_cg_l0 = bm.verts.new((-1.16, 5.80, 1.43))
    v_cg_l1 = bm.verts.new((-0.25, 5.94, 1.53))
    add_quad(v_cg_l0, v_cg_l1, v_cv_l1, v_cv_l0, mat_idx=3)
    
    v_cg_r0 = bm.verts.new(( 0.25, 5.94, 1.53))
    v_cg_r1 = bm.verts.new(( 1.16, 5.80, 1.43))
    add_quad(v_cg_r0, v_cg_r1, v_cv_r1, v_cv_r0, mat_idx=3)
    
    # Front Bonnet Chrome Emblem Badge
    v_emb_t = bm.verts.new(( 0.0,  5.99, 1.70))
    v_emb_l = bm.verts.new((-0.18, 5.96, 1.60))
    v_emb_r = bm.verts.new(( 0.18, 5.96, 1.60))
    v_emb_b = bm.verts.new(( 0.0,  5.99, 1.50))
    add_quad(v_emb_l, v_emb_t, v_emb_r, v_emb_b, mat_idx=8)
    
    # 4. Front Radiator Grille (Polished Chrome on Recessed Satin Black Mesh)
    v_gr_tl = v_fc_ml
    v_gr_tc = v_fc_c
    v_gr_tr = v_fc_mr
    v_gr_bl = bm.verts.new((-0.65, 5.94, 0.85))
    v_gr_bc = bm.verts.new(( 0.0,  6.00, 0.85))
    v_gr_br = bm.verts.new(( 0.65, 5.94, 0.85))
    
    add_quad(v_gr_bl, v_gr_bc, v_gr_tc, v_gr_tl, mat_idx=7)
    add_quad(v_gr_bc, v_gr_br, v_gr_tr, v_gr_tc, mat_idx=7)
    
    for slat_z in [1.25, 1.08, 0.92]:
        s_y0 = 5.95
        s_yc = 6.015
        v_s_l  = bm.verts.new((-0.62, s_y0, slat_z))
        v_s_c  = bm.verts.new(( 0.0,  s_yc, slat_z))
        v_s_r  = bm.verts.new(( 0.62, s_y0, slat_z))
        v_s_lt = bm.verts.new((-0.62, s_y0, slat_z + 0.04))
        v_s_ct = bm.verts.new(( 0.0,  s_yc, slat_z + 0.04))
        v_s_rt = bm.verts.new(( 0.62, s_y0, slat_z + 0.04))
        add_quad(v_s_l, v_s_c, v_s_ct, v_s_lt, mat_idx=8)
        add_quad(v_s_c, v_s_r, v_s_rt, v_s_ct, mat_idx=8)
        
    # 5. Multi-Element Modern Projector LED Headlamp Clusters
    # Left Headlamp
    v_hl_l_tl = v_fc_l
    v_hl_l_tr = v_fc_ml
    v_hl_l_br = v_gr_bl
    v_hl_l_bl = bm.verts.new((-w_waist * 0.98, 5.86, 0.85))
    add_quad(v_hl_l_bl, v_hl_l_br, v_hl_l_tr, v_hl_l_tl, mat_idx=7)
    
    add_box(bm, (-0.90, 5.90, 0.95), (-0.72, 5.93, 1.15), mat_idx=10)
    add_box(bm, (-1.15, 5.85, 0.95), (-0.97, 5.88, 1.15), mat_idx=10)
    
    v_drl_l0 = bm.verts.new((-1.20, 5.85, 1.34))
    v_drl_l1 = bm.verts.new((-0.68, 5.92, 1.36))
    v_drl_l2 = bm.verts.new((-0.68, 5.92, 1.28))
    v_drl_l3 = bm.verts.new((-1.20, 5.85, 1.26))
    add_quad(v_drl_l3, v_drl_l2, v_drl_l1, v_drl_l0, mat_idx=11)
    
    # Right Headlamp
    v_hl_r_tl = v_fc_mr
    v_hl_r_tr = v_fc_r
    v_hl_r_bl = v_gr_br
    v_hl_r_br = bm.verts.new((w_waist * 0.98, 5.86, 0.85))
    add_quad(v_hl_r_bl, v_hl_r_br, v_hl_r_tr, v_hl_r_tl, mat_idx=7)
    
    add_box(bm, (0.72, 5.90, 0.95), (0.90, 5.93, 1.15), mat_idx=10)
    add_box(bm, (0.97, 5.85, 0.95), (1.15, 5.88, 1.15), mat_idx=10)
    
    v_drl_r0 = bm.verts.new((0.68, 5.92, 1.36))
    v_drl_r1 = bm.verts.new((1.20, 5.85, 1.34))
    v_drl_r2 = bm.verts.new((1.20, 5.85, 1.26))
    v_drl_r3 = bm.verts.new((0.68, 5.92, 1.28))
    add_quad(v_drl_r3, v_drl_r2, v_drl_r1, v_drl_r0, mat_idx=11)
    
    # 6. Sculpted Front Bumper
    v_bmp_tl = v_hl_l_bl
    v_bmp_tc = v_gr_bc
    v_bmp_tr = v_hl_r_br
    v_bmp_bl = bm.verts.new((-w_waist * 0.96, 5.92, z_skirt))
    v_bmp_bc = bm.verts.new(( 0.0,             6.02, z_skirt))
    v_bmp_br = bm.verts.new(( w_waist * 0.96, 5.92, z_skirt))
    
    add_quad(v_bmp_bl, v_bmp_bc, v_bmp_tc, v_bmp_tl, mat_idx=0)
    add_quad(v_bmp_bc, v_bmp_br, v_bmp_tr, v_bmp_tc, mat_idx=0)
    
    add_box(bm, (-1.10, 5.91, 0.48), (-0.88, 5.95, 0.65), mat_idx=10)
    add_box(bm, ( 0.88, 5.91, 0.48), ( 1.10, 5.95, 0.65), mat_idx=10)
    add_box(bm, (-0.50, 5.96, 0.42), ( 0.50, 6.02, 0.52), mat_idx=7)
    
    add_quad(s_cowl['Bot_L'], v_bmp_bl, v_bmp_br, s_cowl['Bot_R'], mat_idx=7)
    add_quad(s_cowl['L_skirt'], s_cowl['L_floor'], v_fc_l, v_bmp_tl, mat_idx=0)
    add_quad(v_bmp_tr, v_fc_r, s_cowl['R_floor'], s_cowl['R_skirt'], mat_idx=0)
    
    # 7. Aerodynamic "Elephant-Ear" Side Mirrors with Curved Arms
    # Left Mirror:
    add_box(bm, (-w_waist - 0.24, 5.28, 3.10), (-w_waist + 0.02, 5.34, 3.16), mat_idx=7) # Top Arm from A-pillar
    add_box(bm, (-w_waist - 0.24, 5.48, 2.05), (-w_waist + 0.02, 5.54, 2.11), mat_idx=7) # Bottom Arm
    add_box(bm, (-w_waist - 0.35, 5.30, 2.15), (-w_waist - 0.20, 5.46, 3.15), mat_idx=0) # Mirror Shell (White)
    add_box(bm, (-w_waist - 0.33, 5.29, 2.20), (-w_waist - 0.22, 5.30, 3.10), mat_idx=4) # Main Mirror Glass
    add_box(bm, (-w_waist - 0.33, 5.30, 1.95), (-w_waist - 0.22, 5.42, 2.12), mat_idx=0) # Spotter Casing
    add_box(bm, (-w_waist - 0.32, 5.29, 1.97), (-w_waist - 0.23, 5.30, 2.10), mat_idx=4) # Spotter Glass
    
    # Right Mirror:
    add_box(bm, (w_waist - 0.02, 5.28, 3.10), (w_waist + 0.24, 5.34, 3.16), mat_idx=7)
    add_box(bm, (w_waist - 0.02, 5.48, 2.05), (w_waist + 0.24, 5.54, 2.11), mat_idx=7)
    add_box(bm, (w_waist + 0.20, 5.30, 2.15), (w_waist + 0.35, 5.46, 3.15), mat_idx=0)
    add_box(bm, (w_waist + 0.22, 5.29, 2.20), (w_waist + 0.33, 5.30, 3.10), mat_idx=4)
    add_box(bm, (w_waist + 0.22, 5.30, 1.95), (w_waist + 0.33, 5.42, 2.12), mat_idx=0)
    add_box(bm, (w_waist + 0.23, 5.29, 1.97), (w_waist + 0.32, 5.30, 2.10), mat_idx=4)

    # =========================================================================
    # SCULPTED REAR FASCIA (ZERO-Z-FIGHTING ENGINE HATCH & AERODYNAMIC SPOILER)
    # =========================================================================
    s_rear = st[-5.90]
    
    # 1. Rear Panoramic Tinted Window
    v_rw_bl = bm.verts.new((-0.92, -5.90, 2.45))
    v_rw_bc = bm.verts.new(( 0.0,  -5.90, 2.45))
    v_rw_br = bm.verts.new(( 0.92, -5.90, 2.45))
    v_rw_tl = bm.verts.new((-0.90, -5.90, 3.35))
    v_rw_tc = bm.verts.new(( 0.0,  -5.90, 3.35))
    v_rw_tr = bm.verts.new(( 0.90, -5.90, 3.35))
    
    add_quad(v_rw_bc, v_rw_bl, v_rw_tl, v_rw_tc, mat_idx=4)
    add_quad(v_rw_br, v_rw_bc, v_rw_tc, v_rw_tr, mat_idx=4)
    
    add_quad(v_rw_bl, s_rear['L_waist'], s_rear['roof_l'][0], v_rw_tl, mat_idx=0)
    add_quad(s_rear['R_waist'], v_rw_br, v_rw_tr, s_rear['roof_r'][0], mat_idx=0)
    
    # 2. Aerodynamic Rear Roof Aerofoil Spoiler Wing
    add_quad(v_rw_tc, v_rw_tl, s_rear['roof_l'][3], s_rear['roof_l'][5], mat_idx=0)
    add_quad(v_rw_tr, v_rw_tc, s_rear['roof_r'][5], s_rear['roof_r'][3], mat_idx=0)
    add_quad(v_rw_tl, s_rear['roof_l'][0], s_rear['roof_l'][1], s_rear['roof_l'][3], mat_idx=0)
    add_quad(s_rear['roof_r'][0], v_rw_tr, s_rear['roof_r'][3], s_rear['roof_r'][1], mat_idx=0)
    
    add_box(bm, (-1.18, -6.10, 3.65), (1.18, -5.92, 3.75), mat_idx=0)
    add_box(bm, (-1.20, -6.12, 3.60), (-1.16, -5.90, 3.82), mat_idx=3)
    add_box(bm, ( 1.16, -6.12, 3.60), ( 1.20, -5.90, 3.82), mat_idx=3)
    
    add_box(bm, (-0.45, -6.07, 3.48), (0.45, -6.05, 3.54), mat_idx=12)
    
    # 3. Modern Vertical LED Taillight Stacks
    # Left Taillight
    add_box(bm, (-1.24, -5.93, 2.05), (-0.93, -5.901, 2.45), mat_idx=12)
    add_box(bm, (-1.24, -5.93, 1.65), (-0.93, -5.901, 2.05), mat_idx=12)
    add_box(bm, (-1.24, -5.93, 1.25), (-0.93, -5.901, 1.65), mat_idx=11)
    add_box(bm, (-1.24, -5.93, 0.85), (-0.93, -5.901, 1.25), mat_idx=8)
    
    # Right Taillight
    add_box(bm, (0.93, -5.93, 2.05), (1.24, -5.901, 2.45), mat_idx=12)
    add_box(bm, (0.93, -5.93, 1.65), (1.24, -5.901, 2.05), mat_idx=12)
    add_box(bm, (0.93, -5.93, 1.25), (1.24, -5.901, 1.65), mat_idx=11)
    add_box(bm, (0.93, -5.93, 0.85), (1.24, -5.901, 1.25), mat_idx=8)
    
    # 4. REAR ENGINE HATCH WALL — CLEAN TOPOLOGY WITH RECESSED LOUVRE APERTURE
    # To permanently prevent Z-fighting: we construct the white hatch wall with a
    # geometric aperture (hole) for the louvre grille. No two faces share coordinates!
    # Grille aperture bounds: X = -0.78 to +0.78, Z = 1.35 to 2.05 at Y = -5.90
    v_h_tl = bm.verts.new((-0.92, -5.90, 2.45)) # Under window left
    v_h_tr = bm.verts.new(( 0.92, -5.90, 2.45)) # Under window right
    v_h_bl = bm.verts.new((-0.92, -5.90, 0.85)) # Above bumper left
    v_h_br = bm.verts.new(( 0.92, -5.90, 0.85)) # Above bumper right
    
    # Aperture perimeter vertices
    v_ap_tl = bm.verts.new((-0.78, -5.90, 2.05))
    v_ap_tr = bm.verts.new(( 0.78, -5.90, 2.05))
    v_ap_bl = bm.verts.new((-0.78, -5.90, 1.35))
    v_ap_br = bm.verts.new(( 0.78, -5.90, 1.35))
    
    # 4 White Surrounding Panels (Top, Bottom, Left, Right of Grille)
    add_quad(v_h_tl, v_ap_tl, v_ap_tr, v_h_tr, mat_idx=0) # Panel above grille
    add_quad(v_h_bl, v_h_br, v_ap_br, v_ap_bl, mat_idx=0) # Panel below grille
    add_quad(v_h_bl, v_ap_bl, v_ap_tl, v_h_tl, mat_idx=0) # Panel left of grille
    add_quad(v_ap_br, v_h_br, v_h_tr, v_ap_tr, mat_idx=0) # Panel right of grille
    
    # Outer flank walls behind taillights
    v_tl_bl = bm.verts.new((-w_waist, -5.90, 0.85))
    v_tl_br = bm.verts.new(( w_waist, -5.90, 0.85))
    add_quad(v_tl_bl, v_h_bl, v_h_tl, s_rear['L_waist'], mat_idx=0)
    add_quad(v_h_br, v_tl_br, s_rear['R_waist'], v_h_tr, mat_idx=0)
    
    # Rear Engine Louvre Recess Box (Recessed into aperture: Y = -5.88 to -5.82)
    # Recessed Backplate (Satin Black)
    v_rec_tl = bm.verts.new((-0.78, -5.84, 2.05))
    v_rec_tr = bm.verts.new(( 0.78, -5.84, 2.05))
    v_rec_bl = bm.verts.new((-0.78, -5.84, 1.35))
    v_rec_br = bm.verts.new(( 0.78, -5.84, 1.35))
    add_quad(v_rec_bl, v_rec_br, v_rec_tr, v_rec_tl, mat_idx=7) # Black backplate
    
    # Recess Reveal Walls (Connecting outer wall to backplate)
    add_quad(v_ap_bl, v_rec_bl, v_rec_tl, v_ap_tl, mat_idx=7) # Left reveal
    add_quad(v_ap_tr, v_rec_tr, v_rec_br, v_ap_br, mat_idx=7) # Right reveal
    add_quad(v_ap_tl, v_rec_tl, v_rec_tr, v_ap_tr, mat_idx=7) # Top reveal
    add_quad(v_rec_bl, v_ap_bl, v_ap_br, v_rec_br, mat_idx=7) # Bottom reveal
    
    # 3 Clean 3D Angled Louvre Slats (Satin Black with visible depth)
    for l_z in [1.88, 1.68, 1.48]:
        add_box(bm, (-0.76, -5.89, l_z), (0.76, -5.85, l_z + 0.06), mat_idx=7)
        
    # Registration Plate Recess Box (Yellow Kerala Commercial Plate)
    add_box(bm, (-0.38, -5.93, 1.05), (0.38, -5.905, 1.25), mat_idx=14)
    
    # Rear Dynamic Graphic Chevrons (Across engine hatch, matching reference rear view)
    # Turquoise sweeping diagonal ribbon
    v_rg_l0 = bm.verts.new((-0.90, -5.902, 1.85))
    v_rg_l1 = bm.verts.new(( 0.10, -5.902, 2.35))
    v_rg_l2 = bm.verts.new(( 0.10, -5.902, 2.44))
    v_rg_l3 = bm.verts.new((-0.90, -5.902, 2.05))
    add_quad(v_rg_l0, v_rg_l1, v_rg_l2, v_rg_l3, mat_idx=1)
    
    # Royal Purple trailing chevron
    v_rp_l0 = bm.verts.new((-0.90, -5.902, 1.65))
    v_rp_l1 = bm.verts.new((-0.10, -5.902, 2.15))
    v_rp_l2 = bm.verts.new((-0.10, -5.902, 2.25))
    v_rp_l3 = bm.verts.new((-0.90, -5.902, 1.80))
    add_quad(v_rp_l0, v_rp_l1, v_rp_l2, v_rp_l3, mat_idx=2)
    
    # Metallic Gold accent ribbon
    v_rgold_l0 = bm.verts.new((-0.90, -5.902, 1.60))
    v_rgold_l1 = bm.verts.new((-0.10, -5.902, 2.10))
    add_quad(v_rgold_l0, v_rgold_l1, v_rp_l1, v_rp_l0, mat_idx=3)
    
    # 5. Sculpted Rear Bumper
    v_rbmp_tl = bm.verts.new((-w_waist,        -6.00, 0.85))
    v_rbmp_tc = bm.verts.new(( 0.0,            -6.02, 0.85))
    v_rbmp_tr = bm.verts.new(( w_waist,        -6.00, 0.85))
    v_rbmp_bl = bm.verts.new((-w_waist * 0.96, -6.00, z_skirt))
    v_rbmp_bc = bm.verts.new(( 0.0,            -6.02, z_skirt))
    v_rbmp_br = bm.verts.new(( w_waist * 0.96, -6.00, z_skirt))
    
    add_quad(v_rbmp_bc, v_rbmp_bl, v_rbmp_tl, v_rbmp_tc, mat_idx=7)
    add_quad(v_rbmp_br, v_rbmp_bc, v_rbmp_tc, v_rbmp_tr, mat_idx=7)
    
    add_box(bm, (-1.15, -6.01, 0.60), (-0.85, -5.99, 0.70), mat_idx=12)
    add_box(bm, ( 0.85, -6.01, 0.60), ( 1.15, -5.99, 0.70), mat_idx=12)
    
    add_quad(s_rear['L_skirt'], v_tl_bl, v_rbmp_tl, v_rbmp_bl, mat_idx=7)
    add_quad(v_rbmp_tr, v_tl_br, s_rear['R_skirt'], v_rbmp_br, mat_idx=7)
    add_quad(s_rear['Bot_R'], s_rear['Bot_L'], v_rbmp_bl, v_rbmp_br, mat_idx=7)

    # =========================================================================
    # ROOFTOP AIR CONDITIONING POD & EMERGENCY HATCHES (UNMODIFIED)
    # =========================================================================
    add_box(bm, (-0.68, -1.20, 3.80), (0.68, 2.20, 4.15), mat_idx=0)
    add_box(bm, (-0.68, 2.20, 3.80), (0.68, 2.45, 3.98), mat_idx=0)
    add_box(bm, (-0.68, -1.45, 3.80), (0.68, -1.20, 3.98), mat_idx=0)
    add_box(bm, (-0.72, -1.18, 3.82), (-0.68, 2.18, 4.10), mat_idx=1)
    add_box(bm, ( 0.68, -1.18, 3.82), ( 0.72, 2.18, 4.10), mat_idx=1)
    
    fan_centers_y = [-0.60, 0.50, 1.60]
    for f_y in fan_centers_y:
        add_box(bm, (-0.45, f_y - 0.35, 4.14), (0.45, f_y + 0.35, 4.16), mat_idx=7)
        add_box(bm, (-0.12, f_y - 0.12, 4.15), (0.12, f_y + 0.12, 4.18), mat_idx=8)
        
    add_box(bm, (-0.45,  3.40, 3.80), (0.45,  4.10, 3.86), mat_idx=0)
    add_box(bm, (-0.42,  3.43, 3.85), (0.42,  4.07, 3.87), mat_idx=7)
    add_box(bm, (-0.45, -2.80, 3.80), (0.45, -2.10, 3.86), mat_idx=0)
    add_box(bm, (-0.42, -2.77, 3.85), (0.42, -2.13, 3.87), mat_idx=7)

    # Finalize mesh
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    
    obj = bpy.data.objects.new("Bus_Body", mesh)
    bpy.context.collection.objects.link(obj)
    
    for poly in mesh.polygons:
        poly.use_smooth = True
        
    mod = obj.modifiers.new(name="EdgeSplit", type='EDGE_SPLIT')
    mod.split_angle = math.radians(45)
    
    return obj

def setup_cameras_and_lights():
    """Sets up standard orthographic and perspective cameras calibrated for the 12m coach."""
    cameras = {}
    
    # 1. Front Orthographic Camera
    cam_front_data = bpy.data.cameras.new("Cam_Front")
    cam_front_data.type = 'ORTHO'
    cam_front_data.ortho_scale = 7.2
    cam_front_obj = bpy.data.objects.new("Cam_Front", cam_front_data)
    cam_front_obj.location = (0.0, 18.0, 2.10)
    cam_front_obj.rotation_euler = (math.radians(90), 0, math.radians(180))
    bpy.context.collection.objects.link(cam_front_obj)
    cameras['front'] = cam_front_obj
    
    # 2. Side Orthographic Camera
    cam_side_data = bpy.data.cameras.new("Cam_Side")
    cam_side_data.type = 'ORTHO'
    cam_side_data.ortho_scale = 15.6
    cam_side_obj = bpy.data.objects.new("Cam_Side", cam_side_data)
    cam_side_obj.location = (-20.0, 0.0, 2.10)
    cam_side_obj.rotation_euler = (math.radians(90), 0, math.radians(-90))
    bpy.context.collection.objects.link(cam_side_obj)
    cameras['side'] = cam_side_obj
    
    # 3. Rear Orthographic Camera
    cam_rear_data = bpy.data.cameras.new("Cam_Rear")
    cam_rear_data.type = 'ORTHO'
    cam_rear_data.ortho_scale = 7.2
    cam_rear_obj = bpy.data.objects.new("Cam_Rear", cam_rear_data)
    cam_rear_obj.location = (0.0, -18.0, 2.10)
    cam_rear_obj.rotation_euler = (math.radians(90), 0, 0)
    bpy.context.collection.objects.link(cam_rear_obj)
    cameras['rear'] = cam_rear_obj
    
    # 4. Three-Quarter Perspective Camera
    cam_persp_data = bpy.data.cameras.new("Cam_Perspective")
    cam_persp_data.type = 'PERSP'
    cam_persp_data.lens = 42
    cam_persp_obj = bpy.data.objects.new("Cam_Perspective", cam_persp_data)
    cam_persp_obj.location = (-13.5, 13.5, 5.5)
    direction = Vector((0.0, 0.8, 2.0)) - cam_persp_obj.location
    rot_quat = direction.to_track_quat('-Z', 'Y')
    cam_persp_obj.rotation_euler = rot_quat.to_euler()
    bpy.context.collection.objects.link(cam_persp_obj)
    cameras['perspective'] = cam_persp_obj
    
    return cameras

def compute_statistics():
    """Computes exact bounding boxes, vertex counts, and face counts."""
    stats = {}
    total_verts = 0
    total_faces = 0
    total_tris = 0
    
    objects = [obj for obj in bpy.data.objects if obj.type == 'MESH']
    
    all_min = Vector((float('inf'), float('inf'), float('inf')))
    all_max = Vector((float('-inf'), float('-inf'), float('-inf')))
    
    obj_stats = []
    for obj in objects:
        mesh = obj.data
        v_count = len(mesh.vertices)
        f_count = len(mesh.polygons)
        
        t_count = sum(len(p.vertices) - 2 for p in mesh.polygons)
        
        total_verts += v_count
        total_faces += f_count
        total_tris += t_count
        
        bbox_corners = [obj.matrix_world @ Vector(corner) for corner in obj.bound_box]
        min_c = Vector((min(c[0] for c in bbox_corners), min(c[1] for c in bbox_corners), min(c[2] for c in bbox_corners)))
        max_c = Vector((max(c[0] for c in bbox_corners), max(c[1] for c in bbox_corners), max(c[2] for c in bbox_corners)))
        dims = max_c - min_c
        
        for i in range(3):
            all_min[i] = min(all_min[i], min_c[i])
            all_max[i] = max(all_max[i], max_c[i])
            
        obj_stats.append({
            'name': obj.name,
            'verts': v_count,
            'faces': f_count,
            'tris': t_count,
            'location': tuple(round(c, 3) for c in obj.location),
            'dims': (round(dims.x, 3), round(dims.y, 3), round(dims.z, 3))
        })
        
    overall_dims = all_max - all_min
    
    stats['objects'] = obj_stats
    stats['total_verts'] = total_verts
    stats['total_faces'] = total_faces
    stats['total_tris'] = total_tris
    stats['overall_min'] = tuple(round(c, 3) for c in all_min)
    stats['overall_max'] = tuple(round(c, 3) for c in all_max)
    stats['overall_dims'] = (round(overall_dims.x, 3), round(overall_dims.y, 3), round(overall_dims.z, 3))
    
    return stats

def main():
    print("=== Bharat Bus Simulator: 12m Three-Axle Tourist Coach — Polish Pass ===")
    setup_scene()
    
    # 1. Wheels & Axle Reference: Six Wheel Positions (STRICTLY PRESERVED)
    wheel_fl = build_commercial_wheel("Wheel_FL", is_front=True)
    wheel_fl.location = (-1.18, 3.50, 0.52)
    
    wheel_fr = build_commercial_wheel("Wheel_FR", is_front=True)
    wheel_fr.location = (1.18, 3.50, 0.52)
    wheel_fr.rotation_euler = (0, 0, math.pi)
    
    wheel_ml = build_commercial_wheel("Wheel_ML", is_front=False)
    wheel_ml.location = (-1.18, -2.30, 0.52)
    
    wheel_mr = build_commercial_wheel("Wheel_MR", is_front=False)
    wheel_mr.location = (1.18, -2.30, 0.52)
    wheel_mr.rotation_euler = (0, 0, math.pi)
    
    wheel_rl = build_commercial_wheel("Wheel_RL", is_front=False)
    wheel_rl.location = (-1.18, -3.60, 0.52)
    
    wheel_rr = build_commercial_wheel("Wheel_RR", is_front=False)
    wheel_rr.location = (1.18, -3.60, 0.52)
    wheel_rr.rotation_euler = (0, 0, math.pi)
    
    # Wheel Verification
    expected_wheels = {
        'Wheel_FL': (-1.18, 3.50, 0.52),
        'Wheel_FR': (1.18, 3.50, 0.52),
        'Wheel_ML': (-1.18, -2.30, 0.52),
        'Wheel_MR': (1.18, -2.30, 0.52),
        'Wheel_RL': (-1.18, -3.60, 0.52),
        'Wheel_RR': (1.18, -3.60, 0.52),
    }
    print("\n=== WHEEL CENTRE VERIFICATION ===")
    all_wheels_ok = True
    for wname, expected_loc in expected_wheels.items():
        obj = bpy.data.objects.get(wname)
        if obj is None:
            print(f"  FAIL: {wname} NOT FOUND")
            all_wheels_ok = False
        else:
            loc = tuple(round(c, 3) for c in obj.location)
            match = all(abs(loc[i] - expected_loc[i]) < 0.001 for i in range(3))
            status = "OK" if match else "MISMATCH"
            print(f"  {wname}: {loc} — {status}")
            if not match:
                all_wheels_ok = False
    print(f"  All wheels correct: {all_wheels_ok}")
    print("=================================\n")
    
    # 2. Main Bus Body Shell
    body = build_12m_tourist_coach()
    body.location = (0.0, 0.0, 0.0)
    
    # 3. Setup Cameras
    cameras = setup_cameras_and_lights()
    
    # 4. Save Master Blender File
    output_dir = r"C:\Project\BHARAT BUS SIMULATOR\art_src\vehicles\kerala_bus"
    os.makedirs(output_dir, exist_ok=True)
    blend_path = os.path.join(output_dir, "kerala_bus_exterior_blockout.blend")
    bpy.ops.wm.save_as_mainfile(filepath=blend_path)
    print(f"Master .blend saved to: {blend_path}")
    
    # 5. Render Preview Images
    scene = bpy.context.scene
    renders = {
        'front': "render_preview_front.png",
        'side': "render_preview_side.png",
        'rear': "render_preview_rear.png",
        'perspective': "render_preview_perspective.png"
    }
    
    for view_name, filename in renders.items():
        cam = cameras[view_name]
        scene.camera = cam
        out_filepath = os.path.join(output_dir, filename)
        scene.render.filepath = out_filepath
        print(f"Rendering {view_name} preview to {out_filepath}...")
        bpy.ops.render.render(write_still=True)
        print(f"Rendered {view_name} successfully.")
        
    # 6. Compute and report actual geometry metrics
    stats = compute_statistics()
    print("\n=== ACTUAL 12M COACH GEOMETRY & DIMENSION METRICS (POLISH PASS) ===")
    print(f"Overall Bounding Box (X, Y, Z): {stats['overall_dims']} metres")
    print(f"Overall Min Bounds: {stats['overall_min']}")
    print(f"Overall Max Bounds: {stats['overall_max']}")
    print(f"Total Vertices: {stats['total_verts']}")
    print(f"Total Faces/Polygons: {stats['total_faces']}")
    print(f"Total Triangles: {stats['total_tris']}")
    print("\nPer-Object Breakdown:")
    for obj_s in stats['objects']:
        print(f"  - {obj_s['name']}: {obj_s['tris']} tris, {obj_s['verts']} verts, Dims: {obj_s['dims']}m, Loc: {obj_s['location']}")
    print("===================================================\n")

if __name__ == "__main__":
    main()
