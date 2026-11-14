using System;
using System.Collections.Generic;
using System.IO;
using UnityEngine;
using UnityEditor;
using BharatBusSim.Vehicles;
using BharatBusSim.UI;

namespace BharatBusSim.Editor
{
    public static class HinoBusFactory
    {
        public const string FbxPath = "Assets/_Project/Vehicles/Models/Hino/HINO_RM2.fbx";
        public const string WheelsFbxPath = "Assets/_Project/Vehicles/Models/BUS_ECOLINE.fbx";

        public const string LiveryTexPath = "Assets/_Project/Vehicles/Textures/Hino/Rm 2 paint .png";
        public const string FrontGlassTexPath = "Assets/_Project/Vehicles/Textures/Hino/FRONT GLASS.png";
        public const string LightsTexPath = "Assets/_Project/Vehicles/Textures/Hino/Lights0012_350.jpg";
        public const string SpeedoTexPath = "Assets/_Project/Vehicles/Textures/Hino/Main gear kmh.png";
        public const string RpmTexPath = "Assets/_Project/Vehicles/Textures/Hino/rpmpper minute.png";
        public const string AirPressureTexPath = "Assets/_Project/Vehicles/Textures/Hino/air pressure.png";
        public const string AcControlTexPath = "Assets/_Project/Vehicles/Textures/Hino/AC CONTROLL.png";
        public const string ButtonsTexPath = "Assets/_Project/Vehicles/Textures/Hino/AC AND OTHER BUTTON.png";
        public const string Button2TexPath = "Assets/_Project/Vehicles/Textures/Hino/button 2.png";
        public const string HourMeterTexPath = "Assets/_Project/Vehicles/Textures/Hino/HOUR METER.png";
        public const string GearManualTexPath = "Assets/_Project/Vehicles/Textures/Hino/GEAR MANUAL.png";
        public const string PlateTexPath = "Assets/_Project/Vehicles/Textures/Hino/plate no.png";
        public const string BlackTrimTexPath = "Assets/_Project/Vehicles/Textures/Hino/black.png";
        public const string GreyTrimTexPath = "Assets/_Project/Vehicles/Textures/Hino/grey.png";
        public const string BlackGreyTexPath = "Assets/_Project/Vehicles/Textures/Hino/black and grey.png";
        public const string GreyYellowTexPath = "Assets/_Project/Vehicles/Textures/Hino/greay and yellow.png";
        public const string TissueTexPath = "Assets/_Project/Vehicles/Textures/Hino/tissue rtexture.png";

        public struct WheelMeshDef
        {
            public string name;
            public int classificationIndex;
            public bool isLeftSide;
            public bool receivesSteering;
            public Vector3 sourceHubCenter;
            public Vector3 busLocalPosition;
        }

        [MenuItem("BharatBusSim/Showcase/Capture Hino RM 2 Showcase Screenshots")]
        public static void CaptureHinoShowcaseScreenshots()
        {
            string scenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
            var scene = UnityEditor.SceneManagement.EditorSceneManager.OpenScene(scenePath, UnityEditor.SceneManagement.OpenSceneMode.Single);

            var busGo = GameObject.Find("Kerala_Bus_Prototype");
            if (busGo == null)
            {
                Debug.LogError("[HinoShowcase] Kerala_Bus_Prototype not found!");
                return;
            }

            var tempCamGo = new GameObject("ShowcaseCamera");
            var cam = tempCamGo.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.Skybox;

            int width = 1920;
            int height = 1080;
            var rt = new RenderTexture(width, height, 24, RenderTextureFormat.ARGB32);
            cam.targetTexture = rt;

            try
            {
                // 1. Front-Face View (Headlights, Grille, Windshield)
                tempCamGo.transform.position = busGo.transform.TransformPoint(new Vector3(0f, 1.8f, 9.2f));
                tempCamGo.transform.LookAt(busGo.transform.TransformPoint(new Vector3(0f, 1.6f, 4.0f)));
                cam.Render();
                SaveRenderTexture(rt, "C:/Project/BHARAT BUS SIMULATOR/screen_hino_front_face.png");

                // 2. Front 3/4 Quarter View (Wheels, Body Livery, Mirrors)
                tempCamGo.transform.position = busGo.transform.TransformPoint(new Vector3(-4.8f, 2.2f, 8.8f));
                tempCamGo.transform.LookAt(busGo.transform.TransformPoint(new Vector3(0f, 1.6f, 1.0f)));
                cam.Render();
                SaveRenderTexture(rt, "C:/Project/BHARAT BUS SIMULATOR/screen_hino_front_quarter.png");

                // 3. Cabin Aisle View (Passenger Seats, Curtains, Aisle looking toward Driver Cockpit)
                tempCamGo.transform.position = busGo.transform.TransformPoint(new Vector3(0f, 2.25f, -1.5f));
                tempCamGo.transform.LookAt(busGo.transform.TransformPoint(new Vector3(0.5f, 1.8f, 5.0f)));
                cam.Render();
                SaveRenderTexture(rt, "C:/Project/BHARAT BUS SIMULATOR/screen_hino_cabin_aisle.png");

                Debug.Log("[HinoShowcase] Successfully captured Hino RM 2 showcase screenshots!");
            }
            finally
            {
                cam.targetTexture = null;
                RenderTexture.active = null;
                UnityEngine.Object.DestroyImmediate(rt);
                UnityEngine.Object.DestroyImmediate(tempCamGo);
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

        public static GameObject CreateHinoBus(Vector3 spawnPos, Quaternion spawnRot, bool initializeInPark)
        {
            var busGo = new GameObject("Kerala_Bus_Prototype");
            busGo.tag = "Player";
            busGo.transform.position = spawnPos;
            busGo.transform.rotation = spawnRot;

            var rb = busGo.AddComponent<Rigidbody>();
            rb.mass = 10500f;
            rb.linearDamping = 0.05f;
            rb.angularDamping = 1.0f;
            rb.interpolation = RigidbodyInterpolation.Interpolate;

            var bodyCollider = busGo.AddComponent<BoxCollider>();
            bodyCollider.size = new Vector3(2.55f, 2.70f, 12.0f);
            bodyCollider.center = new Vector3(0f, 1.85f, -0.24f);

            GameObject busModelPrefab = AssetDatabase.LoadAssetAtPath<GameObject>(FbxPath);
            if (busModelPrefab == null)
            {
                throw new FileNotFoundException($"[HinoBusFactory] Hino RM 2 FBX model missing at '{FbxPath}'.");
            }

            GameObject visualsGo = (GameObject)PrefabUtility.InstantiatePrefab(busModelPrefab, busGo.transform);
            PrefabUtility.UnpackPrefabInstance(visualsGo, PrefabUnpackMode.Completely, InteractionMode.AutomatedAction);
            visualsGo.name = "KeralaBus12m_Visuals";
            // Scale and center Hino RM 2 model to match realistic 2.55m x 12.0m bus dimensions
            visualsGo.transform.localScale = new Vector3(0.685f, 0.720f, 0.765f);
            visualsGo.transform.localPosition = new Vector3(0f, 2.12f, 0.55f);
            visualsGo.transform.localRotation = Quaternion.identity;

            // Strip existing colliders, rigidbodies, and non-bus objects
            foreach (var col in visualsGo.GetComponentsInChildren<Collider>(true))
            {
                UnityEngine.Object.DestroyImmediate(col);
            }
            foreach (var extraRb in visualsGo.GetComponentsInChildren<Rigidbody>(true))
            {
                if (extraRb != rb) UnityEngine.Object.DestroyImmediate(extraRb);
            }

            // Remove non-bus objects (modeling backdrop plane, lights, cameras, pleated side curtains, and internal driver window sliding box)
            for (int i = visualsGo.transform.childCount - 1; i >= 0; i--)
            {
                Transform child = visualsGo.transform.GetChild(i);
                string lower = child.name.ToLowerInvariant();
                if (lower.Contains("curtain") || lower.Contains("plane.001") || lower.Contains("camera") || lower.StartsWith("lamp") || lower.Contains("area") || lower.Contains("driver glass") || lower == "plane.049")
                {
                    UnityEngine.Object.DestroyImmediate(child.gameObject);
                }
            }

            // Setup and apply realistic PBR materials
            SetupMaterials(visualsGo);

            // Separate combined ban/pelek meshes into 6 visual wheels matching Hino wheel arches
            var visualWheels = RigHinoWheels(busGo.transform);

            var busController = busGo.AddComponent<BusController>();
            busController.initializeInPark = initializeInPark;

            // Create 6 physical WheelColliders matching Hino wheel arches (FL, FR, ML, MR, RL, RR)
            var flCol = CreatePhysicsWheelCollider(busGo, "WheelCollider_FL", new Vector3(-1.10f, 0.62f, 3.00f), 180000f, 18000f);
            var frCol = CreatePhysicsWheelCollider(busGo, "WheelCollider_FR", new Vector3(1.10f, 0.62f, 3.00f), 180000f, 18000f);
            var mlCol = CreatePhysicsWheelCollider(busGo, "WheelCollider_ML", new Vector3(-1.08f, 0.62f, -2.81f), 170000f, 16000f);
            var mrCol = CreatePhysicsWheelCollider(busGo, "WheelCollider_MR", new Vector3(1.08f, 0.62f, -2.81f), 170000f, 16000f);
            var rlCol = CreatePhysicsWheelCollider(busGo, "WheelCollider_RL", new Vector3(-0.78f, 0.62f, -2.81f), 160000f, 15000f);
            var rrCol = CreatePhysicsWheelCollider(busGo, "WheelCollider_RR", new Vector3(0.78f, 0.62f, -2.81f), 160000f, 15000f);

            busController.frontLeftCollider = flCol;
            busController.frontRightCollider = frCol;
            busController.midLeftCollider = mlCol;
            busController.midRightCollider = mrCol;
            busController.rearLeftCollider = rlCol;
            busController.rearRightCollider = rrCol;

            ConfigureBusWheel(busGo, "WheelSync_FL", flCol, visualWheels["Wheel_FL"], receivesSteering: true, isLeftSide: true, new Vector3(-1.10f, 0.52f, 3.00f));
            ConfigureBusWheel(busGo, "WheelSync_FR", frCol, visualWheels["Wheel_FR"], receivesSteering: true, isLeftSide: false, new Vector3(1.10f, 0.52f, 3.00f));
            ConfigureBusWheel(busGo, "WheelSync_ML", mlCol, visualWheels["Wheel_ML"], receivesSteering: false, isLeftSide: true, new Vector3(-1.08f, 0.52f, -2.81f));
            ConfigureBusWheel(busGo, "WheelSync_MR", mrCol, visualWheels["Wheel_MR"], receivesSteering: false, isLeftSide: false, new Vector3(1.08f, 0.52f, -2.81f));
            ConfigureBusWheel(busGo, "WheelSync_RL", rlCol, visualWheels["Wheel_RL"], receivesSteering: false, isLeftSide: true, new Vector3(-0.78f, 0.52f, -2.81f));
            ConfigureBusWheel(busGo, "WheelSync_RR", rrCol, visualWheels["Wheel_RR"], receivesSteering: false, isLeftSide: false, new Vector3(0.78f, 0.52f, -2.81f));

            // Headlamps
            var headlampL = CreateHeadlamp(busGo, "Headlamp_L", new Vector3(-0.95f, 0.85f, 5.85f));
            var headlampR = CreateHeadlamp(busGo, "Headlamp_R", new Vector3(0.95f, 0.85f, 5.85f));
            busController.headlamps = new[] { headlampL, headlampR };

            // Interior Cabin Lighting
            CreateInteriorLighting(busGo);

            // Connect Cockpit Dashboard Sync with Hino 3D steering wheel (Torus.011)
            SetupCockpitSync(busGo, visualsGo, busController);

            // Setup Vehicle Audio System (Diesel Engine, Reverse Warning, Indicators, Air Brakes, Wipers)
            var audioCtrl = SetupBusAudio(busGo, busController);

            // Setup Functional Front Windshield Wipers
            SetupWiperAssembly(busGo, visualsGo, busController, audioCtrl);

            // Setup Working Indicators & Dynamic Signal Lights
            SetupIndicatorVisuals(busGo, visualsGo, busController);

            // Chase Camera
            var camGo = new GameObject("ChaseCamera");
            camGo.tag = "MainCamera";
            var camera = camGo.AddComponent<Camera>();
            camera.clearFlags = CameraClearFlags.Skybox;
            camGo.AddComponent<AudioListener>();

            var camFollow = camGo.AddComponent<BusCameraController>();
            camFollow.target = busGo.transform;
            camFollow.SetCockpitEyeSettings(2.16f, 0.70f, 4.65f, 12.0f);
            camFollow.SnapToTarget();

            // HUD
            var hudGo = new GameObject("BusDashboardHUD");
            var hud = hudGo.AddComponent<BusDashboardHUD>();
            hud.busController = busController;
            hud.cameraController = camFollow;

            // EventSystem
            var esGo = new GameObject("EventSystem");
            esGo.AddComponent<UnityEngine.EventSystems.EventSystem>();
            esGo.AddComponent<UnityEngine.InputSystem.UI.InputSystemUIInputModule>();

            return busGo;
        }

        private static void SetupCockpitSync(GameObject busGo, GameObject visualsGo, BusController controller)
        {
            var syncGo = new GameObject("CockpitDashboardSync");
            syncGo.transform.SetParent(busGo.transform, false);
            var sync = syncGo.AddComponent<CockpitDashboardSync>();
            sync.busController = controller;

            Transform steerWheel = visualsGo.transform.Find("Torus.011");
            if (steerWheel != null)
            {
                // Create dedicated pivot for steering wheel rotation
                GameObject pivotGo = new GameObject("Cockpit_SteeringWheel_Pivot");
                pivotGo.transform.SetParent(steerWheel.parent, false);
                pivotGo.transform.localPosition = steerWheel.localPosition;
                pivotGo.transform.localRotation = steerWheel.localRotation;
                steerWheel.SetParent(pivotGo.transform, true);

                sync.steeringWheel = pivotGo.transform;
                sync.maxSteerWheelRotation = 450f;
            }
        }

        private static void SetupMaterials(GameObject visualsGo)
        {
            Shader litShader = Shader.Find("Universal Render Pipeline/Lit");
            if (litShader == null) litShader = Shader.Find("Standard");

            Texture2D liveryTex = AssetDatabase.LoadAssetAtPath<Texture2D>(LiveryTexPath);
            Texture2D frontGlassTex = AssetDatabase.LoadAssetAtPath<Texture2D>(FrontGlassTexPath);
            Texture2D lightsTex = AssetDatabase.LoadAssetAtPath<Texture2D>(LightsTexPath);
            Texture2D speedoTex = AssetDatabase.LoadAssetAtPath<Texture2D>(SpeedoTexPath);
            Texture2D rpmTex = AssetDatabase.LoadAssetAtPath<Texture2D>(RpmTexPath);
            Texture2D airPresTex = AssetDatabase.LoadAssetAtPath<Texture2D>(AirPressureTexPath);
            Texture2D acControlTex = AssetDatabase.LoadAssetAtPath<Texture2D>(AcControlTexPath);
            Texture2D buttonsTex = AssetDatabase.LoadAssetAtPath<Texture2D>(ButtonsTexPath);
            Texture2D button2Tex = AssetDatabase.LoadAssetAtPath<Texture2D>(Button2TexPath);
            Texture2D hourMeterTex = AssetDatabase.LoadAssetAtPath<Texture2D>(HourMeterTexPath);
            Texture2D gearTex = AssetDatabase.LoadAssetAtPath<Texture2D>(GearManualTexPath);
            Texture2D plateTex = AssetDatabase.LoadAssetAtPath<Texture2D>(PlateTexPath);
            Texture2D blackTex = AssetDatabase.LoadAssetAtPath<Texture2D>(BlackTrimTexPath);
            Texture2D greyTex = AssetDatabase.LoadAssetAtPath<Texture2D>(GreyTrimTexPath);
            Texture2D blackGreyTex = AssetDatabase.LoadAssetAtPath<Texture2D>(BlackGreyTexPath);
            Texture2D greyYellowTex = AssetDatabase.LoadAssetAtPath<Texture2D>(GreyYellowTexPath);
            Texture2D tissueTex = AssetDatabase.LoadAssetAtPath<Texture2D>(TissueTexPath);

            var liveryMat = new Material(litShader)
            {
                name = "Mat_Hino_Livery",
                mainTexture = liveryTex,
                color = Color.white
            };
            liveryMat.SetFloat("_Smoothness", 0.78f);
            liveryMat.SetFloat("_Metallic", 0.08f);

            var glassMat = new Material(litShader)
            {
                name = "Mat_Hino_Glass",
                color = new Color(0.12f, 0.20f, 0.28f, 0.32f)
            };
            glassMat.SetFloat("_Smoothness", 0.95f);
            glassMat.SetFloat("_Surface", 1.0f);
            glassMat.SetFloat("_Blend", 0.0f);
            glassMat.SetFloat("_SrcBlend", 5.0f);
            glassMat.SetFloat("_DstBlend", 10.0f);
            glassMat.SetFloat("_SrcBlendAlpha", 1.0f);
            glassMat.SetFloat("_DstBlendAlpha", 10.0f);
            glassMat.SetFloat("_ZWrite", 0.0f);
            glassMat.SetOverrideTag("RenderType", "Transparent");
            glassMat.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Transparent;
            glassMat.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");

            var frontGlassMat = new Material(litShader)
            {
                name = "Mat_Hino_FrontGlass",
                mainTexture = frontGlassTex,
                color = new Color(1f, 1f, 1f, 0.70f)
            };
            frontGlassMat.SetFloat("_Smoothness", 0.95f);
            frontGlassMat.SetFloat("_Surface", 1.0f);
            frontGlassMat.SetFloat("_Blend", 0.0f);
            frontGlassMat.SetFloat("_SrcBlend", 5.0f);
            frontGlassMat.SetFloat("_DstBlend", 10.0f);
            frontGlassMat.SetFloat("_SrcBlendAlpha", 1.0f);
            frontGlassMat.SetFloat("_DstBlendAlpha", 10.0f);
            frontGlassMat.SetFloat("_ZWrite", 0.0f);
            frontGlassMat.SetOverrideTag("RenderType", "Transparent");
            frontGlassMat.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Transparent;
            frontGlassMat.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");

            var lightsMat = new Material(litShader)
            {
                name = "Mat_Hino_Lights",
                mainTexture = lightsTex,
                color = Color.white
            };
            lightsMat.SetFloat("_Smoothness", 0.90f);
            lightsMat.SetFloat("_Metallic", 0.20f);

            var redLightMat = new Material(litShader)
            {
                name = "Mat_Hino_RedLight",
                color = new Color(0.9f, 0.05f, 0.05f)
            };
            redLightMat.EnableKeyword("_EMISSION");
            redLightMat.SetColor("_EmissionColor", new Color(1.2f, 0.1f, 0.1f));

            var yellowLightMat = new Material(litShader)
            {
                name = "Mat_Hino_YellowLight",
                color = new Color(1.0f, 0.75f, 0.05f)
            };
            yellowLightMat.EnableKeyword("_EMISSION");
            yellowLightMat.SetColor("_EmissionColor", new Color(1.2f, 0.9f, 0.1f));

            var whiteLightMat = new Material(litShader)
            {
                name = "Mat_Hino_WhiteLight",
                color = new Color(1.0f, 0.98f, 0.90f)
            };
            whiteLightMat.EnableKeyword("_EMISSION");
            whiteLightMat.SetColor("_EmissionColor", new Color(1.5f, 1.4f, 1.2f));

            var chromeMat = new Material(litShader)
            {
                name = "Mat_Hino_Chrome",
                color = new Color(0.92f, 0.92f, 0.94f)
            };
            chromeMat.SetFloat("_Metallic", 0.88f);
            chromeMat.SetFloat("_Smoothness", 0.92f);

            var darkTrimMat = new Material(litShader)
            {
                name = "Mat_Hino_DarkTrim",
                mainTexture = blackTex,
                color = new Color(0.12f, 0.12f, 0.12f)
            };
            darkTrimMat.SetFloat("_Smoothness", 0.35f);

            var underbodyMat = new Material(litShader)
            {
                name = "Mat_Hino_Underbody",
                mainTexture = blackGreyTex,
                color = new Color(0.15f, 0.15f, 0.16f)
            };
            underbodyMat.SetFloat("_Smoothness", 0.20f);

            var dashMat = new Material(litShader)
            {
                name = "Mat_Hino_Dashboard",
                mainTexture = greyTex,
                color = new Color(0.20f, 0.20f, 0.22f)
            };
            dashMat.SetFloat("_Smoothness", 0.40f);

            var speedoMat = new Material(litShader)
            {
                name = "Mat_Hino_Speedo",
                mainTexture = speedoTex,
                color = Color.white
            };
            speedoMat.SetFloat("_Smoothness", 0.60f);

            var rpmMat = new Material(litShader)
            {
                name = "Mat_Hino_Rpm",
                mainTexture = rpmTex,
                color = Color.white
            };
            rpmMat.SetFloat("_Smoothness", 0.60f);

            var airPresMat = new Material(litShader)
            {
                name = "Mat_Hino_AirPres",
                mainTexture = airPresTex,
                color = Color.white
            };
            airPresMat.SetFloat("_Smoothness", 0.60f);

            var acControlMat = new Material(litShader)
            {
                name = "Mat_Hino_AcControl",
                mainTexture = acControlTex,
                color = Color.white
            };

            var buttonsMat = new Material(litShader)
            {
                name = "Mat_Hino_Buttons",
                mainTexture = buttonsTex,
                color = Color.white
            };

            var button2Mat = new Material(litShader)
            {
                name = "Mat_Hino_Button2",
                mainTexture = button2Tex,
                color = Color.white
            };

            var hourMeterMat = new Material(litShader)
            {
                name = "Mat_Hino_HourMeter",
                mainTexture = hourMeterTex,
                color = Color.white
            };

            var gearMat = new Material(litShader)
            {
                name = "Mat_Hino_Gear",
                mainTexture = gearTex,
                color = Color.white
            };

            var plateMat = new Material(litShader)
            {
                name = "Mat_Hino_Plate",
                mainTexture = plateTex,
                color = Color.white
            };

            var leatherSeatsMat = new Material(litShader)
            {
                name = "Mat_Hino_LeatherSeats",
                color = new Color(0.35f, 0.12f, 0.12f) // Luxury Kerala coach burgundy upholstery
            };
            leatherSeatsMat.SetFloat("_Smoothness", 0.45f);

            var curtainsMat = new Material(litShader)
            {
                name = "Mat_Hino_Curtains",
                color = new Color(0.65f, 0.50f, 0.20f) // Golden amber luxury curtains
            };
            curtainsMat.SetFloat("_Smoothness", 0.25f);

            var steeringWheelMat = new Material(litShader)
            {
                name = "Mat_Hino_SteeringWheel",
                color = new Color(0.12f, 0.12f, 0.14f)
            };
            steeringWheelMat.SetFloat("_Smoothness", 0.50f);

            var interiorTrimMat = new Material(litShader)
            {
                name = "Mat_Hino_InteriorTrim",
                mainTexture = greyYellowTex,
                color = new Color(0.85f, 0.85f, 0.82f)
            };
            interiorTrimMat.SetFloat("_Smoothness", 0.30f);

            var tissueMat = new Material(litShader)
            {
                name = "Mat_Hino_Tissue",
                mainTexture = tissueTex,
                color = Color.white
            };

            // Intelligent name-based material assignment across all 139 mesh objects
            foreach (var mr in visualsGo.GetComponentsInChildren<MeshRenderer>(true))
            {
                string name = mr.name.ToLower();

                if (name == "main body" || name.StartsWith("box door") || name.StartsWith("box locker"))
                {
                    mr.sharedMaterial = liveryMat;
                }
                else if (name.Contains("glass"))
                {
                    if (name.Contains("front")) mr.sharedMaterial = frontGlassMat;
                    else mr.sharedMaterial = glassMat;
                }
                else if (name.Contains("curtain"))
                {
                    mr.enabled = false;
                }
                else if (name.StartsWith("headlight") || name == "headlight")
                {
                    mr.sharedMaterial = lightsMat;
                }
                else if (name.StartsWith("back light"))
                {
                    if (name.Contains(".002")) mr.sharedMaterial = whiteLightMat;
                    else if (name.Contains(".003")) mr.sharedMaterial = yellowLightMat;
                    else mr.sharedMaterial = redLightMat;
                }
                else if (name.Contains("round light") || name.Contains("side light"))
                {
                    mr.sharedMaterial = yellowLightMat;
                }
                else if (name.Contains("grill") || name.Contains("exausht") || name.Contains("hino") || name.Contains("rm") || name.Contains("handle") || name.Contains("aluminium"))
                {
                    mr.sharedMaterial = chromeMat;
                }
                else if (name.Contains("plate"))
                {
                    mr.sharedMaterial = plateMat;
                }
                else if (name.Contains("dash") || name == "dashboard")
                {
                    mr.sharedMaterial = dashMat;
                }
                else if (name.StartsWith("torus"))
                {
                    mr.sharedMaterial = steeringWheelMat;
                }
                else if (name == "circle.009")
                {
                    mr.sharedMaterial = speedoMat;
                }
                else if (name == "circle.010")
                {
                    mr.sharedMaterial = rpmMat;
                }
                else if (name == "circle.011")
                {
                    mr.sharedMaterial = airPresMat;
                }
                else if (name == "hour meter" || name.Contains("hour"))
                {
                    mr.sharedMaterial = hourMeterMat;
                }
                else if (name == "tissue")
                {
                    mr.sharedMaterial = tissueMat;
                }
                else if (name.Contains("brake") || name.Contains("pedal") || name.Contains("rubber"))
                {
                    mr.sharedMaterial = darkTrimMat;
                }
                else if (name == "base" || name.Contains("suspetion"))
                {
                    mr.sharedMaterial = underbodyMat;
                }
                else if (name.StartsWith("cube") || name.Contains("seat") || name.Contains("leather") || name == "middle" || name == "leg rest")
                {
                    mr.sharedMaterial = leatherSeatsMat;
                }
                else if (name.Contains("switch") || name.Contains("switcj") || name.Contains("screw"))
                {
                    mr.sharedMaterial = button2Mat;
                }
                else if (name.Contains("ac"))
                {
                    mr.sharedMaterial = acControlMat;
                }
                else if (name.StartsWith("plane"))
                {
                    mr.sharedMaterial = interiorTrimMat;
                }
                else
                {
                    mr.sharedMaterial = darkTrimMat;
                }
            }
        }

        private static Dictionary<string, Transform> RigHinoWheels(Transform busTransform)
        {
            Shader litShader = Shader.Find("Universal Render Pipeline/Lit");
            if (litShader == null) litShader = Shader.Find("Standard");

            string tireMatPath = "Assets/_Project/Vehicles/Models/Hino/Mat_Hino_Tire.mat";
            string rimMatPath = "Assets/_Project/Vehicles/Models/Hino/Mat_Hino_Rim.mat";

            Material tireMat = AssetDatabase.LoadAssetAtPath<Material>(tireMatPath);
            if (tireMat == null)
            {
                tireMat = new Material(litShader)
                {
                    name = "Mat_Hino_Tire",
                    color = new Color(0.12f, 0.12f, 0.12f)
                };
                tireMat.SetFloat("_Smoothness", 0.25f);
                AssetDatabase.CreateAsset(tireMat, tireMatPath);
            }

            Material rimMat = AssetDatabase.LoadAssetAtPath<Material>(rimMatPath);
            if (rimMat == null)
            {
                rimMat = new Material(litShader)
                {
                    name = "Mat_Hino_Rim",
                    color = new Color(0.88f, 0.88f, 0.90f)
                };
                rimMat.SetFloat("_Metallic", 0.70f);
                rimMat.SetFloat("_Smoothness", 0.80f);
                AssetDatabase.CreateAsset(rimMat, rimMatPath);
            }

            GameObject wheelsFbxPrefab = AssetDatabase.LoadAssetAtPath<GameObject>(WheelsFbxPath);
            if (wheelsFbxPrefab == null)
            {
                throw new FileNotFoundException($"[HinoBusFactory] Wheel asset FBX missing at '{WheelsFbxPath}'.");
            }

            GameObject wheelsTemp = (GameObject)PrefabUtility.InstantiatePrefab(wheelsFbxPrefab);
            wheelsTemp.transform.position = Vector3.zero;
            wheelsTemp.transform.rotation = Quaternion.identity;
            wheelsTemp.transform.localScale = Vector3.one;

            Transform banT = wheelsTemp.transform.Find("ban");
            Transform pelekT = wheelsTemp.transform.Find("pelek");

            if (banT == null || pelekT == null)
            {
                UnityEngine.Object.DestroyImmediate(wheelsTemp);
                throw new MissingComponentException("[HinoBusFactory] 'ban' or 'pelek' mesh missing from wheel model!");
            }

            Mesh banMesh = banT.GetComponent<MeshFilter>().sharedMesh;
            Mesh pelekMesh = pelekT.GetComponent<MeshFilter>().sharedMesh;

            WheelMeshDef[] wheelDefs = new WheelMeshDef[]
            {
                new WheelMeshDef { name = "Wheel_FL", classificationIndex = 0, isLeftSide = true,  receivesSteering = true,  sourceHubCenter = new Vector3(-1.047f, 0.517f, 3.485f), busLocalPosition = new Vector3(-1.10f, 0.52f, 3.00f) },
                new WheelMeshDef { name = "Wheel_FR", classificationIndex = 1, isLeftSide = false, receivesSteering = true,  sourceHubCenter = new Vector3(1.047f, 0.517f, 3.485f),  busLocalPosition = new Vector3(1.10f, 0.52f, 3.00f) },
                new WheelMeshDef { name = "Wheel_ML", classificationIndex = 2, isLeftSide = true,  receivesSteering = false, sourceHubCenter = new Vector3(-1.154f, 0.517f, -2.596f), busLocalPosition = new Vector3(-1.08f, 0.52f, -2.81f) },
                new WheelMeshDef { name = "Wheel_RL", classificationIndex = 3, isLeftSide = true,  receivesSteering = false, sourceHubCenter = new Vector3(-0.791f, 0.517f, -2.595f), busLocalPosition = new Vector3(-0.78f, 0.52f, -2.81f) },
                new WheelMeshDef { name = "Wheel_MR", classificationIndex = 4, isLeftSide = false, receivesSteering = false, sourceHubCenter = new Vector3(1.154f, 0.517f, -2.596f),  busLocalPosition = new Vector3(1.08f, 0.52f, -2.81f) },
                new WheelMeshDef { name = "Wheel_RR", classificationIndex = 5, isLeftSide = false, receivesSteering = false, sourceHubCenter = new Vector3(0.791f, 0.517f, -2.595f),  busLocalPosition = new Vector3(0.78f, 0.52f, -2.81f) }
            };

            var result = new Dictionary<string, Transform>();

            foreach (var def in wheelDefs)
            {
                GameObject wheelGo = new GameObject(def.name);
                wheelGo.transform.SetParent(busTransform, false);
                wheelGo.transform.localPosition = def.busLocalPosition;
                wheelGo.transform.localRotation = Quaternion.identity;
                wheelGo.transform.localScale = Vector3.one;

                Mesh wheelMesh = BuildSubWheelMesh(banMesh, banT, pelekMesh, pelekT, def.classificationIndex, def.isLeftSide, def.sourceHubCenter, out bool hasRim);

                var mf = wheelGo.AddComponent<MeshFilter>();
                mf.sharedMesh = wheelMesh;

                var mr = wheelGo.AddComponent<MeshRenderer>();
                if (hasRim)
                {
                    mr.sharedMaterials = new Material[] { tireMat, rimMat };
                }
                else
                {
                    mr.sharedMaterials = new Material[] { tireMat };
                }

                result[def.name] = wheelGo.transform;
            }

            UnityEngine.Object.DestroyImmediate(wheelsTemp);
            return result;
        }

        private static Mesh BuildSubWheelMesh(Mesh banMesh, Transform banT, Mesh pelekMesh, Transform pelekT, int classIdx, bool isLeftSide, Vector3 hubCenter, out bool hasRim)
        {
            List<Vector3> vertices = new List<Vector3>();
            List<Vector3> normals = new List<Vector3>();
            List<Vector2> uvs = new List<Vector2>();
            List<int> tireTris = new List<int>();
            List<int> rimTris = new List<int>();

            Quaternion leftSidePreRotation = Quaternion.Euler(0f, 180f, 0f);

            ExtractTrianglesForWheel(banMesh, banT, classIdx, isLeftSide, hubCenter, leftSidePreRotation, vertices, normals, uvs, tireTris);
            ExtractTrianglesForWheel(pelekMesh, pelekT, classIdx, isLeftSide, hubCenter, leftSidePreRotation, vertices, normals, uvs, rimTris);

            Mesh mesh = new Mesh();
            mesh.name = $"Mesh_Hino_Wheel_{classIdx}";
            mesh.SetVertices(vertices);
            mesh.SetNormals(normals);
            mesh.SetUVs(0, uvs);

            if (rimTris.Count > 0)
            {
                mesh.subMeshCount = 2;
                mesh.SetTriangles(tireTris, 0);
                mesh.SetTriangles(rimTris, 1);
                hasRim = true;
            }
            else
            {
                mesh.subMeshCount = 1;
                mesh.SetTriangles(tireTris, 0);
                hasRim = false;
            }

            mesh.RecalculateBounds();

            string assetPath = $"Assets/_Project/Vehicles/Models/Hino/Mesh_Hino_Wheel_{classIdx}.asset";
            Mesh diskMesh = AssetDatabase.LoadAssetAtPath<Mesh>(assetPath);
            if (diskMesh == null)
            {
                AssetDatabase.CreateAsset(mesh, assetPath);
                AssetDatabase.SaveAssets();
                return mesh;
            }
            else
            {
                diskMesh.Clear();
                diskMesh.subMeshCount = mesh.subMeshCount;
                diskMesh.SetVertices(vertices);
                diskMesh.SetNormals(normals);
                diskMesh.SetUVs(0, uvs);
                diskMesh.SetTriangles(tireTris, 0);
                if (rimTris.Count > 0)
                {
                    diskMesh.SetTriangles(rimTris, 1);
                }
                diskMesh.RecalculateBounds();
                EditorUtility.SetDirty(diskMesh);
                AssetDatabase.SaveAssets();
                return diskMesh;
            }
        }

        private static int ClassifyCentroid(Vector3 c)
        {
            if (c.z > 0f)
            {
                return (c.x < 0f) ? 0 : 1; // 0=FL, 1=FR
            }
            else
            {
                if (c.x < 0f)
                {
                    return (c.x < -0.95f) ? 2 : 3; // 2=ML, 3=RL
                }
                else
                {
                    return (c.x > 0.95f) ? 4 : 5; // 4=MR, 5=RR
                }
            }
        }

        private static void ExtractTrianglesForWheel(Mesh sourceMesh, Transform sourceT, int classIdx, bool isLeftSide, Vector3 hubCenter, Quaternion leftSidePreRot,
            List<Vector3> outVerts, List<Vector3> outNormals, List<Vector2> outUVs, List<int> outTris)
        {
            Vector3[] srcVerts = sourceMesh.vertices;
            Vector3[] srcNormals = sourceMesh.normals;
            Vector2[] srcUVs = sourceMesh.uv;
            int[] srcTris = sourceMesh.triangles;
            int triCount = srcTris.Length / 3;

            Dictionary<int, int> srcToOutIndex = new Dictionary<int, int>();

            for (int i = 0; i < triCount; i++)
            {
                int i0 = srcTris[i * 3];
                int i1 = srcTris[i * 3 + 1];
                int i2 = srcTris[i * 3 + 2];

                Vector3 v0 = sourceT.TransformPoint(srcVerts[i0]);
                Vector3 v1 = sourceT.TransformPoint(srcVerts[i1]);
                Vector3 v2 = sourceT.TransformPoint(srcVerts[i2]);
                Vector3 centroid = (v0 + v1 + v2) / 3f;

                if (ClassifyCentroid(centroid) == classIdx)
                {
                    Vector3 n0 = sourceT.TransformDirection((srcNormals != null && srcNormals.Length > i0) ? srcNormals[i0] : Vector3.up);
                    Vector3 n1 = sourceT.TransformDirection((srcNormals != null && srcNormals.Length > i1) ? srcNormals[i1] : Vector3.up);
                    Vector3 n2 = sourceT.TransformDirection((srcNormals != null && srcNormals.Length > i2) ? srcNormals[i2] : Vector3.up);

                    int o0 = GetOrCreateVertex(i0, v0, n0, srcUVs, isLeftSide, hubCenter, leftSidePreRot, srcToOutIndex, outVerts, outNormals, outUVs);
                    int o1 = GetOrCreateVertex(i1, v1, n1, srcUVs, isLeftSide, hubCenter, leftSidePreRot, srcToOutIndex, outVerts, outNormals, outUVs);
                    int o2 = GetOrCreateVertex(i2, v2, n2, srcUVs, isLeftSide, hubCenter, leftSidePreRot, srcToOutIndex, outVerts, outNormals, outUVs);

                    outTris.Add(o0);
                    outTris.Add(o1);
                    outTris.Add(o2);
                }
            }
        }

        private static int GetOrCreateVertex(int srcIdx, Vector3 posWorld, Vector3 normWorld, Vector2[] srcUVs, bool isLeftSide, Vector3 hubCenter, Quaternion leftSidePreRot,
            Dictionary<int, int> map, List<Vector3> outVerts, List<Vector3> outNormals, List<Vector2> outUVs)
        {
            if (map.TryGetValue(srcIdx, out int existing))
            {
                return existing;
            }

            Vector3 wheelPos;
            Vector3 wheelNorm;

            if (isLeftSide)
            {
                wheelPos = leftSidePreRot * (posWorld - hubCenter);
                wheelNorm = leftSidePreRot * normWorld;
            }
            else
            {
                wheelPos = posWorld - hubCenter;
                wheelNorm = normWorld;
            }

            int newIdx = outVerts.Count;
            outVerts.Add(wheelPos);
            outNormals.Add(wheelNorm);
            outUVs.Add((srcUVs != null && srcUVs.Length > srcIdx) ? srcUVs[srcIdx] : Vector2.zero);

            map[srcIdx] = newIdx;
            return newIdx;
        }

        private static WheelCollider CreatePhysicsWheelCollider(GameObject bus, string name, Vector3 localPos, float springRate = 175000f, float damperRate = 16000f)
        {
            var wheelGo = new GameObject(name);
            wheelGo.transform.SetParent(bus.transform, false);
            wheelGo.transform.localPosition = localPos;

            var col = wheelGo.AddComponent<WheelCollider>();
            col.radius = 0.52f;
            col.wheelDampingRate = 0.5f;
            col.suspensionDistance = 0.20f;

            JointSpring spring = new JointSpring
            {
                spring = springRate,
                damper = damperRate,
                targetPosition = 0.5f
            };
            col.suspensionSpring = spring;

            WheelFrictionCurve fFriction = col.forwardFriction;
            fFriction.stiffness = 1.5f;
            fFriction.extremumSlip = 0.4f;
            fFriction.extremumValue = 1.0f;
            fFriction.asymptoteSlip = 0.8f;
            fFriction.asymptoteValue = 0.65f;
            col.forwardFriction = fFriction;

            WheelFrictionCurve sFriction = col.sidewaysFriction;
            sFriction.stiffness = 1.8f;
            sFriction.extremumSlip = 0.35f;
            sFriction.extremumValue = 1.0f;
            sFriction.asymptoteSlip = 0.7f;
            sFriction.asymptoteValue = 0.6f;
            col.sidewaysFriction = sFriction;

            return col;
        }

        private static void ConfigureBusWheel(GameObject bus, string name, WheelCollider col, Transform visualWheel, bool receivesSteering, bool isLeftSide, Vector3 restPos)
        {
            var syncGo = new GameObject(name);
            syncGo.transform.SetParent(bus.transform, false);
            syncGo.transform.localPosition = restPos;

            var sync = syncGo.AddComponent<BusWheel>();
            sync.wheelCollider = col;
            sync.visualTransform = visualWheel;
            sync.receivesSteering = receivesSteering;
            sync.isLeftSide = isLeftSide;
            sync.useExplicitRestPosition = true;
            sync.explicitRestPosition = restPos;
        }

        private static Light CreateHeadlamp(GameObject bus, string name, Vector3 localPos)
        {
            var lampGo = new GameObject(name);
            lampGo.transform.SetParent(bus.transform, false);
            lampGo.transform.localPosition = localPos;
            lampGo.transform.localRotation = Quaternion.identity;

            var light = lampGo.AddComponent<Light>();
            light.type = LightType.Spot;
            light.color = new Color(1.0f, 0.95f, 0.85f);
            light.range = 50f;
            light.spotAngle = 65f;
            light.intensity = 2.5f;
            light.shadows = LightShadows.None;
            light.enabled = false;

            return light;
        }

        private static void CreateInteriorLighting(GameObject bus)
        {
            Transform existing = bus.transform.Find("InteriorCabinLights");
            if (existing != null) UnityEngine.Object.DestroyImmediate(existing.gameObject);

            var interiorLightingGo = new GameObject("InteriorCabinLights");
            interiorLightingGo.transform.SetParent(bus.transform, false);

            float[] zPositions = new float[] { 2.5f, 0.0f, -2.5f, -4.5f };
            for (int i = 0; i < zPositions.Length; i++)
            {
                var lightGo = new GameObject($"CabinCeilingLight_{i + 1}");
                lightGo.transform.SetParent(interiorLightingGo.transform, false);
                lightGo.transform.localPosition = new Vector3(0f, 2.75f, zPositions[i]);

                var light = lightGo.AddComponent<Light>();
                light.type = LightType.Point;
                light.color = new Color(1.0f, 0.94f, 0.86f);
                light.range = 5.0f;
                light.intensity = 1.8f;
                light.shadows = LightShadows.None;
            }
        }

        private static BusAudioController SetupBusAudio(GameObject busGo, BusController controller)
        {
            var audioCtrl = busGo.AddComponent<BusAudioController>();
            audioCtrl.busController = controller;

            audioCtrl.dieselEngineClip = AssetDatabase.LoadAssetAtPath<AudioClip>("Assets/_Project/Audio/SFX/diesel_engine_loop.wav");
            audioCtrl.reverseWarningClip = AssetDatabase.LoadAssetAtPath<AudioClip>("Assets/_Project/Audio/SFX/reverse_sound.mp3");
            audioCtrl.indicatorClip = AssetDatabase.LoadAssetAtPath<AudioClip>("Assets/_Project/Audio/SFX/indicator_sound.mp3");
            audioCtrl.airBrakeClip = AssetDatabase.LoadAssetAtPath<AudioClip>("Assets/_Project/Audio/SFX/air_brake_release.wav");
            audioCtrl.wiperClip = AssetDatabase.LoadAssetAtPath<AudioClip>("Assets/_Project/Audio/SFX/wiper_sweep.wav");

            audioCtrl.SetupAudioSources();
            return audioCtrl;
        }

        private static void SetupWiperAssembly(GameObject busGo, GameObject visualsGo, BusController controller, BusAudioController audioCtrl)
        {
            Shader litShader = Shader.Find("Universal Render Pipeline/Lit");
            if (litShader == null) litShader = Shader.Find("Standard");

            Material wiperMat = new Material(litShader)
            {
                name = "Mat_Hino_WiperBlack",
                color = new Color(0.10f, 0.10f, 0.11f)
            };
            wiperMat.SetFloat("_Smoothness", 0.35f);

            var wiperAssemblyGo = new GameObject("WindshieldWipers");
            wiperAssemblyGo.transform.SetParent(busGo.transform, false);

            // Left wiper pivot: X=-0.45, Y=1.55, Z=5.70
            // Right wiper pivot: X=+0.45, Y=1.55, Z=5.70
            // Windshield tilt: ~18 deg backwards
            Quaternion windshieldRake = Quaternion.Euler(18f, 0f, 0f);

            var leftPivotGo = new GameObject("WiperPivot_Left");
            leftPivotGo.transform.SetParent(wiperAssemblyGo.transform, false);
            leftPivotGo.transform.localPosition = new Vector3(-0.45f, 1.55f, 5.70f);
            leftPivotGo.transform.localRotation = windshieldRake;

            BuildWiperBladeGeometry(leftPivotGo.transform, wiperMat);

            var rightPivotGo = new GameObject("WiperPivot_Right");
            rightPivotGo.transform.SetParent(wiperAssemblyGo.transform, false);
            rightPivotGo.transform.localPosition = new Vector3(0.45f, 1.55f, 5.70f);
            rightPivotGo.transform.localRotation = windshieldRake;

            BuildWiperBladeGeometry(rightPivotGo.transform, wiperMat);

            var wiperCtrl = busGo.AddComponent<BusWiperController>();
            wiperCtrl.busController = controller;
            wiperCtrl.leftWiperPivot = leftPivotGo.transform;
            wiperCtrl.rightWiperPivot = rightPivotGo.transform;
            if (audioCtrl != null)
            {
                wiperCtrl.wiperAudioSource = audioCtrl.wiperAudioSource;
            }
        }

        private static void BuildWiperBladeGeometry(Transform pivot, Material mat)
        {
            // Arm extending up from pivot
            var armGo = GameObject.CreatePrimitive(PrimitiveType.Cube);
            armGo.name = "WiperArm";
            armGo.transform.SetParent(pivot, false);
            armGo.transform.localPosition = new Vector3(0f, 0.40f, 0.015f);
            armGo.transform.localRotation = Quaternion.identity;
            armGo.transform.localScale = new Vector3(0.022f, 0.78f, 0.015f);
            UnityEngine.Object.DestroyImmediate(armGo.GetComponent<Collider>());
            if (mat != null) armGo.GetComponent<MeshRenderer>().sharedMaterial = mat;

            // Rubber blade
            var bladeGo = GameObject.CreatePrimitive(PrimitiveType.Cube);
            bladeGo.name = "WiperBlade";
            bladeGo.transform.SetParent(pivot, false);
            bladeGo.transform.localPosition = new Vector3(0f, 0.45f, 0.022f);
            bladeGo.transform.localRotation = Quaternion.identity;
            bladeGo.transform.localScale = new Vector3(0.012f, 0.74f, 0.012f);
            UnityEngine.Object.DestroyImmediate(bladeGo.GetComponent<Collider>());
            if (mat != null) bladeGo.GetComponent<MeshRenderer>().sharedMaterial = mat;
        }

        private static void SetupIndicatorVisuals(GameObject busGo, GameObject visualsGo, BusController controller)
        {
            Shader litShader = Shader.Find("Universal Render Pipeline/Lit");
            if (litShader == null) litShader = Shader.Find("Standard");

            var indGo = busGo.AddComponent<BusIndicatorVisuals>();
            indGo.busController = controller;

            // Emissive amber materials for turn signals
            var onMat = new Material(litShader)
            {
                name = "Mat_Hino_BlinkerOn",
                color = new Color(1.0f, 0.70f, 0.05f)
            };
            onMat.EnableKeyword("_EMISSION");
            onMat.SetColor("_EmissionColor", new Color(2.4f, 1.4f, 0.1f));

            var offMat = new Material(litShader)
            {
                name = "Mat_Hino_BlinkerOff",
                color = new Color(0.35f, 0.22f, 0.05f)
            };
            offMat.DisableKeyword("_EMISSION");
            offMat.SetColor("_EmissionColor", Color.black);

            indGo.indicatorOnMaterial = onMat;
            indGo.indicatorOffMaterial = offMat;

            // Find combined indicators
            var combinedList = new List<Renderer>();
            foreach (var r in visualsGo.GetComponentsInChildren<MeshRenderer>(true))
            {
                string lower = r.name.ToLower();
                if (lower.Contains("side light") || lower.Contains("back light.003") || lower.Contains("headlight.001") || lower.Contains("headlight.002") || lower.Contains("round light"))
                {
                    combinedList.Add(r);
                }
            }
            indGo.combinedIndicators = combinedList.ToArray();

            // Dynamic amber point lights on all 4 corners + mid sides
            var flLight = CreateAmberSignalLight(busGo, "Signal_FL", new Vector3(-1.15f, 0.90f, 5.65f));
            var frLight = CreateAmberSignalLight(busGo, "Signal_FR", new Vector3(1.15f, 0.90f, 5.65f));
            var mlLight = CreateAmberSignalLight(busGo, "Signal_ML", new Vector3(-1.26f, 1.15f, 0.08f));
            var mrLight = CreateAmberSignalLight(busGo, "Signal_MR", new Vector3(1.26f, 1.15f, 0.08f));
            var rlLight = CreateAmberSignalLight(busGo, "Signal_RL", new Vector3(-1.10f, 1.40f, -6.15f));
            var rrLight = CreateAmberSignalLight(busGo, "Signal_RR", new Vector3(1.10f, 1.40f, -6.15f));

            indGo.leftLights = new[] { flLight, mlLight, rlLight };
            indGo.rightLights = new[] { frLight, mrLight, rrLight };
        }

        private static Light CreateAmberSignalLight(GameObject bus, string name, Vector3 localPos)
        {
            var lampGo = new GameObject(name);
            lampGo.transform.SetParent(bus.transform, false);
            lampGo.transform.localPosition = localPos;

            var light = lampGo.AddComponent<Light>();
            light.type = LightType.Point;
            light.color = new Color(1.0f, 0.65f, 0.05f);
            light.range = 6.0f;
            light.intensity = 2.2f;
            light.shadows = LightShadows.None;
            light.enabled = false;

            return light;
        }
    }
}
