import bpy
import math
import os

blend_path = r"C:\Users\praya\Downloads\Hino+rm+2 (1).blend"
repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
out_fbx_path = os.path.join(repo_root, "art_src", "vehicles", "hino_rm2", "models", "HINO_RM2_Clean.fbx")

print("=" * 60)
print("EXPORTING CLEAN HINO RM 2 BUS FROM:", blend_path)
print("TARGET FBX:", out_fbx_path)
print("=" * 60)

bpy.ops.wm.open_mainfile(filepath=blend_path)
depsgraph = bpy.context.evaluated_depsgraph_get()

# Material fallback lookup for previously empty materials
mat_main_body = bpy.data.materials.get("Material.021")
mat_glass = bpy.data.materials.get("Material.017")
mat_black = bpy.data.materials.get("black")
mat_plastic = bpy.data.materials.get("plastic")
mat_metal = bpy.data.materials.get("metal")
mat_white_light = bpy.data.materials.get("white light")

# Create a clean export scene
clean_scene = bpy.data.scenes.new("HinoCleanScene")
bpy.context.window.scene = clean_scene

root_empty = bpy.data.objects.new("Hino_RM2_Visuals", None)
clean_scene.collection.objects.link(root_empty)

orig_scene = bpy.data.scenes.get("Scene")

# Steering wheel parts to combine:
torus_names = {'Torus.003', 'Torus.004', 'Torus.005', 'Torus.011'}
st_eval_objs = []
for name in torus_names:
    o = orig_scene.objects.get(name)
    if o:
        st_eval_objs.append(o.evaluated_get(depsgraph))

# Compute common steering center
all_st_verts = []
for eo in st_eval_objs:
    all_st_verts.extend([eo.matrix_world @ v.co for v in eo.data.vertices])

st_center_x = sum(v.x for v in all_st_verts) / len(all_st_verts)
st_center_y = sum(v.y for v in all_st_verts) / len(all_st_verts)
st_center_z = sum(v.z for v in all_st_verts) / len(all_st_verts)
st_center = (st_center_x, st_center_y, st_center_z)
print(f"Computed Steering Wheel Center: {st_center}")

# Reference orientation from Torus.011
ref_torus = orig_scene.objects.get("Torus.011")
st_rot = ref_torus.rotation_euler if ref_torus else (0.3567, 0, 3.1416)

# Combine steering wheel meshes
combined_st_mesh = bpy.data.meshes.new("Mesh_Cockpit_SteeringWheel")
st_obj = bpy.data.objects.new("Cockpit_SteeringWheel", combined_st_mesh)
st_obj.location = st_center
st_obj.rotation_euler = st_rot
st_obj.scale = (1, 1, 1)

inv_st_mat = st_obj.matrix_world.inverted()

# Build merged geometry for steering wheel
merged_verts = []
merged_polys = []
merged_mat_indices = []
mat_slot_map = {}

for eo in st_eval_objs:
    eval_mesh = bpy.data.meshes.new_from_object(eo)
    v_base = len(merged_verts)
    
    # Material mapping for this sub-part
    part_mat = eo.data.materials[0] if eo.data.materials else None
    if part_mat:
        if part_mat.name not in mat_slot_map:
            st_obj.data.materials.append(part_mat)
            mat_slot_map[part_mat.name] = len(st_obj.data.materials) - 1
        mat_slot = mat_slot_map[part_mat.name]
    else:
        mat_slot = 0
        
    for v in eval_mesh.vertices:
        w_co = eo.matrix_world @ v.co
        l_co = inv_st_mat @ w_co
        merged_verts.append((l_co.x, l_co.y, l_co.z))
        
    for poly in eval_mesh.polygons:
        merged_polys.append([v_base + vi for vi in poly.vertices])
        merged_mat_indices.append(mat_slot)
        
    bpy.data.meshes.remove(eval_mesh)

combined_st_mesh.from_pydata(merged_verts, [], merged_polys)
for i, poly in enumerate(combined_st_mesh.polygons):
    poly.material_index = merged_mat_indices[i]

st_obj.parent = root_empty
clean_scene.collection.objects.link(st_obj)
print(f"Created merged Cockpit_SteeringWheel: {len(merged_verts)} verts, {len(merged_polys)} polys, {len(st_obj.data.materials)} materials")

# Now export all other non-steering mesh objects
for obj in orig_scene.objects:
    if obj.type != 'MESH' or obj.name in torus_names:
        continue
        
    eval_obj = obj.evaluated_get(depsgraph)
    eval_mesh = bpy.data.meshes.new_from_object(eval_obj)
    
    clean_name = obj.name.strip()
    new_obj = bpy.data.objects.new(clean_name, eval_mesh)
    new_obj.location = (0, 0, 0)
    new_obj.rotation_euler = (0, 0, 0)
    new_obj.scale = (1, 1, 1)
    
    # Apply world matrix to mesh vertices so all geometry is aligned in bus local space
    for v in eval_mesh.vertices:
        v.co = eval_obj.matrix_world @ v.co
        
    # Copy materials
    if obj.data.materials:
        for mat in obj.data.materials:
            if mat:
                new_obj.data.materials.append(mat)
    
    # Fill in fallbacks for objects with empty materials
    if len(new_obj.data.materials) == 0:
        lname = clean_name.lower()
        if "mirror big" in lname or "mirro" in lname:
            if mat_main_body: new_obj.data.materials.append(mat_main_body)
        elif "glass" in lname:
            if mat_glass: new_obj.data.materials.append(mat_glass)
        elif "light" in lname:
            if mat_white_light: new_obj.data.materials.append(mat_white_light)
        elif "metal" in lname or "support" in lname:
            if mat_metal: new_obj.data.materials.append(mat_metal)
        else:
            if mat_plastic: new_obj.data.materials.append(mat_plastic)
            
    new_obj.parent = root_empty
    clean_scene.collection.objects.link(new_obj)

print(f"Export scene ready: {len(clean_scene.objects)} objects.")

# Export clean FBX
os.makedirs(os.path.dirname(out_fbx_path), exist_ok=True)
bpy.ops.export_scene.fbx(
    filepath=out_fbx_path,
    check_existing=False,
    use_selection=False,
    global_scale=1.0,
    apply_unit_scale=True,
    apply_scale_options='FBX_SCALE_ALL',
    bake_space_transform=True,
    object_types={'EMPTY', 'MESH'},
    use_mesh_modifiers=True,
    mesh_smooth_type='FACE',
    axis_forward='-Z',
    axis_up='Y'
)

print("Export complete! File size:", os.path.getsize(out_fbx_path))
