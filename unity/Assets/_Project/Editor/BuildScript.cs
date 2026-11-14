using System;
using System.IO;
using System.Collections.Generic;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using BharatBusSim.Diagnostics;
using BharatBusSim.Vehicles;
using BharatBusSim.UI;
using BharatBusSim.Roads;
using BharatBusSim.Environment;
using BharatBusSim.Signs;
using BharatBusSim.Toll;
using BharatBusSim.Traffic;

namespace BharatBusSim.Editor
{
    public static class BuildScript
    {
        public const string SmokeTestScenePath = "Assets/_Project/Scenes/01_Phase0_SmokeTest.unity";
        public const string BusPrototypeScenePath = "Assets/_Project/Scenes/02_Phase4_BusPrototype.unity";
        public const string ThrissurMasterScenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
        public const string BusModelPrefabPath = "Assets/_Project/Vehicles/Models/Hino/HINO_RM2.fbx";

        private const string SmokeTestApkPath = "../builds/Android/Phase0_SmokeTest.apk";
        private const string BusPrototypeApkPath = "../builds/Android/Phase4_BusPrototype.apk";
        private const string BusPrototypeFixedApkPath = "../builds/Android/Phase4_BusPrototype_Fixed.apk";
        private const string ThrissurMasterApkPath = "../builds/Android/BharatBusSim_Thrissur_Master.apk";
        private const string HotfixApkName = "Stage3B_Hotfix_BusPrototype.apk";
        private const string PolishedApkName = "BharatBusSim_Stage3B_Polished.apk";

        [MenuItem("BharatBusSim/Setup/Configure Android Player Settings")]
        public static void ConfigureAndroidSettings()
        {
            Debug.Log("[BuildScript] Configuring Android Player Settings...");

            PlayerSettings.companyName = "BharatBusSim";
            PlayerSettings.productName = "Bharat Bus Simulator";
            PlayerSettings.SetApplicationIdentifier(NamedBuildTarget.Android, "com.bharatbussim.game");

            PlayerSettings.colorSpace = ColorSpace.Linear;
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.LandscapeLeft;
            PlayerSettings.allowedAutorotateToLandscapeLeft = true;
            PlayerSettings.allowedAutorotateToLandscapeRight = true;
            PlayerSettings.allowedAutorotateToPortrait = false;
            PlayerSettings.allowedAutorotateToPortraitUpsideDown = false;

            PlayerSettings.SetScriptingBackend(NamedBuildTarget.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel26;
            PlayerSettings.Android.targetSdkVersion = (AndroidSdkVersions)34;

            // Set Graphics APIs: OpenGLES 3 primary for rock-solid stability across mobile GPUs, Vulkan fallback
            PlayerSettings.SetUseDefaultGraphicsAPIs(BuildTarget.Android, false);
            PlayerSettings.SetGraphicsAPIs(BuildTarget.Android, new[]
            {
                GraphicsDeviceType.OpenGLES3,
                GraphicsDeviceType.Vulkan
            });

            AssetDatabase.SaveAssets();
            Debug.Log("[BuildScript] Android Player Settings applied successfully.");
        }

        [MenuItem("BharatBusSim/Setup/Generate Smoke Test Scene (Phase 0)")]
        public static void GenerateSmokeTestScene()
        {
            Debug.Log("[BuildScript] Generating Smoke Test Scene at: " + SmokeTestScenePath);

            string directory = Path.GetDirectoryName(SmokeTestScenePath);
            if (!string.IsNullOrEmpty(directory) && !Directory.Exists(directory))
            {
                Directory.CreateDirectory(directory);
            }

            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            // 1. Camera
            var cameraGo = new GameObject("Main Camera");
            var camera = cameraGo.AddComponent<Camera>();
            camera.clearFlags = CameraClearFlags.Skybox;
            camera.backgroundColor = new Color(0.12f, 0.15f, 0.2f);
            cameraGo.transform.position = new Vector3(0, 2, -6);
            cameraGo.transform.rotation = Quaternion.Euler(15, 0, 0);
            cameraGo.tag = "MainCamera";
            cameraGo.AddComponent<AudioListener>();

            // 2. Directional Light
            var lightGo = new GameObject("Directional Light");
            var light = lightGo.AddComponent<Light>();
            light.type = LightType.Directional;
            light.color = new Color(1.0f, 0.96f, 0.9f);
            light.intensity = 1.2f;
            lightGo.transform.rotation = Quaternion.Euler(50, -30, 0);

            // 3. Diagnostics Overlay Object
            var diagGo = new GameObject("DiagnosticsOverlay");
            diagGo.AddComponent<DeviceDiagnosticsOverlay>();

            // 4. Rotating 3D Cube for visual motion test
            var cube = GameObject.CreatePrimitive(PrimitiveType.Cube);
            cube.name = "TestCube_3D_Indicator";
            cube.transform.position = new Vector3(0, 1.2f, 0);
            cube.transform.localScale = new Vector3(1.4f, 1.4f, 1.4f);
            cube.AddComponent<Rotator>();

            // 5. Ground Plane
            var plane = GameObject.CreatePrimitive(PrimitiveType.Plane);
            plane.name = "Ground_Plane";
            plane.transform.position = Vector3.zero;
            plane.transform.localScale = new Vector3(2f, 1f, 2f);

            EditorSceneManager.SaveScene(scene, SmokeTestScenePath);
            UpdateBuildSettingsScenes();

            AssetDatabase.SaveAssets();
            Debug.Log("[BuildScript] Smoke Test Scene generated.");
        }

        [MenuItem("BharatBusSim/Setup/Generate Bus Prototype Scene (Phase 4 M1)")]
        public static void GenerateBusPrototypeScene()
        {
            Debug.Log("[BuildScript] Generating Bus Prototype Scene at: " + BusPrototypeScenePath);

            string directory = Path.GetDirectoryName(BusPrototypeScenePath);
            if (!string.IsNullOrEmpty(directory) && !Directory.Exists(directory))
            {
                Directory.CreateDirectory(directory);
            }

            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            // Materials
            Material groundMat = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            groundMat.color = new Color(0.22f, 0.35f, 0.18f); // Grass green

            Material roadMat = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            roadMat.color = new Color(0.18f, 0.19f, 0.22f); // Asphalt dark grey

            Material curbMat = new Material(Shader.Find("Universal Render Pipeline/Lit"));
            curbMat.color = new Color(0.85f, 0.85f, 0.85f); // White curb

            // 1. Environment: Ground Base
            var ground = GameObject.CreatePrimitive(PrimitiveType.Plane);
            ground.name = "ProvingGround_Terrain";
            ground.transform.position = Vector3.zero;
            ground.transform.localScale = new Vector3(40f, 1f, 40f); // 400m x 400m
            ground.GetComponent<Renderer>().material = groundMat;

            // 2. Track Elements (Enclosed Circuit)
            var trackParent = new GameObject("ProvingGround_Track");

            // Main Straight (200m x 12m)
            CreateRoadSegment(trackParent, "Main_Straight", new Vector3(0, 0.05f, 0), new Vector3(12f, 0.1f, 200f), roadMat);
            CreateRoadSegment(trackParent, "Return_Straight", new Vector3(60f, 0.05f, 0), new Vector3(12f, 0.1f, 200f), roadMat);
            CreateRoadSegment(trackParent, "South_Curve", new Vector3(30f, 0.05f, -100f), new Vector3(60f, 0.1f, 12f), roadMat);
            CreateRoadSegment(trackParent, "North_Curve", new Vector3(30f, 0.05f, 100f), new Vector3(60f, 0.1f, 12f), roadMat);

            var rampUp = CreateRoadSegment(trackParent, "Test_Ramp_Up", new Vector3(60f, 1.8f, 25f), new Vector3(12f, 0.1f, 30f), roadMat);
            rampUp.transform.rotation = Quaternion.Euler(7f, 0f, 0f);

            var rampDown = CreateRoadSegment(trackParent, "Test_Ramp_Down", new Vector3(60f, 1.8f, -25f), new Vector3(12f, 0.1f, 30f), roadMat);
            rampDown.transform.rotation = Quaternion.Euler(-7f, 0f, 0f);

            CreateRoadSegment(trackParent, "Test_Ramp_Top", new Vector3(60f, 3.6f, 0f), new Vector3(12f, 0.1f, 20f), roadMat);

            CreateCurb(trackParent, "West_Curb", new Vector3(-6.2f, 0.2f, 0), new Vector3(0.4f, 0.4f, 200f), curbMat);
            CreateCurb(trackParent, "Center_Curb_1", new Vector3(6.2f, 0.2f, 0), new Vector3(0.4f, 0.4f, 200f), curbMat);

            // 3. Directional Daylight
            var lightGo = new GameObject("Sun_Daylight");
            var light = lightGo.AddComponent<Light>();
            light.type = LightType.Directional;
            light.color = new Color(1.0f, 0.96f, 0.88f);
            light.intensity = 1.25f;
            lightGo.transform.rotation = Quaternion.Euler(50f, -35f, 0f);

            // 4. Procedural Kerala Bus
            BuildPlayerBus(new Vector3(0f, 0.2f, -80f), Quaternion.identity, false);

            // Save Scene
            EditorSceneManager.SaveScene(scene, BusPrototypeScenePath);
            UpdateBuildSettingsScenes();

            AssetDatabase.SaveAssets();
            Debug.Log("[BuildScript] Bus Prototype Scene generated successfully at: " + BusPrototypeScenePath);
        }

        [MenuItem("BharatBusSim/Setup/Generate Thrissur Master Scene (Phase 5/6)")]
        public static void GenerateThrissurMasterScene()
        {
            Debug.Log("[BuildScript] Generating Thrissur Realistic World Master Scene at: " + ThrissurMasterScenePath);

            string directory = Path.GetDirectoryName(ThrissurMasterScenePath);
            if (!string.IsNullOrEmpty(directory) && !Directory.Exists(directory))
            {
                Directory.CreateDirectory(directory);
            }

            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            // 1. Procedural PBR Textures & Materials Palette
            Shader litShader = Shader.Find("Universal Render Pipeline/Lit");
            if (litShader == null) litShader = Shader.Find("Standard");

            Texture2D asphaltTex = ProceduralTextureFactory.GetOrCreateAsphaltTexture();
            Texture2D kerbTex = ProceduralTextureFactory.GetOrCreateKerbTexture();
            Texture2D tileTex = ProceduralTextureFactory.GetOrCreateKeralaTileTexture();
            Texture2D concreteTex = ProceduralTextureFactory.GetOrCreateConcreteTexture();
            Texture2D soilTex = ProceduralTextureFactory.GetOrCreateLateriteSoilTexture();
            Texture2D grassTex = ProceduralTextureFactory.GetOrCreateTropicalGrassTexture();
            Texture2D barkTex = ProceduralTextureFactory.GetOrCreatePalmBarkTexture();
            Texture2D frondTex = ProceduralTextureFactory.GetOrCreatePalmFrondTexture();

            Material roadMat = new Material(litShader)
            {
                name = "Mat_Asphalt_PBR",
                mainTexture = asphaltTex,
                color = new Color(0.24f, 0.24f, 0.25f)
            };
            roadMat.SetFloat("_Smoothness", 0.22f);
            roadMat.mainTextureScale = new Vector2(2f, 18f);

            Material curbMat = new Material(litShader)
            {
                name = "Mat_Kerb_YellowBlack",
                mainTexture = kerbTex,
                color = Color.white
            };
            curbMat.mainTextureScale = new Vector2(1f, 15f);

            Material grassMat = new Material(litShader)
            {
                name = "Mat_Tropical_Grass",
                mainTexture = grassTex,
                color = new Color(0.56f, 0.68f, 0.46f)
            };
            grassMat.mainTextureScale = new Vector2(2400f, 2400f);

            Material sandMat = new Material(litShader)
            {
                name = "Mat_Coastal_Sand",
                color = new Color(0.85f, 0.78f, 0.58f)
            };

            Material waterMat = new Material(litShader)
            {
                name = "Mat_Tropical_Water",
                color = new Color(0.08f, 0.32f, 0.48f)
            };
            waterMat.SetFloat("_Smoothness", 0.85f);

            Material woodMat = new Material(litShader)
            {
                name = "Mat_Palm_Bark",
                mainTexture = barkTex,
                color = new Color(0.90f, 0.85f, 0.80f)
            };
            woodMat.mainTextureScale = new Vector2(1f, 6f);

            Material leafMat = new Material(litShader)
            {
                name = "Mat_Palm_Frond",
                mainTexture = frondTex,
                color = Color.white
            };
            leafMat.SetFloat("_AlphaClip", 1.0f);
            leafMat.SetFloat("_Cutoff", 0.35f);
            leafMat.EnableKeyword("_ALPHATEST_ON");
            leafMat.SetFloat("_Cull", 0f); // Double-sided rendering so fronds are visible from above and below
            leafMat.SetOverrideTag("RenderType", "TransparentCutout");
            leafMat.renderQueue = (int)UnityEngine.Rendering.RenderQueue.AlphaTest;

            Material wallMat = new Material(litShader)
            {
                name = "Mat_Shop_Plaster",
                color = new Color(0.93f, 0.91f, 0.88f)
            };

            Material roofMat = new Material(litShader)
            {
                name = "Mat_Kerala_ClayTile",
                mainTexture = tileTex,
                color = Color.white
            };
            roofMat.mainTextureScale = new Vector2(4f, 4f);

            Material concreteMat = new Material(litShader)
            {
                name = "Mat_Concrete_Slab",
                mainTexture = concreteTex,
                color = Color.white
            };
            concreteMat.mainTextureScale = new Vector2(4f, 4f);

            Material goldMat = new Material(litShader)
            {
                name = "Mat_Temple_Gold",
                color = new Color(0.94f, 0.78f, 0.22f)
            };
            goldMat.SetFloat("_Smoothness", 0.75f);

            Material glassMat = new Material(litShader)
            {
                name = "Mat_Booth_Glass",
                color = new Color(0.18f, 0.28f, 0.38f)
            };

            Material poleMat = new Material(litShader)
            {
                name = "Mat_Steel_Galvanized",
                color = new Color(0.55f, 0.58f, 0.62f)
            };

            Material blueSignMat = new Material(litShader)
            {
                name = "Mat_IRC_Sign_Blue",
                color = new Color(0.06f, 0.30f, 0.75f)
            };

            Material greenSignMat = new Material(litShader)
            {
                name = "Mat_IRC_Sign_Green",
                color = new Color(0.08f, 0.45f, 0.18f)
            };

            Material redSignMat = new Material(litShader)
            {
                name = "Mat_IRC_Sign_Red",
                color = new Color(0.85f, 0.12f, 0.12f)
            };

            Material yellowSignMat = new Material(litShader)
            {
                name = "Mat_IRC_Sign_Yellow",
                color = new Color(0.96f, 0.80f, 0.08f)
            };

            Material markingMat = new Material(litShader)
            {
                name = "Mat_White_Marking",
                color = new Color(0.95f, 0.95f, 0.95f)
            };

            Material medianMat = new Material(litShader)
            {
                name = "Mat_Jersey_Barrier",
                mainTexture = concreteTex,
                color = new Color(0.80f, 0.80f, 0.80f)
            };

            // 2. Natural Tropical Sunlight & Atmosphere
            var lightGo = new GameObject("Sun_Daylight");
            var light = lightGo.AddComponent<Light>();
            light.type = LightType.Directional;
            light.color = new Color(1.0f, 0.96f, 0.88f); // Warm natural tropical daylight
            light.intensity = 1.25f;
            light.shadows = LightShadows.Soft;
            lightGo.transform.rotation = Quaternion.Euler(48f, -35f, 0f);

            // Natural Kerala Ambient Lighting & Atmospheric Depth Fog
            RenderSettings.ambientMode = UnityEngine.Rendering.AmbientMode.Trilight;
            RenderSettings.ambientSkyColor = new Color(0.68f, 0.78f, 0.88f);
            RenderSettings.ambientEquatorColor = new Color(0.48f, 0.54f, 0.46f);
            RenderSettings.ambientGroundColor = new Color(0.24f, 0.20f, 0.16f);

            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.ExponentialSquared;
            RenderSettings.fogColor = new Color(0.68f, 0.78f, 0.88f);
            RenderSettings.fogDensity = 0.00028f;

            // 3. Connected Real-World Thrissur Road Network (Built in Blender from OSM/OSRM)
            ThrissurBlenderRoadBuilder.ApplyToCurrentScene();

            // 5. Regional Landmarks
            var landmarksParent = new GameObject("World_Landmarks");
            var landmarksGo = LandmarkBuilder.BuildAllLandmarks(landmarksParent.transform, concreteMat, goldMat, roofMat, wallMat, glassMat);
            SakthanStandFactory.BuildRealisticSakthanStand(landmarksGo.transform);
            SwarajRoundFactory.BuildSwarajRound(landmarksGo.transform, goldMat, roofMat);
            PaliyekkaraTollFactory.Build6LaneTollPlaza(landmarksGo.transform, redSignMat, greenSignMat, concreteMat);

            // 6. Functional Toll Plaza System (Paliyekkara Toll Plaza on NH 544)
            SetupPaliyekkaraTollSystem(redSignMat, greenSignMat, concreteMat);

            // 9. Player 12m Coach Spawned at Sakthan Thampuran Bus Stand Terminal Bay
            // Position: (25.0f, 0.35f, -169.3f) inside Sakthan Platform Bay 1, facing North
            Vector3 sakthanSpawnPos = new Vector3(25.0f, 0.35f, -169.3f);
            Quaternion sakthanSpawnRot = Quaternion.identity;
            var busGo = BuildPlayerBus(sakthanSpawnPos, sakthanSpawnRot, initializeInPark: true);


            // Ensure no extraneous cameras exist in the scene other than ChaseCamera
            var allCams = UnityEngine.Object.FindObjectsByType<Camera>(FindObjectsSortMode.None);
            foreach (var c in allCams)
            {
                if (c.gameObject.name != "ChaseCamera")
                {
                    Debug.LogWarning($"[BuildScript] Removing extraneous scene camera: {c.gameObject.name}");
                    UnityEngine.Object.DestroyImmediate(c.gameObject);
                }
            }

            var camGo = GameObject.Find("ChaseCamera");
            if (camGo != null)
            {
                var camCtrl = camGo.GetComponent<BharatBusSim.Vehicles.BusCameraController>();
                if (camCtrl != null)
                {
                    camCtrl.target = busGo.transform;
                    camCtrl.SetCockpitEyeSettings(2.35f, 0.70f, 4.65f, 6.5f);
                    camCtrl.SnapToTarget();
                }
            }

            // Save Scene
            EditorSceneManager.SaveScene(scene, ThrissurMasterScenePath);
            UpdateBuildSettingsScenes();

            AssetDatabase.SaveAssets();
            Debug.Log("[BuildScript] Thrissur Master Scene generated successfully at: " + ThrissurMasterScenePath);
        }


        private static void SetupPaliyekkaraTollSystem(Material redMat, Material greenMat, Material concreteMat)
        {
            var tollSystemGo = new GameObject("Toll_System_Controller");
            // Positioned at Paliyekkara Toll Booth: (998.2, 0, -1805.2)
            tollSystemGo.transform.position = new Vector3(998.2f, 0f, -1805.2f);

            var economy = tollSystemGo.AddComponent<TollEconomyManager>();
            var controller = tollSystemGo.AddComponent<TollPlazaController>();
            controller.tollFee = 50;
            controller.plazaName = "Paliyekkara Toll Plaza (NH 544)";
            controller.redLightMat = redMat;
            controller.greenLightMat = greenMat;

            // Automated 6-Lane Synchronized Boom Barriers (Lanes 1 to 6 across 38m highway)
            float[] laneOffsets = new float[] { -12.5f, -7.5f, -2.5f, 2.5f, 7.5f, 12.5f };
            var armsList = new System.Collections.Generic.List<Transform>();
            var collidersList = new System.Collections.Generic.List<Collider>();
            var signalsList = new System.Collections.Generic.List<Renderer>();

            for (int i = 0; i < laneOffsets.Length; i++)
            {
                float laneX = laneOffsets[i];

                var barrierPillar = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                barrierPillar.name = $"Barrier_Pillar_Lane_{i + 1}";
                barrierPillar.transform.SetParent(tollSystemGo.transform, false);
                barrierPillar.transform.localPosition = new Vector3(laneX + 2.3f, 1.0f, 0f);
                barrierPillar.transform.localScale = new Vector3(0.35f, 1.0f, 0.35f);
                barrierPillar.GetComponent<Renderer>().sharedMaterial = concreteMat;

                var pivotGo = new GameObject($"Barrier_Pivot_Lane_{i + 1}");
                pivotGo.transform.SetParent(tollSystemGo.transform, false);
                pivotGo.transform.localPosition = new Vector3(laneX + 2.3f, 1.8f, 0f);

                var arm = GameObject.CreatePrimitive(PrimitiveType.Cube);
                arm.name = $"Barrier_Arm_Beam_Lane_{i + 1}";
                arm.transform.SetParent(pivotGo.transform, false);
                arm.transform.localPosition = new Vector3(-2.2f, 0f, 0f);
                arm.transform.localScale = new Vector3(4.5f, 0.16f, 0.16f);
                arm.GetComponent<Renderer>().sharedMaterial = redMat;

                armsList.Add(pivotGo.transform);
                collidersList.Add(arm.GetComponent<Collider>());

                // Signal Light Lamp
                var lampGo = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                lampGo.name = $"Traffic_Signal_Lamp_Lane_{i + 1}";
                lampGo.transform.SetParent(tollSystemGo.transform, false);
                lampGo.transform.localPosition = new Vector3(laneX + 2.3f, 2.7f, 0f);
                lampGo.transform.localScale = new Vector3(0.40f, 0.40f, 0.40f);
                lampGo.GetComponent<Renderer>().sharedMaterial = redMat;
                signalsList.Add(lampGo.GetComponent<Renderer>());
            }

            controller.barrierArms = armsList.ToArray();
            controller.barrierPhysicalColliders = collidersList.ToArray();
            controller.trafficLightRenderers = signalsList.ToArray();

            // Set primary (Lane 2) for legacy single-element references
            controller.barrierArm = armsList[1];
            controller.barrierPhysicalCollider = collidersList[1];
            controller.trafficLightRenderer = signalsList[1];

            // 6-Lane Highway Triggers (Width 38m covering all 6 lanes seamlessly)
            // Trigger 1: Approach Zone Trigger (X: 998.2, Z: -1780, length 24m)
            var approachGo = new GameObject("Trigger_Approach_Zone");
            approachGo.transform.SetParent(tollSystemGo.transform, false);
            approachGo.transform.localPosition = new Vector3(0f, 2.0f, 20f);
            var approachCol = approachGo.AddComponent<BoxCollider>();
            approachCol.isTrigger = true;
            approachCol.size = new Vector3(38f, 5f, 24f);
            var appTrigger = approachGo.AddComponent<TollZoneTrigger>();
            appTrigger.zoneType = TollZoneTrigger.TriggerZoneType.ApproachZone;
            appTrigger.controller = controller;

            // Trigger 2: Barrier Crossing Zone Trigger (At barrier line: Z = 0)
            var barrierZoneGo = new GameObject("Trigger_Barrier_Zone");
            barrierZoneGo.transform.SetParent(tollSystemGo.transform, false);
            barrierZoneGo.transform.localPosition = new Vector3(0f, 2.0f, 0f);
            var barrierZoneCol = barrierZoneGo.AddComponent<BoxCollider>();
            barrierZoneCol.isTrigger = true;
            barrierZoneCol.size = new Vector3(38f, 5f, 6f);
            var barTrigger = barrierZoneGo.AddComponent<TollZoneTrigger>();
            barTrigger.zoneType = TollZoneTrigger.TriggerZoneType.BarrierZone;
            barTrigger.controller = controller;

            // Trigger 3: Exit Zone Trigger (X: 998.2, Z: -1825, length 24m)
            var exitGo = new GameObject("Trigger_Exit_Zone");
            exitGo.transform.SetParent(tollSystemGo.transform, false);
            exitGo.transform.localPosition = new Vector3(0f, 2.0f, -20f);
            var exitCol = exitGo.AddComponent<BoxCollider>();
            exitCol.isTrigger = true;
            exitCol.size = new Vector3(38f, 5f, 24f);
            var exitTrigger = exitGo.AddComponent<TollZoneTrigger>();
            exitTrigger.zoneType = TollZoneTrigger.TriggerZoneType.ExitZone;
            exitTrigger.controller = controller;
        }

        private static GameObject BuildPlayerBus(Vector3 spawnPos, Quaternion spawnRot, bool initializeInPark)
        {
            return HinoBusFactory.CreateHinoBus(spawnPos, spawnRot, initializeInPark);
        }

        private static GameObject CreateRoadSegment(GameObject parent, string name, Vector3 pos, Vector3 scale, Material mat)
        {
            var road = GameObject.CreatePrimitive(PrimitiveType.Cube);
            road.name = name;
            road.transform.SetParent(parent.transform, false);
            road.transform.localPosition = pos;
            road.transform.localScale = scale;
            road.GetComponent<Renderer>().material = mat;
            return road;
        }

        private static void CreateCurb(GameObject parent, string name, Vector3 pos, Vector3 scale, Material mat)
        {
            var curb = GameObject.CreatePrimitive(PrimitiveType.Cube);
            curb.name = name;
            curb.transform.SetParent(parent.transform, false);
            curb.transform.localPosition = pos;
            curb.transform.localScale = scale;
            curb.GetComponent<Renderer>().material = mat;
        }

        public static void UpdateBuildSettingsScenes()
        {
            var scenes = new List<EditorBuildSettingsScene>();
            if (File.Exists(ThrissurMasterScenePath))
            {
                scenes.Add(new EditorBuildSettingsScene(ThrissurMasterScenePath, true));
            }
            if (File.Exists(SmokeTestScenePath))
            {
                scenes.Add(new EditorBuildSettingsScene(SmokeTestScenePath, true));
            }
            if (File.Exists(BusPrototypeScenePath))
            {
                scenes.Add(new EditorBuildSettingsScene(BusPrototypeScenePath, true));
            }
            EditorBuildSettings.scenes = scenes.ToArray();
        }

        [MenuItem("BharatBusSim/Build/Build Android Thrissur Master World APK")]
        public static void BuildAndroidThrissurMaster()
        {
            Debug.Log("=================================================");
            Debug.Log("[BuildScript] Starting Android Thrissur Realistic World Master Build...");
            Debug.Log("=================================================");

            ConfigureAndroidSettings();
            GenerateThrissurMasterScene();
            UpdateBuildSettingsScenes();

            string projectPath = Application.dataPath.Substring(0, Application.dataPath.Length - "Assets".Length);
            string outputFullPath = Path.GetFullPath(Path.Combine(projectPath, ThrissurMasterApkPath));
            string outputDir = Path.GetDirectoryName(outputFullPath);

            if (!string.IsNullOrEmpty(outputDir) && !Directory.Exists(outputDir))
            {
                Directory.CreateDirectory(outputDir);
            }

            if (File.Exists(outputFullPath))
            {
                try { File.Delete(outputFullPath); } catch { }
            }

            BuildPlayerOptions buildPlayerOptions = new BuildPlayerOptions
            {
                scenes = new[] { ThrissurMasterScenePath },
                locationPathName = outputFullPath,
                target = BuildTarget.Android,
                options = BuildOptions.Development
            };

            Debug.Log($"[BuildScript] Compiling Master APK to: {outputFullPath}");
            BuildReport report = BuildPipeline.BuildPlayer(buildPlayerOptions);
            BuildSummary summary = report.summary;

            if (summary.result == BuildResult.Succeeded)
            {
                FileInfo fi = new FileInfo(outputFullPath);
                Debug.Log($"[BuildScript] Thrissur Master APK BUILD SUCCEEDED!");
                Debug.Log($"[BuildScript] APK Path: {outputFullPath}");
                Debug.Log($"[BuildScript] Size: {fi.Length / (1024f * 1024f):0.2f} MB ({fi.Length} bytes)");
                Debug.Log($"[BuildScript] Duration: {summary.totalTime.TotalSeconds:0.1f} s");
                Debug.Log($"[BuildScript] Warnings: {summary.totalWarnings}, Errors: {summary.totalErrors}");
            }
            else
            {
                Debug.LogError($"[BuildScript] BUILD FAILED with result: {summary.result}");
                throw new Exception($"Android build failed with result {summary.result}");
            }
        }

        [MenuItem("BharatBusSim/Build/Build Android Bus Prototype APK")]
        public static void BuildAndroidBusPrototype()
        {
            Debug.Log("=================================================");
            Debug.Log("[BuildScript] Starting Android Bus Prototype Build (Stage 3B)...");
            Debug.Log("=================================================");

            ConfigureAndroidSettings();
            GenerateBusPrototypeScene();
            UpdateBuildSettingsScenes();

            string projectPath = Application.dataPath.Substring(0, Application.dataPath.Length - "Assets".Length);
            string outputFullPath = Path.GetFullPath(Path.Combine(projectPath, BusPrototypeFixedApkPath));
            string outputDir = Path.GetDirectoryName(outputFullPath);

            if (!string.IsNullOrEmpty(outputDir) && !Directory.Exists(outputDir))
            {
                Directory.CreateDirectory(outputDir);
            }

            if (File.Exists(outputFullPath))
            {
                try { File.Delete(outputFullPath); } catch { }
            }

            BuildPlayerOptions buildPlayerOptions = new BuildPlayerOptions
            {
                scenes = new[] { BusPrototypeScenePath },
                locationPathName = outputFullPath,
                target = BuildTarget.Android,
                options = BuildOptions.Development
            };

            Debug.Log($"[BuildScript] Compiling APK to: {outputFullPath}");
            BuildReport report = BuildPipeline.BuildPlayer(buildPlayerOptions);
            BuildSummary summary = report.summary;

            if (summary.result == BuildResult.Succeeded)
            {
                FileInfo fi = new FileInfo(outputFullPath);
                string hotfixApk = Path.Combine(outputDir, HotfixApkName);
                string polishedApk = Path.Combine(outputDir, PolishedApkName);
                File.Copy(outputFullPath, hotfixApk, true);
                File.Copy(outputFullPath, polishedApk, true);

                Debug.Log($"[BuildScript] Bus Prototype APK BUILD SUCCEEDED!");
                Debug.Log($"[BuildScript] Fixed APK Path: {outputFullPath}");
                Debug.Log($"[BuildScript] Polished APK Path: {polishedApk}");
                Debug.Log($"[BuildScript] Size: {fi.Length / (1024f * 1024f):0.2f} MB");
                Debug.Log($"[BuildScript] Duration: {summary.totalTime.TotalSeconds:0.1f} s");
                Debug.Log($"[BuildScript] Warnings: {summary.totalWarnings}, Errors: {summary.totalErrors}");
            }
            else
            {
                Debug.LogError($"[BuildScript] BUILD FAILED with result: {summary.result}");
                throw new Exception($"Android build failed with result {summary.result}");
            }
        }
    }
}
