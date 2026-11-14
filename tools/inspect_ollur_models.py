import bpy
import mathutils
import os

models = [
    'HWY4_Highway_Exit_Diverge_Taper.fbx',
    'MARK_Chevron_Diverge_Gore_Zone.fbx',
    'RAMP_Curved_Exit_Right_R80m.fbx',
    'RAMP_SlipRoad_Straight_60m.fbx',
    'HWY4_Straight_Medium_50m.fbx',
    'JUNC_TJunction_4Lane_Major_R22m.fbx'
]

root = r'c:\Project\BHARAT BUS SIMULATOR\unity\Assets\Game\Environment\KeralaRoadKit'

for m in models:
    found = None
    for r, d, files in os.walk(root):
        if m in files:
            found = os.path.join(r, m)
            break
    if not found:
        print(f'NOT FOUND: {m}')
        continue
    
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.fbx(filepath=found)
    
    print('='*60)
    print(f'MODEL: {m}')
    for obj in bpy.data.objects:
        if obj.type == 'MESH':
            bb = [obj.matrix_world @ mathutils.Vector(corner) for corner in obj.bound_box]
            min_x = min(v.x for v in bb)
            max_x = max(v.x for v in bb)
            min_y = min(v.y for v in bb)
            max_y = max(v.y for v in bb)
            min_z = min(v.z for v in bb)
            max_z = max(v.z for v in bb)
            print(f'  Object: {obj.name}, verts: {len(obj.data.vertices)}, faces: {len(obj.data.polygons)}')
            print(f'    Bounds X: [{min_x:.3f}, {max_x:.3f}] (width: {max_x-min_x:.3f}m)')
            print(f'    Bounds Y: [{min_y:.3f}, {max_y:.3f}] (length: {max_y-min_y:.3f}m)')
            print(f'    Bounds Z: [{min_z:.3f}, {max_z:.3f}] (height: {max_z-min_z:.3f}m)')
            
            # Find vertices at extrema
            verts = [obj.matrix_world @ v.co for v in obj.data.vertices]
            # Endpoints in Y (road direction in Blender)
            near_verts = [v for v in verts if abs(v.y - min_y) < 0.1]
            far_verts = [v for v in verts if abs(v.y - max_y) < 0.1]
            if near_verts:
                nx_min = min(v.x for v in near_verts)
                nx_max = max(v.x for v in near_verts)
                print(f'    Near Y={min_y:.3f}: X span [{nx_min:.3f}, {nx_max:.3f}] (width {nx_max-nx_min:.3f}m)')
            if far_verts:
                fx_min = min(v.x for v in far_verts)
                fx_max = max(v.x for v in far_verts)
                print(f'    Far  Y={max_y:.3f}: X span [{fx_min:.3f}, {fx_max:.3f}] (width {fx_max-fx_min:.3f}m)')
                
            mats = [s.material.name for s in obj.material_slots if s.material]
            print(f'    Materials: {mats}')
