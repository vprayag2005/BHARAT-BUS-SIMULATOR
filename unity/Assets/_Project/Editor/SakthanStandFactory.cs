using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEngine;

namespace BharatBusSim.Editor
{
    /// <summary>
    /// Factory for instantiating and configuring the realistic 3D Sakthan Thampuran Bus Stand model.
    /// Replaces the procedural stand with a high-fidelity 12-bay terminal featuring authentic
    /// Kerala architectural elements, corrugated roofs, terracotta columns, checkerboard platforms,
    /// weathered asphalt apron, parked ambient Kerala buses, and full UCX collision geometry.
    /// </summary>
    public static class SakthanStandFactory
    {
        private const string FbxPath = "Assets/_Project/Environment/SakthanStand/Sakthan_Stand.fbx";
        private const string TexturesDir = "Assets/_Project/Environment/SakthanStand/textures";
        private const string MaterialsDir = "Assets/_Project/Environment/SakthanStand/Materials";

        // Anchor position calculated to align Bus Bay slot exactly with Player Spawn (25.0, 0.35, -169.3)
        public static readonly Vector3 StandPosition = new Vector3(-8.75f, 0.21f, -154.50f);

        public static GameObject BuildRealisticSakthanStand(Transform parent)
        {
            var prefab = AssetDatabase.LoadAssetAtPath<GameObject>(FbxPath);
            if (prefab == null)
            {
                Debug.LogError($"[SakthanStandFactory] Failed to load Sakthan Stand FBX at '{FbxPath}'!");
                return null;
            }

            // Ensure Materials directory exists
            if (!AssetDatabase.IsValidFolder(MaterialsDir))
            {
                AssetDatabase.CreateFolder("Assets/_Project/Environment/SakthanStand", "Materials");
            }

            var standGo = Object.Instantiate(prefab, parent);
            standGo.name = "Landmark_Sakthan_Thampuran_Bus_Stand";
            standGo.transform.position = StandPosition;
            standGo.transform.rotation = Quaternion.identity;
            standGo.transform.localScale = Vector3.one;

            // Load or create all 21 realistic materials
            var materialMap = GetOrCreateRealisticMaterials();

            // Apply materials to all MeshRenderers
            var renderers = standGo.GetComponentsInChildren<MeshRenderer>(true);
            foreach (var r in renderers)
            {
                ApplyMaterialsToRenderer(r, materialMap);
            }

            // Configure Colliders & UCX meshes
            ConfigureColliders(standGo);

            // Clear Bay 1 for Player Bus
            ClearBayOneForPlayer(standGo);

            Debug.Log($"[SakthanStandFactory] Realistic Sakthan Thampuran Bus Stand successfully instantiated at {StandPosition} with {renderers.Length} renderers configured.");
            return standGo;
        }

        private static Dictionary<string, Material> GetOrCreateRealisticMaterials()
        {
            var dict = new Dictionary<string, Material>();
            Shader urpLitShader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");

            // Textures
            Texture2D texAsphalt = AssetDatabase.LoadAssetAtPath<Texture2D>($"{TexturesDir}/T_Asphalt_Weathered.png");
            Texture2D texTile = AssetDatabase.LoadAssetAtPath<Texture2D>($"{TexturesDir}/T_Platform_CheckerTile.png");
            Texture2D texRoof = AssetDatabase.LoadAssetAtPath<Texture2D>($"{TexturesDir}/T_Roof_Corrugated.png");
            Texture2D texColumn = AssetDatabase.LoadAssetAtPath<Texture2D>($"{TexturesDir}/T_Column_Terracotta.png");
            Texture2D texFascia = AssetDatabase.LoadAssetAtPath<Texture2D>($"{TexturesDir}/T_Fascia_SakthanOrange.png");
            Texture2D texShop = AssetDatabase.LoadAssetAtPath<Texture2D>($"{TexturesDir}/T_Shop_Signboard_Red.png");
            Texture2D texBaySlot = AssetDatabase.LoadAssetAtPath<Texture2D>($"{TexturesDir}/T_Parking_Bay_Slot.png");
            Texture2D texSignBack = AssetDatabase.LoadAssetAtPath<Texture2D>($"{TexturesDir}/T_Sign_Backing_Premium.png");
            Texture2D texBlueBus = AssetDatabase.LoadAssetAtPath<Texture2D>($"{TexturesDir}/T_KeralaBus_Blue_Livery.png");
            Texture2D texPinkBus = AssetDatabase.LoadAssetAtPath<Texture2D>($"{TexturesDir}/T_KeralaBus_Pink_Livery.png");

            // 1. MAT_Road_Asphalt & MAT_Yard_Asphalt
            var matAsphalt = GetOrCreateMaterial("MAT_Road_Asphalt", urpLitShader, Color.white, texAsphalt, 0.25f, 0.05f);
            dict["MAT_Road_Asphalt"] = matAsphalt;
            dict["MAT_Yard_Asphalt"] = matAsphalt;

            // 2. MAT_Platform_CheckerTile
            dict["MAT_Platform_CheckerTile"] = GetOrCreateMaterial("MAT_Platform_CheckerTile", urpLitShader, Color.white, texTile, 0.40f, 0.05f);

            // 3. MAT_Roof_Galvanized
            dict["MAT_Roof_Galvanized"] = GetOrCreateMaterial("MAT_Roof_Galvanized", urpLitShader, Color.white, texRoof, 0.45f, 0.25f);

            // 4. MAT_Column_Terracotta
            dict["MAT_Column_Terracotta"] = GetOrCreateMaterial("MAT_Column_Terracotta", urpLitShader, Color.white, texColumn, 0.20f, 0.05f);

            // 5. MAT_Fascia_OrangeRed
            dict["MAT_Fascia_OrangeRed"] = GetOrCreateMaterial("MAT_Fascia_OrangeRed", urpLitShader, Color.white, texFascia, 0.35f, 0.05f);

            // 6. MAT_Shop_Sign_Red
            dict["MAT_Shop_Sign_Red"] = GetOrCreateMaterial("MAT_Shop_Sign_Red", urpLitShader, Color.white, texShop, 0.35f, 0.05f);

            // 7. MAT_Parking_Bay_Slot & MAT_Sign_Backing_Premium
            dict["MAT_Parking_Bay_Slot"] = GetOrCreateMaterial("MAT_Parking_Bay_Slot", urpLitShader, Color.white, texBaySlot, 0.35f, 0.05f);
            dict["MAT_Sign_Backing_Premium"] = GetOrCreateMaterial("MAT_Sign_Backing_Premium", urpLitShader, Color.white, texSignBack, 0.40f, 0.10f);

            // 8. MAT_Sign_BrushedGold & MAT_Sign_WarmCream_Lit
            dict["MAT_Sign_BrushedGold"] = GetOrCreateMaterial("MAT_Sign_BrushedGold", urpLitShader, new Color(0.88f, 0.72f, 0.22f, 1.0f), null, 0.75f, 0.85f);
            var matLitSign = GetOrCreateMaterial("MAT_Sign_WarmCream_Lit", urpLitShader, new Color(1.0f, 0.96f, 0.88f, 1.0f), null, 0.40f, 0.0f);
            matLitSign.EnableKeyword("_EMISSION");
            matLitSign.SetColor("_EmissionColor", new Color(1.0f, 0.95f, 0.85f) * 1.5f);
            dict["MAT_Sign_WarmCream_Lit"] = matLitSign;

            // 9. MAT_KeralaBus_Blue_Livery & Pink
            dict["MAT_KeralaBus_Blue_Livery"] = GetOrCreateMaterial("MAT_KeralaBus_Blue_Livery", urpLitShader, Color.white, texBlueBus, 0.65f, 0.15f);
            dict["MAT_KeralaBus_Pink_Livery"] = GetOrCreateMaterial("MAT_KeralaBus_Pink_Livery", urpLitShader, Color.white, texPinkBus, 0.65f, 0.15f);

            // 10. Concrete, Plaster, Masonry
            dict["MAT_Concrete_Grey"] = GetOrCreateMaterial("MAT_Concrete_Grey", urpLitShader, new Color(0.72f, 0.72f, 0.74f, 1.0f), null, 0.25f, 0.0f);
            var matPlaster = GetOrCreateMaterial("MAT_Cream_Plaster", urpLitShader, new Color(0.92f, 0.90f, 0.84f, 1.0f), null, 0.20f, 0.0f);
            dict["MAT_Cream_Plaster"] = matPlaster;
            dict["MAT_Boundary_Wall_Masonry"] = matPlaster;

            // 11. MAT_Laterite_Earth
            dict["MAT_Laterite_Earth"] = GetOrCreateMaterial("MAT_Laterite_Earth", urpLitShader, new Color(0.55f, 0.28f, 0.18f, 1.0f), null, 0.10f, 0.0f);

            // 12. MAT_Truss_DarkSteel
            dict["MAT_Truss_DarkSteel"] = GetOrCreateMaterial("MAT_Truss_DarkSteel", urpLitShader, new Color(0.20f, 0.22f, 0.24f, 1.0f), null, 0.50f, 0.60f);

            // 13. MAT_White_Paint & MAT_Yellow_Paint
            dict["MAT_White_Paint"] = GetOrCreateMaterial("MAT_White_Paint", urpLitShader, new Color(0.95f, 0.95f, 0.95f, 1.0f), null, 0.30f, 0.0f);
            var matYellow = GetOrCreateMaterial("MAT_Yellow_Marking", urpLitShader, new Color(0.95f, 0.82f, 0.15f, 1.0f), null, 0.30f, 0.0f);
            dict["MAT_Yellow_Marking"] = matYellow;
            dict["MAT_Yellow_Paint"] = matYellow;

            // 15. MAT_Bus_Windshield
            var matWindshield = GetOrCreateMaterial("MAT_Bus_Windshield", urpLitShader, new Color(0.15f, 0.25f, 0.35f, 0.95f), null, 0.90f, 0.10f);
            dict["MAT_Bus_Windshield"] = matWindshield;

            // 16. MAT_Bus_Tire_Rubber
            dict["MAT_Bus_Tire_Rubber"] = GetOrCreateMaterial("MAT_Bus_Tire_Rubber", urpLitShader, new Color(0.12f, 0.12f, 0.12f, 1.0f), null, 0.15f, 0.0f);

            // 17. MAT_Signboard_Blue
            dict["MAT_Signboard_Blue"] = GetOrCreateMaterial("MAT_Signboard_Blue", urpLitShader, new Color(0.08f, 0.32f, 0.72f, 1.0f), null, 0.35f, 0.05f);

            // 18. MAT_Shop_Stall_Cyan
            dict["MAT_Shop_Stall_Cyan"] = GetOrCreateMaterial("MAT_Shop_Stall_Cyan", urpLitShader, new Color(0.15f, 0.55f, 0.65f, 1.0f), null, 0.30f, 0.05f);

            // 19. MAT_Streetlight_Glow
            var matGlow = GetOrCreateMaterial("MAT_Streetlight_Glow", urpLitShader, new Color(1.0f, 0.92f, 0.65f, 1.0f), null, 0.50f, 0.0f);
            matGlow.EnableKeyword("_EMISSION");
            matGlow.SetColor("_EmissionColor", new Color(1.0f, 0.88f, 0.50f) * 2.0f);
            dict["MAT_Streetlight_Glow"] = matGlow;

            // 20. MAT_Palm_Trunk
            dict["MAT_Palm_Trunk"] = GetOrCreateMaterial("MAT_Palm_Trunk", urpLitShader, new Color(0.38f, 0.28f, 0.20f, 1.0f), null, 0.10f, 0.0f);

            // 21. MAT_Palm_Green
            dict["MAT_Palm_Green"] = GetOrCreateMaterial("MAT_Palm_Green", urpLitShader, new Color(0.15f, 0.45f, 0.12f, 1.0f), null, 0.20f, 0.0f);

            return dict;
        }

        private static Material GetOrCreateMaterial(string matName, Shader shader, Color color, Texture2D tex, float smoothness, float metallic)
        {
            string path = $"{MaterialsDir}/{matName}.mat";
            var mat = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (mat == null)
            {
                mat = new Material(shader) { name = matName };
                AssetDatabase.CreateAsset(mat, path);
            }

            mat.color = color;
            if (tex != null)
            {
                mat.mainTexture = tex;
                if (mat.HasProperty("_BaseMap")) mat.SetTexture("_BaseMap", tex);
            }
            if (mat.HasProperty("_Smoothness")) mat.SetFloat("_Smoothness", smoothness);
            if (mat.HasProperty("_Metallic")) mat.SetFloat("_Metallic", metallic);

            EditorUtility.SetDirty(mat);
            return mat;
        }

        private static void ApplyMaterialsToRenderer(MeshRenderer mr, Dictionary<string, Material> map)
        {
            if (mr == null) return;
            var currentMats = mr.sharedMaterials;
            var newMats = new Material[currentMats.Length];
            bool modified = false;

            for (int i = 0; i < currentMats.Length; i++)
            {
                var cur = currentMats[i];
                string curName = cur != null ? cur.name : "";

                // Strip "(Instance)" if present
                if (curName.EndsWith(" (Instance)"))
                    curName = curName.Substring(0, curName.Length - 11);

                if (map.TryGetValue(curName, out var foundMat))
                {
                    newMats[i] = foundMat;
                    modified = true;
                }
                else
                {
                    // Fallback matching by object name heuristics
                    newMats[i] = MatchFallbackMaterial(mr.gameObject.name, map, cur);
                    if (newMats[i] != cur) modified = true;
                }
            }

            if (modified)
            {
                mr.sharedMaterials = newMats;
            }
        }

        private static Material MatchFallbackMaterial(string objName, Dictionary<string, Material> map, Material defaultMat)
        {
            string lower = objName.ToLowerInvariant();
            if (lower.Contains("asphalt") || lower.Contains("road") || lower.Contains("apron"))
                return map["MAT_Road_Asphalt"];
            if (lower.Contains("platform") || lower.Contains("checker") || lower.Contains("kerb"))
                return map["MAT_Platform_CheckerTile"];
            if (lower.Contains("roof") || lower.Contains("canopy") || lower.Contains("sheet"))
                return map["MAT_Roof_Galvanized"];
            if (lower.Contains("column") || lower.Contains("pillar"))
                return map["MAT_Column_Terracotta"];
            if (lower.Contains("fascia") || lower.Contains("orange") || lower.Contains("arch"))
                return map["MAT_Fascia_OrangeRed"];
            if (lower.Contains("sign") || lower.Contains("board"))
                return map["MAT_Shop_Sign_Red"];
            if (lower.Contains("blue"))
                return map["MAT_KeralaBus_Blue_Livery"];
            if (lower.Contains("pink"))
                return map["MAT_KeralaBus_Pink_Livery"];
            if (lower.Contains("windshield") || lower.Contains("glass"))
                return map["MAT_Bus_Windshield"];
            if (lower.Contains("tire") || lower.Contains("wheel"))
                return map["MAT_Bus_Tire_Rubber"];
            if (lower.Contains("truss") || lower.Contains("steel"))
                return map["MAT_Truss_DarkSteel"];
            if (lower.Contains("wall") || lower.Contains("booth") || lower.Contains("building"))
                return map["MAT_Cream_Plaster"];
            if (lower.Contains("palm") || lower.Contains("leaf") || lower.Contains("tree"))
                return map["MAT_Palm_Green"];

            return defaultMat ?? map["MAT_Concrete_Grey"];
        }

        private static void ConfigureColliders(GameObject standGo)
        {
            // 1. UCX Colliders (Unreal/Blender precision collision hulls)
            foreach (Transform t in standGo.GetComponentsInChildren<Transform>(true))
            {
                if (t.name.StartsWith("UCX_"))
                {
                    // UCX meshes are invisible collision hulls: disable MeshRenderer
                    var mr = t.GetComponent<MeshRenderer>();
                    if (mr != null) mr.enabled = false;

                    // Boundary walls must not have colliders to keep adjacent road network corridors (e.g. EDGE_SAKTHAN_TO_OLLUR) unobstructed
                    if (t.name.Contains("Wall"))
                    {
                        var col = t.GetComponent<Collider>();
                        if (col != null) Object.DestroyImmediate(col);
                        continue;
                    }

                    var mf = t.GetComponent<MeshFilter>();
                    if (mf != null && mf.sharedMesh != null)
                    {
                        var mc = t.GetComponent<MeshCollider>();
                        if (mc == null) mc = t.gameObject.AddComponent<MeshCollider>();
                        mc.sharedMesh = mf.sharedMesh;

                        // Pillars use convex collision for fast robust solver checks; large ground/platform use non-convex
                        if (t.name.Contains("Pillar") || t.name.Contains("Column"))
                        {
                            mc.convex = true;
                        }
                        else
                        {
                            mc.convex = false;
                        }
                    }
                }
            }

            // 2. Boundary_Wall_South: Deactivate so the exit corridor directly toward Swaraj Round is 100% unobstructed
            var southCenterWall = standGo.transform.Find("Boundary_Wall_South_Center") ?? standGo.transform.Find("Boundary_Wall_SouthCenter");
            if (southCenterWall != null)
            {
                southCenterWall.gameObject.SetActive(false);
            }

            // Note: Visual perimeter walls (East, West, North) do not receive colliders so they do not overlap
            // the adjacent road network corridors (e.g. EDGE_SAKTHAN_TO_OLLUR). Physical boundaries are
            // defined accurately by the UCX colliders (pillars, columns, and platforms).

            // 4. Ensure no visual canopy roofs or platform overheads have colliders that could block chase camera line of sight
            foreach (Transform t in standGo.GetComponentsInChildren<Transform>(true))
            {
                if (t.name.Contains("Roof") || t.name.Contains("Canopy") || t.name.Contains("Sheet") || t.name.Contains("Fascia"))
                {
                    var col = t.GetComponent<Collider>();
                    if (col != null)
                    {
                        Object.DestroyImmediate(col);
                    }
                }
            }
        }

        private static void ClearBayOneForPlayer(GameObject standGo)
        {
            // Bay 1 is reserved for the Player Bus Spawn at (25.0, 0.35, -169.3).
            // Disable or destroy the placeholder bus and windshield occupying Bay 1.
            var bus1 = standGo.transform.Find("Bus_Placeholder_01");
            if (bus1 != null)
            {
                bus1.gameObject.SetActive(false);
            }

            var win1 = standGo.transform.Find("Bus_Windshield_01");
            if (win1 != null)
            {
                win1.gameObject.SetActive(false);
            }

            // Ensure ambient parked buses have proper liveries
            // Bays 3, 8, 11 -> Blue Kerala Livery; Bays 5, 10, 12 -> Pink Kerala Livery
            string[] blueBuses = new[] { "Bus_Placeholder_03", "Bus_Placeholder_08", "Bus_Placeholder_11" };
            string[] pinkBuses = new[] { "Bus_Placeholder_05", "Bus_Placeholder_10", "Bus_Placeholder_12" };

            var blueMat = AssetDatabase.LoadAssetAtPath<Material>($"{MaterialsDir}/MAT_KeralaBus_Blue_Livery.mat");
            var pinkMat = AssetDatabase.LoadAssetAtPath<Material>($"{MaterialsDir}/MAT_KeralaBus_Pink_Livery.mat");

            foreach (var bName in blueBuses)
            {
                var bus = standGo.transform.Find(bName);
                if (bus != null && blueMat != null)
                {
                    var mr = bus.GetComponent<MeshRenderer>();
                    if (mr != null) mr.sharedMaterial = blueMat;
                }
            }

            foreach (var bName in pinkBuses)
            {
                var bus = standGo.transform.Find(bName);
                if (bus != null && pinkMat != null)
                {
                    var mr = bus.GetComponent<MeshRenderer>();
                    if (mr != null) mr.sharedMaterial = pinkMat;
                }
            }
        }
    }
}
