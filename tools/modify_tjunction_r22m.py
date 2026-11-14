"""
modify_tjunction_r22m.py
Creates a separate working copy of JUNC_TJunction_4Lane_Major in Blender,
expands corner kerb fillets from R15m (R=13.5m kerb-face) to R=22.0m,
preserving lane widths (3.5m), central median (2.0m), shoulders (1.5m),
road markings, and arm connection interfaces (at 42m).
Exports JUNC_TJunction_4Lane_Major_R22m.fbx without modifying the original.
"""

import bpy
import math
import os
import sys

def modify_tjunction():
    # Target file paths
    blend_path = os.path.abspath(r"unity/Assets/Game/Environment/KeralaRoadKit/Documentation/Kerala_Road_Kit.blend")
    output_fbx = os.path.abspath(r"unity/Assets/Game/Environment/KeralaRoadKit/Junctions/JUNC_TJunction_4Lane_Major_R22m.fbx")
    
    print(f"Loading Blender file: {blend_path}")
    bpy.ops.wm.open_mainfile(filepath=blend_path)
    
    orig_obj = bpy.data.objects.get("JUNC_TJunction_4Lane_Major")
    if not orig_obj:
        raise ValueError("Could not find JUNC_TJunction_4Lane_Major in Blender file.")
    
    # Duplicate object to preserve original 100% untouched
    bpy.ops.object.select_all(action='DESELECT')
    orig_obj.select_set(True)
    bpy.context.view_layer.objects.active = orig_obj
    bpy.ops.object.duplicate(linked=False)
    
    new_obj = bpy.context.active_object
    new_obj.name = "JUNC_TJunction_4Lane_Major_R22m"
    mesh = new_obj.data
    mesh.name = "Mesh_JUNC_TJunction_4Lane_Major_R22m"
    
    print(f"Created working copy: {new_obj.name} with {len(mesh.vertices)} verts, {len(mesh.polygons)} polys.")
    
    # Road geometry constants
    # Half-width of carriageway bed: 8.5m (from centerline)
    # Target fillet radius R = 22.0m
    R_new = 22.0
    half_w = 8.50
    center_c1 = (half_w + R_new, half_w + R_new)   # (+30.50, +30.50)
    center_c2 = (-half_w - R_new, half_w + R_new)  # (-30.50, +30.50)
    
    # Corner 1 (Right Corner between +X and +Y):
    # Arc angles from alpha = 0 (along +X at y=8.5) to alpha = pi/2 (along +Y at x=8.5)
    # 5 intermediate division segments (6 vertices along road edge)
    # Vertices to update:
    # Road edge: 0, 18, 21, 24, 27, 30, 35
    # Kerb inner: 20, 19, 22, 25, 28, 31, 53
    # Kerb outer top/back: 23, 26, 29, 32
    
    num_steps = 6
    for i in range(num_steps + 1):
        frac = i / float(num_steps)
        # alpha goes from 0 to pi/2
        # at frac = 0: tangent to +X arm -> (30.50, 8.50)
        # at frac = 1: tangent to +Y arm -> (8.50, 30.50)
        alpha = frac * (math.pi / 2.0)
        
        # Pavement edge (r = R_new)
        px = center_c1[0] - R_new * math.cos(alpha)
        py = center_c1[1] - R_new * math.sin(alpha)
        
        # Kerb front face (r = R_new - 0.15m)
        k_inner_x = center_c1[0] - (R_new - 0.15) * math.cos(alpha)
        k_inner_y = center_c1[1] - (R_new - 0.15) * math.sin(alpha)
        
        # Kerb back face (r = R_new - 0.50m)
        k_outer_x = center_c1[0] - (R_new - 0.50) * math.cos(alpha)
        k_outer_y = center_c1[1] - (R_new - 0.50) * math.sin(alpha)
        
        if i == 0:
            mesh.vertices[0].co.x = px
            mesh.vertices[0].co.y = py
            mesh.vertices[20].co.x = k_outer_x
            mesh.vertices[20].co.y = k_outer_y
        elif i == 1:
            mesh.vertices[18].co.x = px
            mesh.vertices[18].co.y = py
            mesh.vertices[19].co.x = k_inner_x
            mesh.vertices[19].co.y = k_inner_y
            mesh.vertices[23].co.x = k_outer_x
            mesh.vertices[23].co.y = k_outer_y
        elif i == 2:
            mesh.vertices[21].co.x = px
            mesh.vertices[21].co.y = py
            mesh.vertices[22].co.x = k_inner_x
            mesh.vertices[22].co.y = k_inner_y
            mesh.vertices[26].co.x = k_outer_x
            mesh.vertices[26].co.y = k_outer_y
        elif i == 3:
            mesh.vertices[24].co.x = px
            mesh.vertices[24].co.y = py
            mesh.vertices[25].co.x = k_inner_x
            mesh.vertices[25].co.y = k_inner_y
            mesh.vertices[29].co.x = k_outer_x
            mesh.vertices[29].co.y = k_outer_y
        elif i == 4:
            mesh.vertices[27].co.x = px
            mesh.vertices[27].co.y = py
            mesh.vertices[28].co.x = k_inner_x
            mesh.vertices[28].co.y = k_inner_y
            mesh.vertices[32].co.x = k_outer_x
            mesh.vertices[32].co.y = k_outer_y
        elif i == 5:
            mesh.vertices[30].co.x = px
            mesh.vertices[30].co.y = py
            mesh.vertices[31].co.x = k_inner_x
            mesh.vertices[31].co.y = k_inner_y
        elif i == 6:
            mesh.vertices[35].co.x = px
            mesh.vertices[35].co.y = py
            
    # Corner 2 (Left Corner between -X and +Y):
    # center_c2 = (-30.50, +30.50)
    # alpha goes from 0 (along +Y at x=-8.5) to pi/2 (along -X at y=8.5)
    for i in range(num_steps + 1):
        frac = i / float(num_steps)
        alpha = frac * (math.pi / 2.0)
        
        # Pavement edge
        px = center_c2[0] + R_new * math.sin(alpha)
        py = center_c2[1] - R_new * math.cos(alpha)
        
        # Kerb front face
        k_inner_x = center_c2[0] + (R_new - 0.15) * math.sin(alpha)
        k_inner_y = center_c2[1] - (R_new - 0.15) * math.cos(alpha)
        
        # Kerb back face
        k_outer_x = center_c2[0] + (R_new - 0.50) * math.sin(alpha)
        k_outer_y = center_c2[1] - (R_new - 0.50) * math.cos(alpha)
        
        if i == 0:
            mesh.vertices[33].co.x = px
            mesh.vertices[33].co.y = py
            mesh.vertices[53].co.x = k_outer_x
            mesh.vertices[53].co.y = k_outer_y
        elif i == 1:
            mesh.vertices[51].co.x = px
            mesh.vertices[51].co.y = py
            mesh.vertices[52].co.x = k_inner_x
            mesh.vertices[52].co.y = k_inner_y
            mesh.vertices[56].co.x = k_outer_x
            mesh.vertices[56].co.y = k_outer_y
        elif i == 2:
            mesh.vertices[54].co.x = px
            mesh.vertices[54].co.y = py
            mesh.vertices[55].co.x = k_inner_x
            mesh.vertices[55].co.y = k_inner_y
            mesh.vertices[59].co.x = k_outer_x
            mesh.vertices[59].co.y = k_outer_y
        elif i == 3:
            mesh.vertices[57].co.x = px
            mesh.vertices[57].co.y = py
            mesh.vertices[58].co.x = k_inner_x
            mesh.vertices[58].co.y = k_inner_y
            mesh.vertices[62].co.x = k_outer_x
            mesh.vertices[62].co.y = k_outer_y
        elif i == 4:
            mesh.vertices[60].co.x = px
            mesh.vertices[60].co.y = py
            mesh.vertices[61].co.x = k_inner_x
            mesh.vertices[61].co.y = k_inner_y
            mesh.vertices[65].co.x = k_outer_x
            mesh.vertices[65].co.y = k_outer_y
        elif i == 5:
            mesh.vertices[63].co.x = px
            mesh.vertices[63].co.y = py
            mesh.vertices[64].co.x = k_inner_x
            mesh.vertices[64].co.y = k_inner_y
        elif i == 6:
            mesh.vertices[68].co.x = px
            mesh.vertices[68].co.y = py

    # Update mesh geometry, normals, and bounds
    mesh.update()
    
    # Create UCX collision child mesh for the modified model
    ucx_mesh = mesh.copy()
    ucx_mesh.name = "UCX_JUNC_TJunction_4Lane_Major_R22m"
    ucx_obj = bpy.data.objects.new("UCX_JUNC_TJunction_4Lane_Major_R22m", ucx_mesh)
    bpy.context.collection.objects.link(ucx_obj)
    ucx_obj.parent = new_obj
    
    # Export FBX
    bpy.ops.object.select_all(action='DESELECT')
    new_obj.select_set(True)
    ucx_obj.select_set(True)
    bpy.context.view_layer.objects.active = new_obj
    
    os.makedirs(os.path.dirname(output_fbx), exist_ok=True)
    
    print(f"Exporting modified FBX to: {output_fbx}")
    bpy.ops.export_scene.fbx(
        filepath=output_fbx,
        use_selection=True,
        global_scale=1.0,
        apply_unit_scale=False,
        apply_scale_options='FBX_SCALE_NONE',
        bake_space_transform=False,
        object_types={'MESH'},
        use_mesh_modifiers=True,
        mesh_smooth_type='FACE',
        axis_forward='-Z',
        axis_up='Y'
    )
    
    print("FBX export successful.")

if __name__ == '__main__':
    modify_tjunction()
