import bpy
import os

def export_kerala_bus():
    blend_path = r"C:\Project\BHARAT BUS SIMULATOR\art_src\vehicles\kerala_bus\kerala_bus_exterior_blockout.blend"
    fbx_path = r"C:\Project\BHARAT BUS SIMULATOR\unity\Assets\_Project\Vehicles\Models\KeralaBus12m.fbx"

    print(f"Opening blend: {blend_path}")
    bpy.ops.wm.open_mainfile(filepath=blend_path)

    # Deselect all
    bpy.ops.object.select_all(action='DESELECT')
    
    # 7 visual mesh objects
    target_names = [
        'Bus_Body',
        'Wheel_FL',
        'Wheel_FR',
        'Wheel_ML',
        'Wheel_MR',
        'Wheel_RL',
        'Wheel_RR'
    ]
    
    selected_count = 0
    for name in target_names:
        obj = bpy.data.objects.get(name)
        if obj and obj.type == 'MESH':
            obj.select_set(True)
            selected_count += 1
            print(f"Selected: {name}")
        else:
            print(f"ERROR: Target mesh '{name}' not found!")

    if selected_count != 7:
        raise RuntimeError(f"Expected 7 meshes, got {selected_count}")

    os.makedirs(os.path.dirname(fbx_path), exist_ok=True)
    
    print(f"Exporting to FBX: {fbx_path}")
    bpy.ops.export_scene.fbx(
        filepath=fbx_path,
        use_selection=True,
        object_types={'MESH'},
        axis_forward='-Z',
        axis_up='Y',
        global_scale=1.0,
        apply_unit_scale=True,
        apply_scale_options='FBX_SCALE_ALL',
        bake_space_transform=True,
        use_mesh_modifiers=True,
        mesh_smooth_type='FACE',
        embed_textures=False,
        path_mode='AUTO'
    )
    print("Master KeralaBus12m.fbx exported successfully.")

if __name__ == '__main__':
    export_kerala_bus()
