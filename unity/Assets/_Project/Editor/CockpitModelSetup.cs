using System;
using System.IO;
using System.Collections.Generic;
using UnityEditor;
using UnityEngine;
using BharatBusSim.Vehicles;

namespace BharatBusSim.Editor
{
    public static class CockpitModelSetup
    {
        public const string CockpitFbxPath = "Assets/_Project/Vehicles/Cockpit/kerala_bus_cockpit_only.fbx";
        public const string CockpitRealisticFbxPath = "Assets/_Project/Vehicles/Cockpit/kerala_bus_realistic_cockpit.fbx";
        public const string CockpitMatDir = "Assets/_Project/Vehicles/Cockpit/Materials";

        [MenuItem("BharatBusSim/Setup/Build & Attach Realistic Cockpit")]
        public static void SetupRealisticCockpitMenu()
        {
            string scenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
            var scene = UnityEditor.SceneManagement.EditorSceneManager.OpenScene(scenePath, UnityEditor.SceneManagement.OpenSceneMode.Single);

            var busGo = GameObject.Find("Kerala_Bus_Prototype");
            if (busGo == null)
            {
                Debug.LogError("[CockpitModelSetup] Kerala_Bus_Prototype not found in Thrissur scene!");
                return;
            }

            var visualsGo = busGo.transform.Find("KeralaBus12m_Visuals");
            if (visualsGo == null)
            {
                Debug.LogError("[CockpitModelSetup] KeralaBus12m_Visuals not found under Kerala_Bus_Prototype!");
                return;
            }

            AttachCockpitToBus(busGo, visualsGo.gameObject);

            // Update ChaseCamera presets to ensure latest settings are saved into scene
            var camGo = GameObject.Find("ChaseCamera");
            if (camGo != null)
            {
                var camCtrl = camGo.GetComponent<BusCameraController>();
                if (camCtrl != null)
                {
                    camCtrl.ResetDefaultPresets();
                    camCtrl.SetCockpitEyeSettings(2.35f, 0.70f, 4.65f, 6.5f);
                    camCtrl.SnapToTarget();
                }
            }

            UnityEditor.SceneManagement.EditorSceneManager.MarkSceneDirty(scene);
            UnityEditor.SceneManagement.EditorSceneManager.SaveScene(scene);
            Debug.Log("[CockpitModelSetup] Realistic Cockpit successfully built and attached to Thrissur bus!");
        }

        [MenuItem("BharatBusSim/Setup/Validate Cockpit Alignment")]
        public static void ValidateCockpitAlignment()
        {
            string scenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
            var scene = UnityEditor.SceneManagement.EditorSceneManager.OpenScene(scenePath, UnityEditor.SceneManagement.OpenSceneMode.Single);

            var busGo = GameObject.Find("Kerala_Bus_Prototype");
            if (busGo == null)
            {
                Debug.LogError("[CockpitModelSetup] Bus not found!");
                return;
            }

            var cockpitGo = GameObject.Find("RHD_DriverCockpit");
            if (cockpitGo == null)
            {
                Debug.LogError("[CockpitModelSetup] RHD_DriverCockpit not found!");
                return;
            }

            var wheel = FindChildRecursive(cockpitGo.transform, "Cockpit_SteeringWheel_Pivot");
            var drvDoor = FindChildRecursive(cockpitGo.transform, "Cockpit_DriverDoor_Hinge_Pivot");
            var paxDoor = FindChildRecursive(cockpitGo.transform, "Cockpit_PassengerDoor_Hinge_Pivot");
            var camPivot = FindChildRecursive(cockpitGo.transform, "Cam1_LookAround_Pivot");

            Debug.Log($"[CockpitValidation] Cockpit Root LocalPos={cockpitGo.transform.localPosition}, LocalRot={cockpitGo.transform.localRotation.eulerAngles}");
            if (wheel != null)
                Debug.Log($"[CockpitValidation] SteeringWheel LocalToBus={busGo.transform.InverseTransformPoint(wheel.position)}");
            if (drvDoor != null)
                Debug.Log($"[CockpitValidation] DriverDoor LocalToBus={busGo.transform.InverseTransformPoint(drvDoor.position)}");
            if (paxDoor != null)
                Debug.Log($"[CockpitValidation] PassengerDoor LocalToBus={busGo.transform.InverseTransformPoint(paxDoor.position)}");
            if (camPivot != null)
                Debug.Log($"[CockpitValidation] Cam1Pivot LocalToBus={busGo.transform.InverseTransformPoint(camPivot.position)}");

            var camGo = GameObject.Find("ChaseCamera");
            if (camGo != null)
            {
                var camCtrl = camGo.GetComponent<BusCameraController>();
                if (camCtrl != null)
                {
                    camCtrl.SetCameraPreset(0);
                    camCtrl.SnapToTarget();
                    Debug.Log($"[CockpitValidation] Cam 1 Eye LocalToBus={busGo.transform.InverseTransformPoint(camGo.transform.position)}");
                }
            }
        }

        [MenuItem("BharatBusSim/Setup/Capture Cockpit Screenshots")]
        public static void CaptureCockpitScreenshots()
        {
            string scenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
            var scene = UnityEditor.SceneManagement.EditorSceneManager.OpenScene(scenePath, UnityEditor.SceneManagement.OpenSceneMode.Single);

            var camGo = GameObject.Find("ChaseCamera");
            if (camGo == null)
            {
                Debug.LogError("[CockpitScreenshot] ChaseCamera not found!");
                return;
            }

            var camera = camGo.GetComponent<Camera>();
            var camCtrl = camGo.GetComponent<BusCameraController>();
            if (camera == null || camCtrl == null) return;

            camCtrl.SetCameraPreset(0); // Driver Cockpit
            camCtrl.SnapToTarget();

            int width = 1920;
            int height = 1080;
            var rt = new RenderTexture(width, height, 24, RenderTextureFormat.ARGB32);
            camera.targetTexture = rt;

            // 1. Forward View
            camCtrl.ResetCockpitView();
            camera.Render();
            SaveRenderTexture(rt, "C:/Project/BHARAT BUS SIMULATOR/screen_cam1_forward.png");
            Debug.Log("[CockpitScreenshot] Saved screen_cam1_forward.png");

            // 2. Driver Door View (Turn head right)
            camCtrl.SetCockpitLook(68f, -6f);
            var lateUpdate = typeof(BusCameraController).GetMethod("LateUpdate", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);
            for (int i = 0; i < 30; i++) if (lateUpdate != null) lateUpdate.Invoke(camCtrl, null);
            camera.Render();
            SaveRenderTexture(rt, "C:/Project/BHARAT BUS SIMULATOR/screen_cam1_driver_door.png");
            Debug.Log("[CockpitScreenshot] Saved screen_cam1_driver_door.png");

            // 3. Passenger Entry Door View (Turn head left)
            camCtrl.SetCockpitLook(-68f, -6f);
            for (int i = 0; i < 30; i++) if (lateUpdate != null) lateUpdate.Invoke(camCtrl, null);
            camera.Render();
            SaveRenderTexture(rt, "C:/Project/BHARAT BUS SIMULATOR/screen_cam1_passenger_door.png");
            Debug.Log("[CockpitScreenshot] Saved screen_cam1_passenger_door.png");

            // 4. Cam 2 (Rear Chase View)
            camCtrl.SetCameraPreset(1);
            camCtrl.SnapToTarget();
            for (int i = 0; i < 30; i++) if (lateUpdate != null) lateUpdate.Invoke(camCtrl, null);
            camera.Render();
            SaveRenderTexture(rt, "C:/Project/BHARAT BUS SIMULATOR/screen_cam2_chase.png");
            Debug.Log("[CockpitScreenshot] Saved screen_cam2_chase.png");

            // 5. Cam 3 (360 Orbit View)
            camCtrl.SetCameraPreset(2);
            camCtrl.SnapToTarget();
            camCtrl.OnOrbitDrag(new Vector2(150f, 0f));
            for (int i = 0; i < 30; i++) if (lateUpdate != null) lateUpdate.Invoke(camCtrl, null);
            camera.Render();
            SaveRenderTexture(rt, "C:/Project/BHARAT BUS SIMULATOR/screen_cam3_orbit.png");
            Debug.Log("[CockpitScreenshot] Saved screen_cam3_orbit.png");

            // Reset back to Cam 1
            camCtrl.SetCameraPreset(0);
            camCtrl.SnapToTarget();

            camera.targetTexture = null;
            RenderTexture.active = null;
            UnityEngine.Object.DestroyImmediate(rt);

            camCtrl.ResetCockpitView();
        }

        private static void SaveRenderTexture(RenderTexture rt, string filePath)
        {
            RenderTexture.active = rt;
            Texture2D tex = new Texture2D(rt.width, rt.height, TextureFormat.RGB24, false);
            tex.ReadPixels(new Rect(0, 0, rt.width, rt.height), 0, 0);
            tex.Apply();
            byte[] bytes = tex.EncodeToPNG();
            File.WriteAllBytes(filePath, bytes);
            UnityEngine.Object.DestroyImmediate(tex);
        }

        public static GameObject AttachCockpitToBus(GameObject busGo, GameObject visualsGo)
        {
            // 1. Remove old procedural cockpit if present
            Transform existing = visualsGo.transform.Find("RHD_DriverCockpit");
            if (existing != null) UnityEngine.Object.DestroyImmediate(existing.gameObject);

            // Also remove any existing Cockpit_Module_Root if present
            Transform existingRoot = visualsGo.transform.Find("Cockpit_Module_Root");
            if (existingRoot != null) UnityEngine.Object.DestroyImmediate(existingRoot.gameObject);

            // Hide old low-poly Ecoline dashboard and driver seat so they don't clip through
            string[] oldInteriorToDisable = new[] { "dasbor", "kursi_supir", "stir" };
            foreach (var name in oldInteriorToDisable)
            {
                Transform t = visualsGo.transform.Find(name);
                if (t != null) t.gameObject.SetActive(false);
            }

            // 2. Load FBX asset (prefer kerala_bus_realistic_cockpit.fbx)
            GameObject cockpitPrefab = AssetDatabase.LoadAssetAtPath<GameObject>(CockpitRealisticFbxPath);
            if (cockpitPrefab == null)
            {
                cockpitPrefab = AssetDatabase.LoadAssetAtPath<GameObject>(CockpitFbxPath);
            }
            if (cockpitPrefab == null)
            {
                Debug.LogError($"[CockpitModelSetup] Could not load cockpit FBX at {CockpitFbxPath} or {CockpitRealisticFbxPath}");
                return null;
            }

            // 3. Instantiate into bus visuals hierarchy
            GameObject cockpitGo = (GameObject)PrefabUtility.InstantiatePrefab(cockpitPrefab, visualsGo.transform);
            cockpitGo.name = "RHD_DriverCockpit";
            cockpitGo.transform.localPosition = Vector3.zero;
            cockpitGo.transform.localRotation = Quaternion.Euler(0f, 180f, 0f);
            cockpitGo.transform.localScale = Vector3.one;

            // 4. Strip any colliders, extra cameras, lights, and non-visual components to prevent physics & rendering interference
            foreach (var cam in cockpitGo.GetComponentsInChildren<Camera>(true))
            {
                Debug.Log($"[CockpitModelSetup] Removing extraneous camera imported from FBX: {cam.gameObject.name}");
                UnityEngine.Object.DestroyImmediate(cam.gameObject);
            }
            foreach (var listener in cockpitGo.GetComponentsInChildren<AudioListener>(true))
            {
                UnityEngine.Object.DestroyImmediate(listener);
            }
            foreach (var light in cockpitGo.GetComponentsInChildren<Light>(true))
            {
                Debug.Log($"[CockpitModelSetup] Removing extraneous light imported from FBX: {light.gameObject.name}");
                UnityEngine.Object.DestroyImmediate(light.gameObject);
            }
            foreach (var col in cockpitGo.GetComponentsInChildren<Collider>(true))
            {
                UnityEngine.Object.DestroyImmediate(col);
            }
            foreach (var rb in cockpitGo.GetComponentsInChildren<Rigidbody>(true))
            {
                UnityEngine.Object.DestroyImmediate(rb);
            }

            // 5. Build and apply URP PBR materials
            ApplyMaterials(cockpitGo);

            // 6. Connect CockpitDashboardSync for live physics animation
            var sync = busGo.GetComponent<CockpitDashboardSync>();
            if (sync == null) sync = busGo.AddComponent<CockpitDashboardSync>();
            sync.busController = busGo.GetComponent<BusController>();

            Transform wheelPivot = FindChildRecursive(cockpitGo.transform, "Cockpit_SteeringWheel_Pivot");
            Transform speedoNeedle = FindChildRecursive(cockpitGo.transform, "Cockpit_Gauge_Speedometer_Needle");
            Transform tachNeedle = FindChildRecursive(cockpitGo.transform, "Cockpit_Gauge_Tachometer_RPM_Needle");

            if (wheelPivot != null) sync.steeringWheel = wheelPivot;
            if (speedoNeedle != null)
            {
                sync.speedoNeedle = speedoNeedle;
                sync.speedoMinAngle = 120f;
                sync.speedoMaxAngle = -120f;
            }
            if (tachNeedle != null)
            {
                sync.tachNeedle = tachNeedle;
                sync.tachMinAngle = 120f;
                sync.tachMaxAngle = -120f;
            }
            sync.maxSteerWheelRotation = 450f;

            Debug.Log($"[CockpitModelSetup] Cockpit synced: SteeringWheel={(wheelPivot != null)}, Speedo={(speedoNeedle != null)}, Tach={(tachNeedle != null)}");
            return cockpitGo;
        }

        private static void ApplyMaterials(GameObject rootGo)
        {
            if (!Directory.Exists(CockpitMatDir))
            {
                Directory.CreateDirectory(CockpitMatDir);
            }

            Shader litShader = Shader.Find("Universal Render Pipeline/Lit");
            if (litShader == null) litShader = Shader.Find("Standard");

            Dictionary<string, Material> matMap = BuildMaterialLibrary(litShader);

            Renderer[] renderers = rootGo.GetComponentsInChildren<Renderer>(true);
            foreach (var r in renderers)
            {
                Material[] sharedMats = r.sharedMaterials;
                for (int i = 0; i < sharedMats.Length; i++)
                {
                    if (sharedMats[i] == null) continue;
                    string origName = sharedMats[i].name.Replace(" (Instance)", "");

                    // Check exact match or prefix
                    Material matched = null;
                    if (matMap.ContainsKey(origName))
                    {
                        matched = matMap[origName];
                    }
                    else
                    {
                        foreach (var kvp in matMap)
                        {
                            if (origName.IndexOf(kvp.Key, StringComparison.OrdinalIgnoreCase) >= 0)
                            {
                                matched = kvp.Value;
                                break;
                            }
                        }
                    }

                    if (matched != null)
                    {
                        sharedMats[i] = matched;
                    }
                }
                r.sharedMaterials = sharedMats;
            }
        }

        private static Dictionary<string, Material> BuildMaterialLibrary(Shader litShader)
        {
            var dict = new Dictionary<string, Material>(StringComparer.OrdinalIgnoreCase);

            // 1. Dashboard Charcoal Plastic
            dict["KBS_Mat_Dash_Charcoal"] = GetOrCreateMaterial("KBS_Mat_Dash_Charcoal", litShader,
                new Color(0.042f, 0.045f, 0.052f), metallic: 0.04f, smoothness: 0.44f);

            // 2. Quilted Diamond-Stitched Slate Leather
            dict["KBS_Mat_Quilted_Leather"] = GetOrCreateMaterial("KBS_Mat_Quilted_Leather", litShader,
                new Color(0.065f, 0.070f, 0.082f), metallic: 0.05f, smoothness: 0.45f);

            // 3. High-Gloss Burl Mahogany Woodgrain
            dict["KBS_Mat_Dash_Woodgrain"] = GetOrCreateMaterial("KBS_Mat_Dash_Woodgrain", litShader,
                new Color(0.30f, 0.085f, 0.025f), metallic: 0.12f, smoothness: 0.88f);

            // 4. Exterior Livery Gold / Yellow
            dict["KBS_Mat_Livery_Gold"] = GetOrCreateMaterial("KBS_Mat_Livery_Gold", litShader,
                new Color(0.88f, 0.62f, 0.03f), metallic: 0.65f, smoothness: 0.75f);

            // 5. Exterior Livery Emerald Green
            dict["KBS_Mat_Livery_Emerald"] = GetOrCreateMaterial("KBS_Mat_Livery_Emerald", litShader,
                new Color(0.04f, 0.36f, 0.11f), metallic: 0.50f, smoothness: 0.75f);

            // 6. Exterior Livery Vermilion Red
            dict["KBS_Mat_Livery_Vermilion"] = GetOrCreateMaterial("KBS_Mat_Livery_Vermilion", litShader,
                new Color(0.86f, 0.12f, 0.06f), metallic: 0.20f, smoothness: 0.70f);

            // 7. Champagne Gold Trim & Emblems
            dict["KBS_Mat_Champagne_Gold"] = GetOrCreateMaterial("KBS_Mat_Champagne_Gold", litShader,
                new Color(0.82f, 0.68f, 0.32f), metallic: 0.85f, smoothness: 0.85f);

            // 8. Brushed Satin Aluminum
            dict["KBS_Mat_Brushed_Silver"] = GetOrCreateMaterial("KBS_Mat_Brushed_Silver", litShader,
                new Color(0.62f, 0.65f, 0.68f), metallic: 0.85f, smoothness: 0.75f);

            // 9. Navy/Charcoal Seat Upholstery
            dict["KBS_Mat_Seat_Upholstery"] = GetOrCreateMaterial("KBS_Mat_Seat_Upholstery", litShader,
                new Color(0.07f, 0.12f, 0.20f), metallic: 0.02f, smoothness: 0.25f);

            // 10. Overhead Velvet Valance Maroon
            dict["KBS_Mat_Velvet_Maroon"] = GetOrCreateMaterial("KBS_Mat_Velvet_Maroon", litShader,
                new Color(0.32f, 0.02f, 0.05f), metallic: 0.0f, smoothness: 0.15f);

            // 11. Ambient Cyan LED Piping (Emissive)
            var ledCyan = GetOrCreateMaterial("KBS_Mat_LED_Ambient_Cyan", litShader,
                new Color(0.05f, 0.82f, 0.98f), metallic: 0.0f, smoothness: 0.90f);
            SetEmission(ledCyan, new Color(0.05f, 0.82f, 0.98f) * 2.5f);
            dict["KBS_Mat_LED_Ambient_Cyan"] = ledCyan;

            // 12. Transparent Glass (Windshield, Doors)
            var glass = GetOrCreateMaterial("KBS_Mat_Glass_Clear", litShader,
                new Color(0.85f, 0.92f, 0.98f, 0.22f), metallic: 0.1f, smoothness: 0.95f, isTransparent: true);
            dict["KBS_Mat_Glass_Clear"] = glass;

            // 13. Reflective Mirrors (Driver, Passenger, Interior)
            dict["KBS_Mat_Mirror_Reflective"] = GetOrCreateMaterial("KBS_Mat_Mirror_Reflective", litShader,
                new Color(0.92f, 0.92f, 0.95f), metallic: 0.95f, smoothness: 0.96f);

            // 14. Gauge Needles (Emissive Red/Orange)
            var needleRed = GetOrCreateMaterial("KBS_Mat_Needle_Red", litShader,
                new Color(1.0f, 0.22f, 0.08f), metallic: 0.0f, smoothness: 0.80f);
            SetEmission(needleRed, new Color(1.0f, 0.22f, 0.08f) * 2.0f);
            dict["KBS_Mat_Needle_Red"] = needleRed;

            // 15. Gauge Dial Cluster
            dict["KBS_Mat_Gauge_Dial"] = GetOrCreateMaterial("KBS_Mat_Gauge_Dial", litShader,
                new Color(0.08f, 0.08f, 0.10f), metallic: 0.15f, smoothness: 0.60f);

            // 16. Floor Tufted Carpet
            dict["KBS_Mat_Floor_Carpet"] = GetOrCreateMaterial("KBS_Mat_Floor_Carpet", litShader,
                new Color(0.08f, 0.08f, 0.09f), metallic: 0.0f, smoothness: 0.15f);

            // 17. Handrails Safety Yellow
            dict["KBS_Mat_Handrail_Yellow"] = GetOrCreateMaterial("KBS_Mat_Handrail_Yellow", litShader,
                new Color(0.95f, 0.80f, 0.05f), metallic: 0.2f, smoothness: 0.65f);

            // 18. Rubber Trim / Tires
            dict["KBS_Mat_Rubber_Black"] = GetOrCreateMaterial("KBS_Mat_Rubber_Black", litShader,
                new Color(0.05f, 0.05f, 0.05f), metallic: 0.0f, smoothness: 0.20f);

            // 19. Amber Switch (Emissive)
            var swAmber = GetOrCreateMaterial("KBS_Mat_Switch_Lit_Amber", litShader,
                new Color(1.0f, 0.55f, 0.05f), metallic: 0.0f, smoothness: 0.85f);
            SetEmission(swAmber, new Color(1.0f, 0.55f, 0.05f) * 1.8f);
            dict["KBS_Mat_Switch_Lit_Amber"] = swAmber;

            // 20. Green Switch (Emissive)
            var swGreen = GetOrCreateMaterial("KBS_Mat_Switch_Lit_Green", litShader,
                new Color(0.1f, 0.95f, 0.2f), metallic: 0.0f, smoothness: 0.85f);
            SetEmission(swGreen, new Color(0.1f, 0.95f, 0.2f) * 1.8f);
            dict["KBS_Mat_Switch_Lit_Green"] = swGreen;

            // 21. GPS Navigation Screen (Emissive Cyan/Blue)
            var navScreen = GetOrCreateMaterial("KBS_Mat_Nav_Screen", litShader,
                new Color(0.10f, 0.35f, 0.55f), metallic: 0.0f, smoothness: 0.80f);
            SetEmission(navScreen, new Color(0.10f, 0.35f, 0.55f) * 1.5f);
            dict["KBS_Mat_Nav_Screen"] = navScreen;

            // 22. Steering Leather
            dict["KBS_Mat_Steering_Leather"] = GetOrCreateMaterial("KBS_Mat_Steering_Leather", litShader,
                new Color(0.06f, 0.06f, 0.07f), metallic: 0.02f, smoothness: 0.35f);

            return dict;
        }

        private static Material GetOrCreateMaterial(string matName, Shader shader, Color color, float metallic = 0f, float smoothness = 0.5f, bool isTransparent = false)
        {
            string path = $"{CockpitMatDir}/{matName}.mat";
            Material mat = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (mat == null)
            {
                mat = new Material(shader) { name = matName };
                AssetDatabase.CreateAsset(mat, path);
            }

            mat.color = color;
            mat.SetFloat("_Metallic", metallic);
            mat.SetFloat("_Smoothness", smoothness);

            if (isTransparent)
            {
                // Set URP transparent properties
                mat.SetFloat("_Surface", 1.0f); // 0 = Opaque, 1 = Transparent
                mat.SetFloat("_Blend", 0.0f);   // 0 = Alpha
                mat.SetInt("_SrcBlend", (int)UnityEngine.Rendering.BlendMode.SrcAlpha);
                mat.SetInt("_DstBlend", (int)UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha);
                mat.SetInt("_ZWrite", 0);
                mat.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
                mat.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Transparent;
            }
            else
            {
                mat.SetFloat("_Surface", 0.0f);
                mat.SetInt("_SrcBlend", (int)UnityEngine.Rendering.BlendMode.One);
                mat.SetInt("_DstBlend", (int)UnityEngine.Rendering.BlendMode.Zero);
                mat.SetInt("_ZWrite", 1);
                mat.DisableKeyword("_SURFACE_TYPE_TRANSPARENT");
                mat.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Geometry;
            }

            EditorUtility.SetDirty(mat);
            return mat;
        }

        private static void SetEmission(Material mat, Color emissionColor)
        {
            if (mat.HasProperty("_EmissionColor"))
            {
                mat.EnableKeyword("_EMISSION");
                mat.SetColor("_EmissionColor", emissionColor);
            }
        }

        public static Transform FindChildRecursive(Transform parent, string name)
        {
            if (parent.name.Equals(name, StringComparison.OrdinalIgnoreCase)) return parent;
            for (int i = 0; i < parent.childCount; i++)
            {
                Transform found = FindChildRecursive(parent.GetChild(i), name);
                if (found != null) return found;
            }
            return null;
        }
    }
}
