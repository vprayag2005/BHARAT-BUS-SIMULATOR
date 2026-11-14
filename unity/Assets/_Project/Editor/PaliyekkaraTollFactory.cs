using System.Collections.Generic;
using UnityEditor;
using UnityEngine;
using BharatBusSim.Toll;

namespace BharatBusSim.Editor
{
    /// <summary>
    /// Factory for instantiating and configuring the realistic 6-lane Paliyekkara Toll Plaza on NH 544.
    /// Features 6 operational FASTag lanes, Kerala traditional clay-tiled canopy roofs,
    /// concrete barrier islands, illuminated traffic signals, and 6 synchronized automated boom barriers.
    /// </summary>
    public static class PaliyekkaraTollFactory
    {
        public const string FbxPath = "Assets/_Project/Environment/PaliyekkaraToll/Paliyekkara_Toll_Plaza.fbx";
        public const string MaterialsDir = "Assets/_Project/Environment/PaliyekkaraToll/Materials";
        public static readonly Vector3 TollPlazaPosition = new Vector3(998.2f, 0f, -1805.2f);
        public static readonly Quaternion TollPlazaRotation = Quaternion.Euler(0f, 180f, 0f);

        public static GameObject Build6LaneTollPlaza(Transform parent, Material redMat, Material greenMat, Material concreteMat)
        {
            var prefab = AssetDatabase.LoadAssetAtPath<GameObject>(FbxPath);
            if (prefab == null)
            {
                Debug.LogError($"[PaliyekkaraTollFactory] Failed to load Toll Plaza FBX at '{FbxPath}'!");
                return null;
            }

            if (!AssetDatabase.IsValidFolder(MaterialsDir))
            {
                if (!AssetDatabase.IsValidFolder("Assets/_Project/Environment/PaliyekkaraToll"))
                {
                    AssetDatabase.CreateFolder("Assets/_Project/Environment", "PaliyekkaraToll");
                }
                AssetDatabase.CreateFolder("Assets/_Project/Environment/PaliyekkaraToll", "Materials");
            }

            var tollGo = Object.Instantiate(prefab, parent);
            tollGo.name = "Landmark_Paliyekkara_Toll_Plaza";
            tollGo.transform.position = TollPlazaPosition;
            tollGo.transform.rotation = TollPlazaRotation;
            tollGo.transform.localScale = Vector3.one;

            var matMap = GetOrCreateTollMaterials(redMat, greenMat, concreteMat);

            // Apply materials to all MeshRenderers
            var renderers = tollGo.GetComponentsInChildren<MeshRenderer>(true);
            foreach (var r in renderers)
            {
                ApplyMaterials(r, matMap);
            }

            // Configure Colliders
            ConfigureColliders(tollGo);

            Debug.Log($"[PaliyekkaraTollFactory] 6-Lane Paliyekkara Toll Plaza successfully instantiated at {TollPlazaPosition}.");
            return tollGo;
        }

        private static Dictionary<string, Material> GetOrCreateTollMaterials(Material redMat, Material greenMat, Material concreteMat)
        {
            var dict = new Dictionary<string, Material>();
            Shader urpLitShader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");

            Texture2D texAsphalt = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Textures/Tex_Asphalt_PBR.png");
            Texture2D texTile = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Textures/Tex_Kerala_ClayTile.png");
            Texture2D texKerb = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Textures/Tex_Kerb_YellowBlack.png");
            Texture2D texConcrete = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Textures/Tex_Concrete_Terminal.png");

            dict["M_Asphalt"] = GetOrCreateMaterial("MAT_Toll_Asphalt", urpLitShader, Color.white, texAsphalt, 0.25f, 0.05f);
            dict["M_Concrete"] = concreteMat ?? GetOrCreateMaterial("MAT_Toll_Concrete", urpLitShader, Color.white, texConcrete, 0.30f, 0.05f);
            dict["M_RoofTile"] = GetOrCreateMaterial("MAT_Toll_RoofTile", urpLitShader, Color.white, texTile, 0.40f, 0.05f);
            dict["M_KerbYB"] = GetOrCreateMaterial("MAT_Toll_KerbYB", urpLitShader, Color.white, texKerb, 0.30f, 0.05f);

            dict["M_BoothPaint"] = GetOrCreateMaterial("MAT_Toll_BoothPaint", urpLitShader, new Color(0.85f, 0.82f, 0.72f), null, 0.25f, 0.0f);
            dict["M_Glass"] = GetOrCreateMaterial("MAT_Toll_Glass", urpLitShader, new Color(0.60f, 0.80f, 0.90f, 0.65f), null, 0.90f, 0.10f);
            dict["M_Red"] = redMat ?? GetOrCreateMaterial("MAT_Toll_Red", urpLitShader, new Color(0.85f, 0.10f, 0.10f), null, 0.35f, 0.0f);
            dict["M_RoadPaint"] = GetOrCreateMaterial("MAT_Toll_RoadPaint", urpLitShader, new Color(0.92f, 0.92f, 0.88f), null, 0.30f, 0.0f);
            dict["M_SignGreen"] = greenMat ?? GetOrCreateMaterial("MAT_Toll_SignGreen", urpLitShader, new Color(0.0f, 0.40f, 0.18f), null, 0.40f, 0.05f);
            dict["M_SteelDark"] = GetOrCreateMaterial("MAT_Toll_SteelDark", urpLitShader, new Color(0.18f, 0.18f, 0.20f), null, 0.60f, 0.70f);
            dict["M_White"] = GetOrCreateMaterial("MAT_Toll_White", urpLitShader, new Color(0.92f, 0.92f, 0.92f), null, 0.30f, 0.0f);
            dict["M_Yellow"] = GetOrCreateMaterial("MAT_Toll_Yellow", urpLitShader, new Color(0.95f, 0.75f, 0.10f), null, 0.30f, 0.0f);

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

        private static void ConfigureColliders(GameObject tollGo)
        {
            // 1. Road Main Mesh Collider (38m wide road deck)
            var roadMain = tollGo.transform.Find("Road_Main");
            if (roadMain != null)
            {
                var mc = roadMain.GetComponent<MeshCollider>();
                if (mc == null) mc = roadMain.gameObject.AddComponent<MeshCollider>();
                var mf = roadMain.GetComponent<MeshFilter>();
                if (mf != null) mc.sharedMesh = mf.sharedMesh;
            }

            // 2. Strip colliders from high roofs and gantries to protect chase camera line of sight
            foreach (Transform t in tollGo.GetComponentsInChildren<Transform>(true))
            {
                string n = t.name.ToLowerInvariant();
                if (n.Contains("roof") || n.Contains("canopy") || n.Contains("sign") || n.Contains("arrow"))
                {
                    var col = t.GetComponent<Collider>();
                    if (col != null) Object.DestroyImmediate(col);
                }
            }
        }
    }
}
