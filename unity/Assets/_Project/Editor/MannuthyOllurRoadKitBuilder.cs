using System;
using System.IO;
using System.Collections.Generic;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace BharatBusSim.Editor
{
    /// <summary>
    /// Builds the realistic Mannuthy-Ollur road corridor in 03_Thrissur_RealisticWorld.unity
    /// using modular road kit models from Assets/_Project/Models/RoadKits.
    /// </summary>
    public static class MannuthyOllurRoadKitBuilder
    {
        public const string FbxPath = "Assets/_Project/Models/RoadKits/BharatRoadKit/Bharat_Road_Kit_CityV2_Colors.fbx";
        public const string ScenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
        public const string MaterialsDir = "Assets/_Project/Models/RoadKits/BharatRoadKit/Materials";
        public const string JunctionFbxPath = "Assets/_Project/Models/RoadKits/RoadJunction/Road+Junction.fbx";

        public static readonly Vector3 MannuthyJunctionAnchor = new Vector3(1352.0f, 1.30f, -472.0f);
        public static readonly Vector3 OllurGoreAnchor = new Vector3(2178.0f, 1.10f, -1481.0f);

        [MenuItem("BharatBusSim/RoadKit/1. Setup RoadKit Materials")]
        public static void SetupMaterials()
        {
            if (!Directory.Exists(MaterialsDir))
            {
                Directory.CreateDirectory(MaterialsDir);
                AssetDatabase.Refresh();
            }

            Shader litShader = Shader.Find("Universal Render Pipeline/Lit");
            if (litShader == null) litShader = Shader.Find("Standard");

            Texture2D asphaltTex = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Models/RoadKits/HighwayKit/Textures/local_Asphalt.png");
            Texture2D pavementTex = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Models/RoadKits/HighwayKit/Textures/local_Pavement.png");
            Texture2D grassTex = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Models/RoadKits/HighwayKit/Textures/TCom_Moss0050_1_seamless_S.png");

            CreateOrUpdateMaterial("M_CityKit_Asphalt", litShader, asphaltTex, new Color(0.24f, 0.24f, 0.25f), 0.25f, new Vector2(2f, 2f));
            CreateOrUpdateMaterial("M_CityKit_Curb", litShader, null, new Color(0.78f, 0.78f, 0.75f), 0.15f, Vector2.one);
            CreateOrUpdateMaterial("M_CityKit_YellowLine", litShader, null, new Color(0.96f, 0.78f, 0.05f), 0.35f, Vector2.one);
            CreateOrUpdateMaterial("M_CityKit_WhiteLine", litShader, null, new Color(0.95f, 0.95f, 0.95f), 0.35f, Vector2.one);
            CreateOrUpdateMaterial("M_CityKit_Sidewalk", litShader, pavementTex, new Color(0.85f, 0.85f, 0.82f), 0.20f, new Vector2(4f, 4f));
            CreateOrUpdateMaterial("M_CityKit_Verge", litShader, grassTex, new Color(0.45f, 0.58f, 0.35f), 0.10f, new Vector2(6f, 6f));
            CreateOrUpdateMaterial("M_CityKit_SlabBase", litShader, null, new Color(0.40f, 0.40f, 0.42f), 0.10f, Vector2.one);

            AssetDatabase.SaveAssets();
            Debug.Log("[MannuthyOllurRoadKitBuilder] All BharatRoadKit materials setup successfully.");
        }

        private static Material CreateOrUpdateMaterial(string matName, Shader shader, Texture2D texture, Color color, float smoothness, Vector2 scale)
        {
            string path = $"{MaterialsDir}/{matName}.mat";
            Material mat = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (mat == null)
            {
                mat = new Material(shader) { name = matName };
                AssetDatabase.CreateAsset(mat, path);
            }
            else
            {
                mat.shader = shader;
            }

            if (texture != null)
            {
                mat.mainTexture = texture;
                mat.mainTextureScale = scale;
            }
            mat.color = color;
            if (mat.HasProperty("_Smoothness")) mat.SetFloat("_Smoothness", smoothness);
            EditorUtility.SetDirty(mat);
            return mat;
        }

        public static Dictionary<string, Material> LoadMaterials()
        {
            var dict = new Dictionary<string, Material>();
            string[] names = { "M_CityKit_Asphalt", "M_CityKit_Curb", "M_CityKit_YellowLine", "M_CityKit_WhiteLine", "M_CityKit_Sidewalk", "M_CityKit_Verge", "M_CityKit_SlabBase" };
            foreach (var n in names)
            {
                var m = AssetDatabase.LoadAssetAtPath<Material>($"{MaterialsDir}/{n}.mat");
                if (m != null) dict[n] = m;
            }
            return dict;
        }

        public static Dictionary<string, Mesh> LoadSubMeshes()
        {
            var dict = new Dictionary<string, Mesh>();
            var allAssets = AssetDatabase.LoadAllAssetsAtPath(FbxPath);
            foreach (var a in allAssets)
            {
                if (a is Mesh mesh && !dict.ContainsKey(mesh.name))
                {
                    dict[mesh.name] = mesh;
                }
            }
            return dict;
        }

        public static GameObject LoadStreetLightMesh()
        {
            var allAssets = AssetDatabase.LoadAllAssetsAtPath(JunctionFbxPath);
            foreach (var a in allAssets)
            {
                if (a is GameObject go && go.name.StartsWith("Street light", StringComparison.OrdinalIgnoreCase))
                {
                    return go;
                }
            }
            return null;
        }

        [MenuItem("BharatBusSim/RoadKit/3. Build Mannuthy-Ollur Corridor")]
        public static void BuildCorridor()
        {
            Debug.Log("[MannuthyOllurRoadKitBuilder] Starting assembly of Mannuthy-Ollur Corridor...");
            SetupMaterials();

            var scene = EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
            if (!scene.IsValid())
            {
                Debug.LogError($"[RoadKitBuilder] Failed to open scene at {ScenePath}");
                return;
            }

            var meshes = LoadSubMeshes();
            var materials = LoadMaterials();
            var streetLightPrefab = LoadStreetLightMesh();

            // Setup High-Friction PhysicsMaterial for Road
            string physMatPath = "Assets/_Project/Models/RoadKits/PhysicsMat_RoadAsphalt.physicsMaterial";
            PhysicsMaterial roadPhysMat = AssetDatabase.LoadAssetAtPath<PhysicsMaterial>(physMatPath);
            if (roadPhysMat == null)
            {
                roadPhysMat = new PhysicsMaterial("PhysicsMat_RoadAsphalt")
                {
                    dynamicFriction = 0.90f,
                    staticFriction = 0.90f,
                    bounciness = 0.0f,
                    frictionCombine = PhysicsMaterialCombine.Average,
                    bounceCombine = PhysicsMaterialCombine.Minimum
                };
                AssetDatabase.CreateAsset(roadPhysMat, physMatPath);
            }

            // Find or create Corridor Root
            const string RootName = "Mannuthy_Ollur_RoadKit_Corridor";
            var oldRoot = GameObject.Find(RootName);
            if (oldRoot != null)
            {
                Debug.Log("[RoadKitBuilder] Removing existing corridor to perform fresh rebuild.");
                UnityEngine.Object.DestroyImmediate(oldRoot);
            }

            // Deactivate legacy conflicting corridor if present
            var legacy = GameObject.Find("Mannuthy_Ollur_Mapped_Complete");
            if (legacy != null)
            {
                legacy.SetActive(false);
                Debug.Log("[RoadKitBuilder] Deactivated legacy Mannuthy_Ollur_Mapped_Complete to avoid geometry z-fighting.");
            }

            var corridorRoot = new GameObject(RootName);
            var highwayRoot = new GameObject("NH544_4Lane_DualCarriageway");
            highwayRoot.transform.SetParent(corridorRoot.transform, false);

            var junctionsRoot = new GameObject("Junctions_And_Transitions");
            junctionsRoot.transform.SetParent(corridorRoot.transform, false);

            var furnitureRoot = new GameObject("Street_Lighting_And_Furniture");
            furnitureRoot.transform.SetParent(corridorRoot.transform, false);

            // ------------------------------------------------------------------------------------
            // 1. MANNUTHY 4-WAY CROSS JUNCTION
            // ------------------------------------------------------------------------------------
            // In imported FBX (bakeAxisConversion=1), local Z is UP, local Y is Forward, local X is Right.
            // When rotated Quaternion.Euler(-90, yaw, 0):
            // local Z -> world UP
            // local Y -> world Forward
            // local X -> world Right
            Vector3 juncPos = MannuthyJunctionAnchor;
            float highwayYaw = 140.7f; // Direction from Mannuthy (1352, -472) to Ollur (2178, -1481)
            Quaternion juncRot = Quaternion.Euler(-90f, highwayYaw, 0f);

            var juncGo = InstantiatePiece("Mannuthy_4Way_Cross_Junction", "Junction_4Way_Cross",
                juncPos, juncRot, junctionsRoot.transform, meshes, materials, roadPhysMat);

            // ------------------------------------------------------------------------------------
            // 2. NH 544 4-LANE DUAL CARRIAGEWAY CHUNKS
            // ------------------------------------------------------------------------------------
            // Mannuthy 4-way cross arm length is 17.10m from center.
            // Highway starts at juncPos + forward * 17.10m
            Vector3 forward = new Vector3(Mathf.Sin(highwayYaw * Mathf.Deg2Rad), 0f, Mathf.Cos(highwayYaw * Mathf.Deg2Rad)).normalized;
            Vector3 startPos = juncPos + forward * 17.10f;
            Vector3 endGore = OllurGoreAnchor;

            // Generate authentic curve alignment between Mannuthy and Ollur
            // Total distance along corridor is approx 1270 m.
            // 22.8m straight piece * 54 pieces = ~1231.2m
            int numPieces = 54;
            Vector3 currentPos = startPos;
            float totalLen = Vector3.Distance(startPos, endGore);

            // Gentle realistic S-curve inflection mimicking NH 544 Thrissur bypass
            List<Vector3> placedTilePositions = new List<Vector3>();

            for (int i = 0; i < numPieces; i++)
            {
                float t = (float)i / (numPieces - 1);
                // Elevation smooth lerp from 1.30m at Mannuthy to 1.10m at Ollur
                float currentElev = Mathf.Lerp(MannuthyJunctionAnchor.y, OllurGoreAnchor.y, t);

                // Gentle curve heading adjustment along the corridor:
                // Begins at 141 deg, gently swings to 148 deg, then returns to 138 deg approaching Ollur
                float curveOffsetAngle = Mathf.Sin(t * Mathf.PI) * 7.5f;
                float currentYaw = highwayYaw + curveOffsetAngle;
                Quaternion tileRot = Quaternion.Euler(-90f, currentYaw, 0f);

                currentPos.y = currentElev;
                placedTilePositions.Add(currentPos);

                var tileGo = InstantiatePiece($"NH544_4Lane_Tile_{i:D2}", "Road_4Lane_Straight_22m",
                    currentPos, tileRot, highwayRoot.transform, meshes, materials, roadPhysMat);

                // Place Street Light on the median every 2 tiles (~45.6m)
                if (i % 2 == 0 && streetLightPrefab != null)
                {
                    Vector3 lightPos = currentPos + (tileRot * new Vector3(0f, 11.4f, 0.25f));
                    var lightGo = UnityEngine.Object.Instantiate(streetLightPrefab, lightPos, tileRot * Quaternion.Euler(0, 0, 90), furnitureRoot.transform);
                    lightGo.name = $"Median_StreetLight_{i:D2}";

                    var ptLight = lightGo.AddComponent<Light>();
                    ptLight.type = LightType.Point;
                    ptLight.color = new Color(1.0f, 0.95f, 0.85f); // Warm natural illumination
                    ptLight.intensity = 1.6f;
                    ptLight.range = 22f;
                    ptLight.shadows = LightShadows.None;
                }

                // Advance forward by 22.80m along current heading
                Vector3 stepDir = new Vector3(Mathf.Sin(currentYaw * Mathf.Deg2Rad), 0f, Mathf.Cos(currentYaw * Mathf.Deg2Rad)).normalized;
                currentPos += stepDir * 22.80f;
            }

            // ------------------------------------------------------------------------------------
            // 3. OLLUR INTERCHANGE 4-TO-2 TRANSITION & CONNECTOR
            // ------------------------------------------------------------------------------------
            float ollurYaw = highwayYaw;
            Quaternion ollurRot = Quaternion.Euler(-90f, ollurYaw, 0f);

            // 4-to-2 Transition Straight Piece (22.8m length)
            currentPos.y = OllurGoreAnchor.y;
            var transGo = InstantiatePiece("Ollur_Transition_4to2", "Road_Transition_4to2_Straight",
                currentPos, ollurRot, junctionsRoot.transform, meshes, materials, roadPhysMat);

            Vector3 ollurStepDir = new Vector3(Mathf.Sin(ollurYaw * Mathf.Deg2Rad), 0f, Mathf.Cos(ollurYaw * Mathf.Deg2Rad)).normalized;
            currentPos += ollurStepDir * 22.80f;

            // 2-Lane straight connector tiles (2 tiles = 45.6m)
            for (int k = 0; k < 3; k++)
            {
                var lane2 = InstantiatePiece($"Ollur_Connector_2Lane_{k}", "Road_2Lane_Straight_22m",
                    currentPos, ollurRot, junctionsRoot.transform, meshes, materials, roadPhysMat);
                currentPos += ollurStepDir * 22.80f;
            }

            // 2-Lane 90 degree curve looping back into local road / turning head
            var curve90 = InstantiatePiece("Ollur_Loop_Curve_90Deg", "Road_2Lane_Curve_90Deg",
                currentPos, ollurRot, junctionsRoot.transform, meshes, materials, roadPhysMat);

            EditorSceneManager.MarkSceneDirty(scene);
            EditorSceneManager.SaveScene(scene);
            AssetDatabase.SaveAssets();

            Debug.Log($"[MannuthyOllurRoadKitBuilder] Successfully built Mannuthy-Ollur RoadKit corridor! Total pieces placed: {numPieces + 5}");
        }

        private static GameObject InstantiatePiece(string name, string meshName, Vector3 position, Quaternion rotation,
            Transform parent, Dictionary<string, Mesh> meshes, Dictionary<string, Material> materials, PhysicsMaterial physMat)
        {
            if (!meshes.TryGetValue(meshName, out Mesh mesh))
            {
                Debug.LogError($"[RoadKitBuilder] Mesh '{meshName}' not found in FBX!");
                return null;
            }

            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            go.transform.position = position;
            go.transform.rotation = rotation;

            var mf = go.AddComponent<MeshFilter>();
            mf.sharedMesh = mesh;

            var mr = go.AddComponent<MeshRenderer>();
            // Assign matched materials
            var assignedMats = new Material[mesh.subMeshCount];
            for (int s = 0; s < mesh.subMeshCount; s++)
            {
                // Default fallback
                assignedMats[s] = materials.ContainsKey("M_CityKit_Asphalt") ? materials["M_CityKit_Asphalt"] : null;
            }

            // Standard submesh mapping for Bharat Road Kit
            // Submesh 0: Sidewalk, 1: Curb, 2: Asphalt, 3: SlabBase, 4: YellowLine, 5: WhiteLine
            if (assignedMats.Length >= 1 && materials.TryGetValue("M_CityKit_Sidewalk", out var mSidewalk)) assignedMats[0] = mSidewalk;
            if (assignedMats.Length >= 2 && materials.TryGetValue("M_CityKit_Curb", out var mCurb)) assignedMats[1] = mCurb;
            if (assignedMats.Length >= 3 && materials.TryGetValue("M_CityKit_Asphalt", out var mAsphalt)) assignedMats[2] = mAsphalt;
            if (assignedMats.Length >= 4 && materials.TryGetValue("M_CityKit_SlabBase", out var mBase)) assignedMats[3] = mBase;
            if (assignedMats.Length >= 5 && materials.TryGetValue("M_CityKit_YellowLine", out var mYellow)) assignedMats[4] = mYellow;
            if (assignedMats.Length >= 6 && materials.TryGetValue("M_CityKit_WhiteLine", out var mWhite)) assignedMats[5] = mWhite;

            mr.sharedMaterials = assignedMats;

            // Mesh Collider with zero-snag smooth physics material
            var col = go.AddComponent<MeshCollider>();
            col.sharedMesh = mesh;
            col.convex = false;
            col.material = physMat;

            return go;
        }
    }
}
