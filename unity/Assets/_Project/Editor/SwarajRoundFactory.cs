using System.Collections.Generic;
using UnityEditor;
using UnityEngine;

namespace BharatBusSim.Editor
{
    /// <summary>
    /// Factory for instantiating and configuring the realistic Swaraj Round and Vadakkumnathan Temple.
    /// Features a 612m diameter, 16m wide 3-lane circular carriageway, the historic Thekkinkadu Maidan,
    /// Kerala multi-tiered pagoda temple sanctum with golden stupikas, perimeter shop buildings,
    /// and radial approach roads (MG Road, MO Road, Shornur Road, and Thrissur-Palakkad Road).
    /// </summary>
    public static class SwarajRoundFactory
    {
        public const string FbxPath = "Assets/_Project/Environment/SwarajRound/Thrissur_Swaraj_Round.fbx";
        public const string MaterialsDir = "Assets/_Project/Environment/SwarajRound/Materials";
        public static readonly Vector3 SwarajPosition = new Vector3(0f, 0f, 14f);

        public static GameObject BuildSwarajRound(Transform parent, Material goldMat, Material tileMat)
        {
            var prefab = AssetDatabase.LoadAssetAtPath<GameObject>(FbxPath);
            if (prefab == null)
            {
                Debug.LogError($"[SwarajRoundFactory] Failed to load Swaraj Round FBX at '{FbxPath}'!");
                return null;
            }

            if (!AssetDatabase.IsValidFolder(MaterialsDir))
            {
                if (!AssetDatabase.IsValidFolder("Assets/_Project/Environment/SwarajRound"))
                {
                    AssetDatabase.CreateFolder("Assets/_Project/Environment", "SwarajRound");
                }
                AssetDatabase.CreateFolder("Assets/_Project/Environment/SwarajRound", "Materials");
            }

            var roundGo = Object.Instantiate(prefab, parent);
            roundGo.name = "Landmark_Thekkinkadu_Maidan";
            roundGo.transform.position = SwarajPosition;
            roundGo.transform.rotation = Quaternion.identity;
            roundGo.transform.localScale = Vector3.one;

            var matMap = GetOrCreateSwarajMaterials(goldMat, tileMat);

            var renderers = roundGo.GetComponentsInChildren<MeshRenderer>(true);
            foreach (var r in renderers)
            {
                ApplyMaterials(r, matMap);
            }

            ConfigureColliders(roundGo);
            ThrissurBlenderRoadBuilder.RemoveAllTreesInScene();

            Debug.Log($"[SwarajRoundFactory] Swaraj Round and Vadakkumnathan Temple successfully instantiated at {SwarajPosition}.");
            return roundGo;
        }

        private static Dictionary<string, Material> GetOrCreateSwarajMaterials(Material goldMat, Material tileMat)
        {
            var dict = new Dictionary<string, Material>();
            Shader urpLitShader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");

            Texture2D texAsphalt = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Textures/Tex_Asphalt_PBR.png");
            Texture2D texGrass = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Textures/Tex_Tropical_Grass.png");
            Texture2D texLaterite = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Textures/Tex_Laterite_Soil.png");
            Texture2D texTile = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Textures/Tex_Kerala_ClayTile.png");
            Texture2D texConcrete = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Textures/Tex_Concrete_Terminal.png");
            Texture2D texPalmBark = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Textures/Tex_Palm_Bark.png");

            dict["M_Asphalt"] = GetOrCreateMaterial("MAT_Swaraj_Asphalt", urpLitShader, Color.white, texAsphalt, 0.25f, 0.05f);
            dict["M_Grass"] = GetOrCreateMaterial("MAT_Swaraj_Grass", urpLitShader, Color.white, texGrass, 0.15f, 0.0f);
            dict["M_Laterite"] = GetOrCreateMaterial("MAT_Swaraj_Laterite", urpLitShader, Color.white, texLaterite, 0.10f, 0.0f);
            dict["M_Tile"] = tileMat ?? GetOrCreateMaterial("MAT_Swaraj_Tile", urpLitShader, Color.white, texTile, 0.35f, 0.05f);
            dict["M_Concrete"] = GetOrCreateMaterial("MAT_Swaraj_Concrete", urpLitShader, Color.white, texConcrete, 0.25f, 0.05f);

            dict["M_BldA"] = GetOrCreateMaterial("MAT_Swaraj_BldA", urpLitShader, new Color(0.78f, 0.72f, 0.60f), null, 0.20f, 0.0f);
            dict["M_BldB"] = GetOrCreateMaterial("MAT_Swaraj_BldB", urpLitShader, new Color(0.62f, 0.68f, 0.72f), null, 0.20f, 0.0f);
            dict["M_BldC"] = GetOrCreateMaterial("MAT_Swaraj_BldC", urpLitShader, new Color(0.80f, 0.55f, 0.45f), null, 0.20f, 0.0f);
            dict["M_BldD"] = GetOrCreateMaterial("MAT_Swaraj_BldD", urpLitShader, new Color(0.85f, 0.85f, 0.80f), null, 0.20f, 0.0f);

            dict["M_CopperRoof"] = GetOrCreateMaterial("MAT_Swaraj_CopperRoof", urpLitShader, new Color(0.18f, 0.42f, 0.35f), null, 0.45f, 0.30f);
            dict["M_Glass"] = GetOrCreateMaterial("MAT_Swaraj_Glass", urpLitShader, new Color(0.15f, 0.25f, 0.35f, 0.85f), null, 0.90f, 0.10f);
            dict["M_Gold"] = goldMat ?? GetOrCreateMaterial("MAT_Swaraj_Gold", urpLitShader, new Color(0.85f, 0.70f, 0.22f), null, 0.80f, 0.85f);
            dict["M_Leaf"] = GetOrCreateMaterial("MAT_Swaraj_Leaf", urpLitShader, new Color(0.10f, 0.35f, 0.08f), null, 0.15f, 0.0f);
            dict["M_Plaster"] = GetOrCreateMaterial("MAT_Swaraj_Plaster", urpLitShader, new Color(0.92f, 0.90f, 0.85f), null, 0.20f, 0.0f);
            dict["M_RoadPaint"] = GetOrCreateMaterial("MAT_Swaraj_RoadPaint", urpLitShader, new Color(0.92f, 0.92f, 0.88f), null, 0.30f, 0.0f);
            dict["M_SignGreen"] = GetOrCreateMaterial("MAT_Swaraj_SignGreen", urpLitShader, new Color(0.0f, 0.42f, 0.18f), null, 0.35f, 0.05f);
            dict["M_Steel"] = GetOrCreateMaterial("MAT_Swaraj_Steel", urpLitShader, new Color(0.18f, 0.18f, 0.20f), null, 0.50f, 0.70f);
            dict["M_Trunk"] = GetOrCreateMaterial("MAT_Swaraj_Trunk", urpLitShader, Color.white, texPalmBark, 0.10f, 0.0f);
            dict["M_White"] = GetOrCreateMaterial("MAT_Swaraj_White", urpLitShader, new Color(0.92f, 0.92f, 0.90f), null, 0.30f, 0.0f);
            dict["M_Yellow"] = GetOrCreateMaterial("MAT_Swaraj_Yellow", urpLitShader, new Color(0.95f, 0.75f, 0.10f), null, 0.30f, 0.0f);

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

        private static void ApplyMaterials(MeshRenderer mr, Dictionary<string, Material> map)
        {
            if (mr == null) return;
            var mats = mr.sharedMaterials;
            bool changed = false;
            for (int i = 0; i < mats.Length; i++)
            {
                string curName = mats[i] != null ? mats[i].name : "";
                if (curName.EndsWith(" (Instance)"))
                    curName = curName.Substring(0, curName.Length - 11);

                if (map.TryGetValue(curName, out var found))
                {
                    mats[i] = found;
                    changed = true;
                }
            }
            if (changed) mr.sharedMaterials = mats;
        }

        private static void ConfigureColliders(GameObject roundGo)
        {
            // 1. Solid driveable road ring, radial roads, and maidan
            string[] solidMeshNames = new[]
            {
                "Road_Ring_Asphalt",
                "Roads_Radial",
                "Maidan_Grass",
                "Maidan_Paths",
                "Pavement_Inner",
                "Pavement_Outer",
                "Temple_Wall",
                "Temple_Walls_Inner",
                "Buildings_b1",
                "Buildings_b2",
                "Buildings_b3",
                "Buildings_b4"
            };

            foreach (var n in solidMeshNames)
            {
                var t = roundGo.transform.Find(n);
                if (t != null)
                {
                    var mc = t.GetComponent<MeshCollider>();
                    if (mc == null) mc = t.gameObject.AddComponent<MeshCollider>();
                    var mf = t.GetComponent<MeshFilter>();
                    if (mf != null) mc.sharedMesh = mf.sharedMesh;
                }
            }

            // 2. Ensure high canopy roofs and tree crowns do not have colliders that could clip chase camera
            foreach (Transform t in roundGo.GetComponentsInChildren<Transform>(true))
            {
                string lower = t.name.ToLowerInvariant();
                if (lower.Contains("crown") || lower.Contains("gold") || lower.Contains("roof") || lower.Contains("parapet") || lower.Contains("glazing") || lower.Contains("lamp_head"))
                {
                    var col = t.GetComponent<Collider>();
                    if (col != null) Object.DestroyImmediate(col);
                }
            }
        }
    }
}
