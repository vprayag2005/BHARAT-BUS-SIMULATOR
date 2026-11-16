using System;
using System.IO;
using System.Collections.Generic;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using BharatBusSim.Vehicles;

namespace BharatBusSim.Editor
{
    public static class HinoBusIntegration
    {
        private const string ScenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
        private const string CleanFbxPath = "Assets/_Project/Vehicles/Models/Hino/HINO_RM2_Clean.fbx";
        private const string TexDir = "Assets/_Project/Vehicles/Textures/Hino";
        private const string MatDir = "Assets/_Project/Vehicles/Models/Hino/Materials";

        [MenuItem("BharatBusSim/Hino/Assemble Clean Bus & Validate Cameras")]
        public static void AssembleCleanBusAndValidate()
        {
            Debug.Log("[HinoIntegration] Starting Hino RM 2 Bus clean assembly and camera validation...");

            var scene = EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
            if (!scene.IsValid())
            {
                Debug.LogError($"[HinoIntegration] Could not open scene at {ScenePath}");
                return;
            }

            var busGo = GameObject.Find("Kerala_Bus_Prototype");
            if (busGo == null)
            {
                Debug.LogError("[HinoIntegration] Kerala_Bus_Prototype not found in scene!");
                return;
            }

            // Move bus out to the wide open road apron under open sky
            busGo.transform.position = new Vector3(25.0f, 0.35f, -105.0f);
            busGo.transform.rotation = Quaternion.Euler(0f, 0f, 0f);

            // 1. Prepare Materials
            EnsureMaterialsDirectory();
            var materials = CreateOrUpdatePbrMaterials();

            // 2. Load clean FBX model
            GameObject cleanPrefab = AssetDatabase.LoadAssetAtPath<GameObject>(CleanFbxPath);
            if (cleanPrefab == null)
            {
                Debug.LogError($"[HinoIntegration] Clean FBX missing at {CleanFbxPath}");
                return;
            }

            // 3. Replace KeralaBus12m_Visuals under bus
            Transform existingVisuals = busGo.transform.Find("KeralaBus12m_Visuals");
            if (existingVisuals != null)
            {
                UnityEngine.Object.DestroyImmediate(existingVisuals.gameObject);
            }

            GameObject newVisuals = (GameObject)PrefabUtility.InstantiatePrefab(cleanPrefab, busGo.transform);
            PrefabUtility.UnpackPrefabInstance(newVisuals, PrefabUnpackMode.Completely, InteractionMode.AutomatedAction);
            newVisuals.name = "KeralaBus12m_Visuals";
            newVisuals.transform.SetSiblingIndex(0);

            // Bus scaling: match 2.55m x 12.0m bus dimensions
            newVisuals.transform.localPosition = new Vector3(0f, 2.12f, 0.55f);
            newVisuals.transform.localRotation = Quaternion.identity;
            newVisuals.transform.localScale = new Vector3(0.685f, 0.720f, 0.765f);

            // Strip any colliders or non-bus modeling planes from visuals
            foreach (var col in newVisuals.GetComponentsInChildren<Collider>(true))
            {
                UnityEngine.Object.DestroyImmediate(col);
            }

            for (int i = newVisuals.transform.childCount - 1; i >= 0; i--)
            {
                Transform child = newVisuals.transform.GetChild(i);
                string ln = child.name.ToLowerInvariant();
                if (ln.Contains("plane.001") || ln.Contains("plane.049") || ln.Contains("camera") || ln.Contains("lamp") || ln.Contains("area"))
                {
                    UnityEngine.Object.DestroyImmediate(child.gameObject);
                }
            }

            // 4. Assign PBR materials across all submeshes
            AssignMaterialsToVisuals(newVisuals, materials);

            // 5. Connect Steering Wheel to CockpitDashboardSync
            Transform steerWheel = newVisuals.transform.Find("Cockpit_SteeringWheel");
            var sync = busGo.GetComponentInChildren<CockpitDashboardSync>(true);
            if (sync != null && steerWheel != null)
            {
                sync.steeringWheel = steerWheel;
                sync.maxSteerWheelRotation = 450f;
                sync.steerSmoothSpeed = 16f;
                Debug.Log($"[HinoIntegration] CockpitDashboardSync hooked to {steerWheel.name} at {steerWheel.localPosition}");
            }
            else
            {
                Debug.LogWarning("[HinoIntegration] CockpitDashboardSync or Cockpit_SteeringWheel not found!");
            }

            // 6. Setup and verify Camera Controller presets
            var camGo = GameObject.Find("ChaseCamera");
            if (camGo == null)
            {
                camGo = GameObject.FindWithTag("MainCamera");
            }

            if (camGo != null)
            {
                var camCtrl = camGo.GetComponent<BusCameraController>();
                if (camCtrl != null)
                {
                    camCtrl.target = busGo.transform;
                    camCtrl.ResetDefaultPresets();

                    // Fine-tune Cockpit eye perspective (seated behind steering wheel, capturing both mirrors)
                    camCtrl.SetCockpitEyeSettings(2.28f, 0.62f, 4.05f, 4.5f);
                    camCtrl.SetCameraPreset(0); // Default to Preset 0: Static Rear Chase
                    camCtrl.SnapToTarget();
                    Debug.Log("[HinoIntegration] Camera Controller presets initialized (0=StaticRearChase, 1=DriverCockpit, 2=360Orbit)");
                }
            }

            // 7. Save Scene
            EditorSceneManager.MarkSceneDirty(scene);
            EditorSceneManager.SaveScene(scene);
            Debug.Log("[HinoIntegration] Scene saved successfully!");

            // 8. Capture Verification Images
            CaptureVerificationScreenshots(busGo, camGo);
        }

        private static void EnsureMaterialsDirectory()
        {
            if (!AssetDatabase.IsValidFolder("Assets/_Project/Vehicles/Models/Hino/Materials"))
            {
                AssetDatabase.CreateFolder("Assets/_Project/Vehicles/Models/Hino", "Materials");
            }
        }

        private static Dictionary<string, Material> CreateOrUpdatePbrMaterials()
        {
            Shader litShader = Shader.Find("Universal Render Pipeline/Lit");
            if (litShader == null) litShader = Shader.Find("Standard");

            var result = new Dictionary<string, Material>();

            Material GetOrCreateMat(string matName, Action<Material> configure)
            {
                string assetPath = $"{MatDir}/{matName}.mat";
                Material mat = AssetDatabase.LoadAssetAtPath<Material>(assetPath);
                if (mat == null)
                {
                    mat = new Material(litShader) { name = matName };
                    configure(mat);
                    AssetDatabase.CreateAsset(mat, assetPath);
                }
                else
                {
                    configure(mat);
                    EditorUtility.SetDirty(mat);
                }
                result[matName] = mat;
                return mat;
            }

            Texture2D LoadTex(string fileName)
            {
                return AssetDatabase.LoadAssetAtPath<Texture2D>($"{TexDir}/{fileName}");
            }

            // Textures
            Texture2D texLivery = LoadTex("Eagle paint test.png");
            if (texLivery == null) texLivery = LoadTex("Ak travels.png");
            Texture2D texFrontGlass = LoadTex("FRONT GLASS.png");
            Texture2D texEagleGlass = LoadTex("Eagle glass.png");
            Texture2D texLights = LoadTex("Lights0012_350.jpg");
            Texture2D texSpeedo = LoadTex("Main gear kmh.png");
            Texture2D texRpm = LoadTex("rpmpper minute.png");
            Texture2D texAirPres = LoadTex("air pressure.png");
            Texture2D texAcControl = LoadTex("AC CONTROLL.png");
            Texture2D texButtons = LoadTex("AC AND OTHER BUTTON.png");
            Texture2D texButton2 = LoadTex("button 2.png");
            Texture2D texHourMeter = LoadTex("HOUR METER.png");
            Texture2D texGear = LoadTex("GEAR MANUAL.png");
            Texture2D texPlate = LoadTex("plate no.png");
            Texture2D texRoofAc = LoadTex("big ac.png");
            Texture2D texWoodDash = LoadTex("327331028_1409317363171787_8606939358390726408_n.jpg");
            Texture2D texGps = LoadTex("327683426_851893149445161_1489542805965647362_n.jpg");
            Texture2D texTissue = LoadTex("tissue rtexture.png");
            Texture2D texBlack = LoadTex("black.png");
            Texture2D texGrey = LoadTex("grey.png");
            Texture2D texInteriorTrim = LoadTex("greay and yellow.png");

            // 1. Exterior Body Livery (Eagle / Kerala livery)
            GetOrCreateMat("MAT_Hino_BodyLivery", m =>
            {
                m.mainTexture = texLivery;
                m.color = Color.white;
                m.SetFloat("_Smoothness", 0.78f);
                m.SetFloat("_Metallic", 0.08f);
            });

            // 2. Front Windshield (Transparent)
            GetOrCreateMat("MAT_Hino_FrontWindshield", m =>
            {
                m.mainTexture = texFrontGlass;
                m.color = new Color(0.95f, 0.98f, 1.0f, 0.65f);
                m.SetFloat("_Smoothness", 0.96f);
                m.SetFloat("_Surface", 1.0f);
                m.SetFloat("_Blend", 0.0f);
                m.SetFloat("_SrcBlend", 5.0f);
                m.SetFloat("_DstBlend", 10.0f);
                m.SetFloat("_ZWrite", 0.0f);
                m.SetOverrideTag("RenderType", "Transparent");
                m.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Transparent;
                m.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            });

            // 3. Side & Passenger Windows (Transparent tinted)
            GetOrCreateMat("MAT_Hino_SideWindows", m =>
            {
                m.mainTexture = texEagleGlass;
                m.color = new Color(0.18f, 0.22f, 0.28f, 0.60f);
                m.SetFloat("_Smoothness", 0.95f);
                m.SetFloat("_Surface", 1.0f);
                m.SetFloat("_Blend", 0.0f);
                m.SetFloat("_SrcBlend", 5.0f);
                m.SetFloat("_DstBlend", 10.0f);
                m.SetFloat("_ZWrite", 0.0f);
                m.SetOverrideTag("RenderType", "Transparent");
                m.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Transparent;
                m.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            });

            // 4. Lights (Headlamps)
            GetOrCreateMat("MAT_Hino_Headlamps", m =>
            {
                m.mainTexture = texLights;
                m.color = Color.white;
                m.SetFloat("_Smoothness", 0.88f);
                m.SetFloat("_Metallic", 0.30f);
            });

            // 5. Tail Lights (Emissive Red)
            GetOrCreateMat("MAT_Hino_TailLights_Red", m =>
            {
                m.color = new Color(0.95f, 0.05f, 0.05f);
                m.EnableKeyword("_EMISSION");
                m.SetColor("_EmissionColor", new Color(1.8f, 0.15f, 0.15f));
            });

            // 6. Indicators (Emissive Yellow)
            GetOrCreateMat("MAT_Hino_Indicators_Yellow", m =>
            {
                m.color = new Color(1.0f, 0.70f, 0.05f);
                m.EnableKeyword("_EMISSION");
                m.SetColor("_EmissionColor", new Color(1.8f, 1.1f, 0.1f));
            });

            // 7. Reverse / White Lights
            GetOrCreateMat("MAT_Hino_Reverse_White", m =>
            {
                m.color = new Color(1.0f, 1.0f, 0.95f);
                m.EnableKeyword("_EMISSION");
                m.SetColor("_EmissionColor", new Color(1.5f, 1.5f, 1.4f));
            });

            // 8. Speedometer Dial
            GetOrCreateMat("MAT_Hino_Gauge_Speedo", m =>
            {
                m.mainTexture = texSpeedo;
                m.color = Color.white;
                m.SetFloat("_Smoothness", 0.65f);
            });

            // 9. Tachometer Dial
            GetOrCreateMat("MAT_Hino_Gauge_Rpm", m =>
            {
                m.mainTexture = texRpm;
                m.color = Color.white;
                m.SetFloat("_Smoothness", 0.65f);
            });

            // 10. Air Pressure Gauge
            GetOrCreateMat("MAT_Hino_Gauge_AirPres", m =>
            {
                m.mainTexture = texAirPres;
                m.color = Color.white;
                m.SetFloat("_Smoothness", 0.65f);
            });

            // 11. AC Control Panel
            GetOrCreateMat("MAT_Hino_AcControl", m =>
            {
                m.mainTexture = texAcControl;
                m.color = Color.white;
                m.SetFloat("_Smoothness", 0.50f);
            });

            // 12. Dashboard Buttons
            GetOrCreateMat("MAT_Hino_Buttons", m =>
            {
                m.mainTexture = texButtons;
                m.color = Color.white;
                m.SetFloat("_Smoothness", 0.40f);
            });

            // 13. Rocker Switches
            GetOrCreateMat("MAT_Hino_Button2", m =>
            {
                m.mainTexture = texButton2;
                m.color = Color.white;
                m.SetFloat("_Smoothness", 0.45f);
            });

            // 14. Hour Meter
            GetOrCreateMat("MAT_Hino_HourMeter", m =>
            {
                m.mainTexture = texHourMeter;
                m.color = Color.white;
                m.SetFloat("_Smoothness", 0.50f);
            });

            // 15. Gear Shift Guide
            GetOrCreateMat("MAT_Hino_GearGuide", m =>
            {
                m.mainTexture = texGear;
                m.color = Color.white;
                m.SetFloat("_Smoothness", 0.50f);
            });

            // 16. Number Plate (Kerala Yellow Commercial Plate)
            GetOrCreateMat("MAT_Hino_Plate", m =>
            {
                m.mainTexture = texPlate;
                m.color = new Color(1.0f, 0.82f, 0.15f);
                m.SetFloat("_Smoothness", 0.55f);
            });

            // 17. Roof AC Cover (Vibrant Orange AC unit matching Reference 1)
            GetOrCreateMat("MAT_Hino_RoofAc", m =>
            {
                m.mainTexture = texRoofAc != null ? texRoofAc : texLivery;
                m.color = new Color(1.0f, 0.55f, 0.12f);
                m.SetFloat("_Smoothness", 0.60f);
            });

            // 18. Dashboard Wood Panel
            GetOrCreateMat("MAT_Hino_DashboardWood", m =>
            {
                m.mainTexture = texWoodDash;
                m.color = new Color(0.40f, 0.28f, 0.22f);
                m.SetFloat("_Smoothness", 0.55f);
            });

            // 19. Dashboard GPS Display
            GetOrCreateMat("MAT_Hino_DashboardGps", m =>
            {
                m.mainTexture = texGps;
                m.color = Color.white;
                m.EnableKeyword("_EMISSION");
                m.SetColor("_EmissionColor", new Color(0.6f, 0.6f, 0.6f));
            });

            // 20. Luxury Leather Seats Cream
            GetOrCreateMat("MAT_Hino_SeatCream", m =>
            {
                m.color = new Color(0.85f, 0.78f, 0.65f);
                m.SetFloat("_Smoothness", 0.40f);
            });

            // 21. Luxury Leather Seats Brown
            GetOrCreateMat("MAT_Hino_SeatBrown", m =>
            {
                m.color = new Color(0.24f, 0.15f, 0.14f);
                m.SetFloat("_Smoothness", 0.35f);
            });

            // 22. Dark Plastic / Rubber Trim
            GetOrCreateMat("MAT_Hino_DarkTrim", m =>
            {
                m.mainTexture = texBlack;
                m.color = new Color(0.12f, 0.12f, 0.12f);
                m.SetFloat("_Smoothness", 0.30f);
            });

            // 23. Steering Wheel Plastic
            GetOrCreateMat("MAT_Hino_SteeringWheel", m =>
            {
                m.color = new Color(0.12f, 0.12f, 0.14f);
                m.SetFloat("_Smoothness", 0.45f);
            });

            // 24. Chrome / Metallic
            GetOrCreateMat("MAT_Hino_Chrome", m =>
            {
                m.color = new Color(0.90f, 0.90f, 0.92f);
                m.SetFloat("_Metallic", 0.92f);
                m.SetFloat("_Smoothness", 0.92f);
            });

            // 25. Underbody / Chassis
            GetOrCreateMat("MAT_Hino_Underbody", m =>
            {
                m.color = new Color(0.15f, 0.15f, 0.16f);
                m.SetFloat("_Smoothness", 0.20f);
            });

            // 26. Interior Wall / Ceiling Trim
            GetOrCreateMat("MAT_Hino_InteriorTrim", m =>
            {
                m.mainTexture = texInteriorTrim != null ? texInteriorTrim : texGrey;
                m.color = new Color(0.82f, 0.82f, 0.80f);
                m.SetFloat("_Smoothness", 0.30f);
            });

            // 27. Curtains Scalloped
            GetOrCreateMat("MAT_Hino_Curtains", m =>
            {
                m.color = new Color(0.80f, 0.12f, 0.14f);
                m.SetFloat("_Smoothness", 0.25f);
            });

            // 28. Tissue Box
            GetOrCreateMat("MAT_Hino_Tissue", m =>
            {
                m.mainTexture = texTissue;
                m.color = Color.white;
                m.SetFloat("_Smoothness", 0.30f);
            });

            AssetDatabase.SaveAssets();
            Debug.Log($"[HinoIntegration] Generated/Updated {result.Count} PBR materials.");
            return result;
        }

        private static void AssignMaterialsToVisuals(GameObject visualsGo, Dictionary<string, Material> mats)
        {
            var livery = mats["MAT_Hino_BodyLivery"];
            var frontGlass = mats["MAT_Hino_FrontWindshield"];
            var sideGlass = mats["MAT_Hino_SideWindows"];
            var headlamps = mats["MAT_Hino_Headlamps"];
            var tailLightRed = mats["MAT_Hino_TailLights_Red"];
            var indicatorYellow = mats["MAT_Hino_Indicators_Yellow"];
            var reverseWhite = mats["MAT_Hino_Reverse_White"];
            var speedo = mats["MAT_Hino_Gauge_Speedo"];
            var rpm = mats["MAT_Hino_Gauge_Rpm"];
            var airPres = mats["MAT_Hino_Gauge_AirPres"];
            var acControl = mats["MAT_Hino_AcControl"];
            var buttons = mats["MAT_Hino_Buttons"];
            var button2 = mats["MAT_Hino_Button2"];
            var hourMeter = mats["MAT_Hino_HourMeter"];
            var gear = mats["MAT_Hino_GearGuide"];
            var plate = mats["MAT_Hino_Plate"];
            var roofAc = mats["MAT_Hino_RoofAc"];
            var dashWood = mats["MAT_Hino_DashboardWood"];
            var dashGps = mats["MAT_Hino_DashboardGps"];
            var seatCream = mats["MAT_Hino_SeatCream"];
            var seatBrown = mats["MAT_Hino_SeatBrown"];
            var darkTrim = mats["MAT_Hino_DarkTrim"];
            var steeringWheelMat = mats["MAT_Hino_SteeringWheel"];
            var chrome = mats["MAT_Hino_Chrome"];
            var underbody = mats["MAT_Hino_Underbody"];
            var interiorTrim = mats["MAT_Hino_InteriorTrim"];
            var curtains = mats["MAT_Hino_Curtains"];
            var tissue = mats["MAT_Hino_Tissue"];

            int assignedCount = 0;
            foreach (var mr in visualsGo.GetComponentsInChildren<MeshRenderer>(true))
            {
                string name = mr.name.ToLowerInvariant();

                if (name.Contains("glass rubber") || name.Contains("glass support") || name.Contains("driver glass.001"))
                {
                    mr.sharedMaterial = darkTrim;
                }
                else if (name.Contains("main body") || name.Contains("box door") || name.Contains("box locker") || name.Contains("mirror big"))
                {
                    mr.sharedMaterial = livery;
                }
                else if (name.Contains("mirror") || name.Contains("mirro"))
                {
                    mr.sharedMaterial = darkTrim;
                }
                else if (name.Contains("front glass"))
                {
                    mr.sharedMaterial = frontGlass;
                }
                else if (name.Contains("glass") || name.Contains("driver glass"))
                {
                    mr.sharedMaterial = sideGlass;
                }
                else if (name.Contains("steer") || name.Contains("torus"))
                {
                    mr.sharedMaterial = steeringWheelMat;
                }
                else if (name.Contains("circle.010"))
                {
                    mr.sharedMaterial = speedo;
                }
                else if (name.Contains("plane.048"))
                {
                    mr.sharedMaterial = rpm;
                }
                else if (name.Contains("plane.031") || name.Contains("circle.011"))
                {
                    mr.sharedMaterial = airPres;
                }
                else if (name.Contains("plane.005"))
                {
                    mr.sharedMaterial = acControl;
                }
                else if (name.Contains("plane.007"))
                {
                    mr.sharedMaterial = buttons;
                }
                else if (name.Contains("circle.017") || name.Contains("switch") || name.Contains("switcj") || name.Contains("screw"))
                {
                    mr.sharedMaterial = button2;
                }
                else if (name.Contains("hour meter") || name.Contains("hour"))
                {
                    mr.sharedMaterial = hourMeter;
                }
                else if (name.Contains("plane.038"))
                {
                    mr.sharedMaterial = gear;
                }
                else if (name.Contains("plate"))
                {
                    mr.sharedMaterial = plate;
                }
                else if (name.Contains("big ac") || name.Contains("upper ac"))
                {
                    mr.sharedMaterial = roofAc;
                }
                else if (name.Contains("dash") || name.Contains("dashboard"))
                {
                    mr.sharedMaterial = dashWood;
                }
                else if (name.Contains("back light middle"))
                {
                    mr.sharedMaterial = dashGps;
                }
                else if (name.Contains("back light.002") || name.Contains("light glass"))
                {
                    mr.sharedMaterial = reverseWhite;
                }
                else if (name.Contains("back light.003") || name.Contains("round light") || name.Contains("side light"))
                {
                    mr.sharedMaterial = indicatorYellow;
                }
                else if (name.Contains("back light"))
                {
                    mr.sharedMaterial = tailLightRed;
                }
                else if (name.Contains("headlight"))
                {
                    mr.sharedMaterial = headlamps;
                }
                else if (name.Contains("grill") || name.Contains("exausht") || name.Contains("hino") || name.Contains("rm") || name.Contains("handle") || name.Contains("aluminium"))
                {
                    mr.sharedMaterial = chrome;
                }
                else if (name.Contains("base") || name.Contains("suspetion"))
                {
                    mr.sharedMaterial = underbody;
                }
                else if (name.Contains("cube") || name.Contains("seat") || name.Contains("leather") || name.Contains("middle") || name.Contains("leg rest"))
                {
                    var existing = mr.sharedMaterials;
                    if (existing != null && existing.Length >= 2)
                    {
                        mr.sharedMaterials = new Material[] { seatCream, seatBrown };
                    }
                    else
                    {
                        mr.sharedMaterial = seatCream;
                    }
                }
                else if (name.Contains("curtain") || name.Contains("plane.036"))
                {
                    mr.sharedMaterial = curtains;
                }
                else if (name.Contains("tissue"))
                {
                    mr.sharedMaterial = tissue;
                }
                else if (name.Contains("plane.002") || name.Contains("plane.003") || name.Contains("plane.004") || name.Contains("plane.021") || name.Contains("plane.026") || name.Contains("plane.029") || name.Contains("plane.035") || name.Contains("plane.039") || name.Contains("plane.046"))
                {
                    mr.sharedMaterial = darkTrim;
                }
                else if (name.Contains("plane"))
                {
                    mr.sharedMaterial = interiorTrim;
                }
                else
                {
                    mr.sharedMaterial = darkTrim;
                }
                assignedCount++;
            }
            Debug.Log($"[HinoIntegration] Assigned materials to {assignedCount} MeshRenderers.");
        }

        private static void CaptureVerificationScreenshots(GameObject busGo, GameObject camGo)
        {
            if (camGo == null) return;
            var camera = camGo.GetComponent<Camera>();
            var camCtrl = camGo.GetComponent<BusCameraController>();
            if (camera == null || camCtrl == null) return;

            int width = 1920;
            int height = 1080;
            var rt = new RenderTexture(width, height, 24, RenderTextureFormat.ARGB32);
            camera.targetTexture = rt;

            var lateUpdate = typeof(BusCameraController).GetMethod("LateUpdate", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);

            try
            {
                // View 0: Static Rear Chase View (Matching Reference 1)
                camCtrl.SetCameraPreset(0);
                camCtrl.SnapToTarget();
                for (int i = 0; i < 15; i++) if (lateUpdate != null) lateUpdate.Invoke(camCtrl, null);
                camera.Render();
                SaveRenderTexture(rt, "C:/Project/BHARAT BUS SIMULATOR/view0_static_rear_chase.png");
                Debug.Log("[HinoIntegration] Saved view0_static_rear_chase.png");

                // View 1: Cockpit View (Matching Reference 2)
                camCtrl.SetCameraPreset(1);
                camCtrl.SnapToTarget();
                camCtrl.ResetCockpitView();
                for (int i = 0; i < 15; i++) if (lateUpdate != null) lateUpdate.Invoke(camCtrl, null);
                camera.Render();
                SaveRenderTexture(rt, "C:/Project/BHARAT BUS SIMULATOR/view1_driver_cockpit.png");
                Debug.Log("[HinoIntegration] Saved view1_driver_cockpit.png");

                // View 2: 360 Orbit View (Orbit slightly angled)
                camCtrl.SetCameraPreset(2);
                camCtrl.SnapToTarget();
                camCtrl.OnOrbitDrag(new Vector2(160f, 0f));
                for (int i = 0; i < 15; i++) if (lateUpdate != null) lateUpdate.Invoke(camCtrl, null);
                camera.Render();
                SaveRenderTexture(rt, "C:/Project/BHARAT BUS SIMULATOR/view2_360_orbit.png");
                Debug.Log("[HinoIntegration] Saved view2_360_orbit.png");

                // Reset back to Preset 0 (Static Rear Chase)
                camCtrl.SetCameraPreset(0);
                camCtrl.SnapToTarget();
            }
            finally
            {
                camera.targetTexture = null;
                RenderTexture.active = null;
                UnityEngine.Object.DestroyImmediate(rt);
            }
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
    }
}
