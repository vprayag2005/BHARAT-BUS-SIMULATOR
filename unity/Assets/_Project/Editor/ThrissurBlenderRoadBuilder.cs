using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace BharatBusSim.Editor
{
    /// <summary>
    /// Replaces all legacy/modular roads in 03_Thrissur_RealisticWorld.unity with
    /// the real-world OpenStreetMap Thrissur road network built in Blender.
    /// Removes all trees and duplicate road meshes, and ensures a clean ground plane.
    /// </summary>
    public static class ThrissurBlenderRoadBuilder
    {
        public const string RoadsFbxPath = "Assets/_Project/Models/ThrissurRoads/Thrissur_Roads.fbx";
        public const string MaterialsDir = "Assets/_Project/Models/ThrissurRoads/Materials";
        public const string ScenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
        public const string PhysMatPath = "Assets/_Project/Models/RoadKits/PhysicsMat_RoadAsphalt.physicsMaterial";

        [MenuItem("BharatBusSim/Roads/Rebuild Thrissur Blender Roads (No Trees)")]
        public static void RebuildInActiveOrMasterScene()
        {
            var scene = EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
            if (!scene.IsValid())
            {
                Debug.LogError($"[ThrissurBlenderRoadBuilder] Failed to open scene at {ScenePath}");
                return;
            }

            ApplyToCurrentScene();
            RemoveAllTreesInScene();
            EditorSceneManager.SaveScene(scene, ScenePath);
            AssetDatabase.SaveAssets();
            Debug.Log("[ThrissurBlenderRoadBuilder] Saved 03_Thrissur_RealisticWorld.unity with new Blender Thrissur roads (0 trees).");
        }

        public static void RunFromCommandLine()
        {
            try
            {
                RebuildInActiveOrMasterScene();
                EditorApplication.Exit(0);
            }
            catch (Exception ex)
            {
                Debug.LogError($"[ThrissurBlenderRoadBuilder] FATAL: {ex.Message}\n{ex.StackTrace}");
                EditorApplication.Exit(1);
            }
        }

        public static GameObject ApplyToCurrentScene()
        {
            // 1. Remove all old/legacy/modular road roots
            string[] oldRoadRoots =
            {
                "Mannuthy_Ollur_RoadKit_Corridor",
                "World_Roads",
                "Thrissur_Road_Network",
                "Kerala_Environment_Scenery",
                "ProvingGround_Track",
                "Vegetation_Clusters"
            };
            foreach (var rootName in oldRoadRoots)
            {
                var existing = GameObject.Find(rootName);
                while (existing != null)
                {
                    Debug.Log($"[ThrissurBlenderRoadBuilder] Removing old object: {existing.name}");
                    UnityEngine.Object.DestroyImmediate(existing);
                    existing = GameObject.Find(rootName);
                }
            }

            // 2. Remove any trees or duplicate legacy road meshes anywhere in the scene
            RemoveAllTreesInScene();

            // 3. Ensure clean non-magenta ground plane covering full Thrissur district network
            EnsureCleanGroundPlane();

            // 4. Import & instantiate Blender Thrissur Roads FBX
            AssetDatabase.ImportAsset(RoadsFbxPath, ImportAssetOptions.ForceUpdate);
            var fbxPrefab = AssetDatabase.LoadAssetAtPath<GameObject>(RoadsFbxPath);
            if (fbxPrefab == null)
            {
                throw new FileNotFoundException($"Could not load Blender road FBX at {RoadsFbxPath}");
            }

            var worldRoads = new GameObject("World_Roads");
            var networkRoot = new GameObject("Thrissur_Road_Network");
            networkRoot.transform.SetParent(worldRoads.transform, false);

            var instance = (GameObject)PrefabUtility.InstantiatePrefab(fbxPrefab, networkRoot.transform);
            if (instance == null)
            {
                instance = UnityEngine.Object.Instantiate(fbxPrefab, networkRoot.transform);
            }
            instance.name = "Thrissur_OSM_Roads_Blender";
            instance.transform.localPosition = Vector3.zero;
            instance.transform.localRotation = Quaternion.identity;
            instance.transform.localScale = Vector3.one;

            // 5. Setup URP Lit Materials & PhysicsMaterial Colliders on every Blender road corridor
            var matMap = SetupRoadMaterials();
            var physMat = GetOrCreateRoadPhysicsMaterial();

            var renderers = instance.GetComponentsInChildren<MeshRenderer>(true);
            foreach (var r in renderers)
            {
                var srcMats = r.sharedMaterials;
                var newMats = new Material[srcMats.Length];
                for (int i = 0; i < srcMats.Length; i++)
                {
                    string mName = srcMats[i] != null ? srcMats[i].name : "";
                    newMats[i] = ResolveRoadMaterial(mName, matMap);
                }
                r.sharedMaterials = newMats;
                r.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
                r.receiveShadows = true;

                var mf = r.GetComponent<MeshFilter>();
                if (mf != null && mf.sharedMesh != null)
                {
                    var mc = r.gameObject.GetComponent<MeshCollider>();
                    if (mc == null) mc = r.gameObject.AddComponent<MeshCollider>();
                    mc.sharedMesh = mf.sharedMesh;
                    mc.convex = false;
                    mc.sharedMaterial = physMat;

                    Bounds b = r.bounds;
                    Debug.Log($"[ThrissurBlenderRoadBuilder] Road '{r.gameObject.name}' bounds center={b.center} size={b.size}");
                }
            }

            return worldRoads;
        }

        public static void RemoveAllTreesInScene()
        {
            var allTransforms = UnityEngine.Object.FindObjectsByType<Transform>(FindObjectsSortMode.None);
            var toDestroy = new List<GameObject>();
            foreach (var t in allTransforms)
            {
                if (t == null) continue;
                string n = t.gameObject.name;
                if (n.IndexOf("Coconut_Palm", StringComparison.OrdinalIgnoreCase) >= 0 ||
                    n.IndexOf("Kerala_Rain_Tree", StringComparison.OrdinalIgnoreCase) >= 0 ||
                    n.IndexOf("Vegetation_Clusters", StringComparison.OrdinalIgnoreCase) >= 0 ||
                    n.StartsWith("Palm", StringComparison.OrdinalIgnoreCase) ||
                    n.StartsWith("Tree", StringComparison.OrdinalIgnoreCase) ||
                    n.StartsWith("Frond_", StringComparison.OrdinalIgnoreCase) ||
                    n.StartsWith("Canopy_Dome", StringComparison.OrdinalIgnoreCase) ||
                    n.Equals("Road_Ring_Asphalt", StringComparison.OrdinalIgnoreCase) ||
                    n.Equals("Roads_Radial", StringComparison.OrdinalIgnoreCase))
                {
                    toDestroy.Add(t.gameObject);
                    continue;
                }

                var mr = t.GetComponent<MeshRenderer>();
                if (mr != null && mr.sharedMaterials != null)
                {
                    foreach (var m in mr.sharedMaterials)
                    {
                        if (m == null) continue;
                        if (m.name.IndexOf("Palm", StringComparison.OrdinalIgnoreCase) >= 0 ||
                            m.name.IndexOf("Leaf", StringComparison.OrdinalIgnoreCase) >= 0 ||
                            m.name.IndexOf("Trunk", StringComparison.OrdinalIgnoreCase) >= 0)
                        {
                            toDestroy.Add(t.gameObject);
                            break;
                        }
                    }
                }
            }

            foreach (var go in toDestroy)
            {
                if (go != null) UnityEngine.Object.DestroyImmediate(go);
            }
            if (toDestroy.Count > 0)
            {
                Debug.Log($"[ThrissurBlenderRoadBuilder] Removed {toDestroy.Count} tree / legacy road objects.");
            }
        }

        private static void EnsureCleanGroundPlane()
        {
            var oldGround = GameObject.Find("Thrissur_Ground_Base");
            if (oldGround != null) UnityEngine.Object.DestroyImmediate(oldGround);

            Shader litShader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
            if (!AssetDatabase.IsValidFolder(MaterialsDir))
            {
                Directory.CreateDirectory(MaterialsDir);
                AssetDatabase.Refresh();
            }

            string groundMatPath = $"{MaterialsDir}/MAT_Thrissur_Ground.mat";
            Material groundMat = AssetDatabase.LoadAssetAtPath<Material>(groundMatPath);
            if (groundMat == null)
            {
                groundMat = new Material(litShader) { name = "MAT_Thrissur_Ground" };
                AssetDatabase.CreateAsset(groundMat, groundMatPath);
            }
            groundMat.shader = litShader;
            groundMat.color = new Color(0.32f, 0.36f, 0.26f);
            if (groundMat.HasProperty("_Smoothness")) groundMat.SetFloat("_Smoothness", 0.05f);
            EditorUtility.SetDirty(groundMat);

            var ground = GameObject.CreatePrimitive(PrimitiveType.Plane);
            ground.name = "Thrissur_Ground_Base";
            ground.transform.position = new Vector3(-400f, -0.02f, -500f);
            ground.transform.localScale = new Vector3(1200f, 1f, 1200f); // 12km x 12km regional base
            ground.GetComponent<Renderer>().sharedMaterial = groundMat;
            var col = ground.GetComponent<Collider>();
            if (col != null)
            {
                col.sharedMaterial = GetOrCreateRoadPhysicsMaterial();
            }
        }

        private static Dictionary<string, Material> SetupRoadMaterials()
        {
            if (!Directory.Exists(MaterialsDir))
            {
                Directory.CreateDirectory(MaterialsDir);
                AssetDatabase.Refresh();
            }

            Shader litShader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
            Texture2D asphaltTex = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Textures/Tex_Asphalt_PBR.jpg");
            if (asphaltTex == null)
                asphaltTex = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Models/RoadKits/HighwayKit/Textures/local_Asphalt.png");
            Texture2D asphaltNormal = AssetDatabase.LoadAssetAtPath<Texture2D>("Assets/_Project/Textures/Tex_Asphalt_Normal.jpg");

            var dict = new Dictionary<string, Material>();
            dict["MAT_Road_Asphalt"] = CreateOrUpdateMat("MAT_Road_Asphalt", litShader, new Color(0.20f, 0.20f, 0.22f), asphaltTex, asphaltNormal, 0.22f, Vector2.one);
            dict["MAT_Road_Shoulder"] = CreateOrUpdateMat("MAT_Road_Shoulder", litShader, new Color(0.46f, 0.30f, 0.20f), null, null, 0.08f, Vector2.one);
            dict["MAT_Road_Curb"] = CreateOrUpdateMat("MAT_Road_Curb", litShader, new Color(0.72f, 0.72f, 0.69f), null, null, 0.15f, Vector2.one);
            dict["MAT_Road_Median"] = CreateOrUpdateMat("MAT_Road_Median", litShader, new Color(0.76f, 0.76f, 0.74f), null, null, 0.18f, Vector2.one);
            dict["MAT_Road_WhiteLine"] = CreateOrUpdateMat("MAT_Road_WhiteLine", litShader, new Color(0.95f, 0.95f, 0.95f), null, null, 0.30f, Vector2.one);
            dict["MAT_Road_YellowLine"] = CreateOrUpdateMat("MAT_Road_YellowLine", litShader, new Color(0.96f, 0.78f, 0.06f), null, null, 0.30f, Vector2.one);

            AssetDatabase.SaveAssets();
            return dict;
        }

        private static Material ResolveRoadMaterial(string srcName, Dictionary<string, Material> map)
        {
            foreach (var kv in map)
            {
                if (srcName.IndexOf(kv.Key, StringComparison.OrdinalIgnoreCase) >= 0)
                    return kv.Value;
            }
            return map["MAT_Road_Asphalt"];
        }

        private static Material CreateOrUpdateMat(string name, Shader shader, Color color, Texture2D mainTex, Texture2D normalTex, float smoothness, Vector2 scale)
        {
            string path = $"{MaterialsDir}/{name}.mat";
            Material mat = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (mat == null)
            {
                mat = new Material(shader) { name = name };
                AssetDatabase.CreateAsset(mat, path);
            }
            else
            {
                mat.shader = shader;
            }
            mat.color = color;
            mat.mainTexture = mainTex;
            mat.mainTextureScale = scale;
            if (normalTex != null && mat.HasProperty("_BumpMap"))
            {
                mat.SetTexture("_BumpMap", normalTex);
                mat.EnableKeyword("_NORMALMAP");
            }
            if (mat.HasProperty("_Smoothness")) mat.SetFloat("_Smoothness", smoothness);
            EditorUtility.SetDirty(mat);
            return mat;
        }

        private static PhysicsMaterial GetOrCreateRoadPhysicsMaterial()
        {
            PhysicsMaterial physMat = AssetDatabase.LoadAssetAtPath<PhysicsMaterial>(PhysMatPath);
            if (physMat == null)
            {
                string dir = Path.GetDirectoryName(PhysMatPath);
                if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir)) Directory.CreateDirectory(dir);
                physMat = new PhysicsMaterial("PhysicsMat_RoadAsphalt")
                {
                    dynamicFriction = 0.90f,
                    staticFriction = 0.90f,
                    bounciness = 0.0f,
                    frictionCombine = PhysicsMaterialCombine.Average,
                    bounceCombine = PhysicsMaterialCombine.Minimum
                };
                AssetDatabase.CreateAsset(physMat, PhysMatPath);
            }
            return physMat;
        }
    }
}
