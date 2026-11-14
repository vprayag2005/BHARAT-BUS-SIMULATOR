using System;
using System.IO;
using System.Reflection;
using System.Collections.Generic;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using BharatBusSim.Vehicles;
using BharatBusSim.UI;
using BharatBusSim.Roads;
using BharatBusSim.Toll;

namespace BharatBusSim.Editor
{
    public static class ThrissurWorldValidator
    {
        private const string ThrissurScenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
        private const string SmokeTestScenePath = "Assets/_Project/Scenes/01_Phase0_SmokeTest.unity";
        private const string BusPrototypeScenePath = "Assets/_Project/Scenes/02_Phase4_BusPrototype.unity";

        [MenuItem("BharatBusSim/Validation/Run Complete Thrissur Master Validation")]
        public static void RunAllValidations()
        {
            Debug.Log("==================================================================");
            Debug.Log("[ThrissurWorldValidator] STARTING COMPREHENSIVE THRISSUR MASTER VALIDATION");
            Debug.Log("==================================================================");

            bool allPassed = true;

            try
            {
                // Check 1: Existing Scenes Preservation
                allPassed &= ValidatePreservedScenes();

                // Check 2: Generate / Load Thrissur Master Scene
                allPassed &= ValidateSceneGenerationAndLoad();

                // Check 3: Bus Spawn at Sakthan Stand in Park with Handbrake
                allPassed &= ValidateSakthanBusSpawnAndReset();

                // Check 4: 6-Wheel Physics Configuration & WheelColliders
                allPassed &= ValidateSixWheelPhysics();

                // Check 5: Landmarks Presence & Geometry
                allPassed &= ValidateLandmarks();

                // Check 6: Connected Road Network Topology & Colliders
                allPassed &= ValidateRoadNetwork();

                // Check 7: IRC Bilingual Indian Road Signage
                allPassed &= ValidateIndianSignage();

                // Check 8: Automated Toll Economy & Barrier Tests
                allPassed &= TollSystemTests.RunAllTollTests();

                // Check 9: HUD Layout, Safe Area, and EventSystem
                allPassed &= ValidateHUDAndInput();

                // Check 10: Build Settings Configuration
                allPassed &= ValidateBuildSettings();

                if (allPassed)
                {
                    Debug.Log("==================================================================");
                    Debug.Log("[ThrissurWorldValidator] ALL THRISSUR MASTER VALIDATIONS PASSED!");
                    Debug.Log("==================================================================");
                }
                else
                {
                    Debug.LogError("[ThrissurWorldValidator] ONE OR MORE VALIDATION CHECKS FAILED!");
                    throw new Exception("Thrissur Master World validation failed!");
                }
            }
            catch (Exception ex)
            {
                Debug.LogError($"[ThrissurWorldValidator] EXCEPTION DURING VALIDATION: {ex.Message}\n{ex.StackTrace}");
                throw;
            }
        }

        private static bool ValidatePreservedScenes()
        {
            Debug.Log("[ThrissurWorldValidator] Checking preservation of Phase 0 and Phase 4 scenes...");
            if (!File.Exists(SmokeTestScenePath))
            {
                Debug.LogError($"[ThrissurWorldValidator] Phase 0 SmokeTest scene missing at {SmokeTestScenePath}!");
                return false;
            }
            if (!File.Exists(BusPrototypeScenePath))
            {
                Debug.LogError($"[ThrissurWorldValidator] Phase 4 BusPrototype scene missing at {BusPrototypeScenePath}!");
                return false;
            }
            Debug.Log("[ThrissurWorldValidator] Phase 0 and Phase 4 scenes verified intact.");
            return true;
        }

        private static bool ValidateSceneGenerationAndLoad()
        {
            Debug.Log("[ThrissurWorldValidator] Generating and opening Thrissur Master Scene...");
            BuildScript.GenerateThrissurMasterScene();

            if (!File.Exists(ThrissurScenePath))
            {
                Debug.LogError($"[ThrissurWorldValidator] Scene file missing at {ThrissurScenePath}!");
                return false;
            }

            var scene = EditorSceneManager.OpenScene(ThrissurScenePath, OpenSceneMode.Single);
            if (!scene.IsValid())
            {
                Debug.LogError("[ThrissurWorldValidator] Failed to open Thrissur Master Scene!");
                return false;
            }

            // Verify scene contains no embedded MonoScripts (!u!115) which corrupt standalone IL2CPP builds
            string sceneYaml = File.ReadAllText(ThrissurScenePath);
            if (sceneYaml.Contains("--- !u!115 &"))
            {
                Debug.LogError("[ThrissurWorldValidator] Found embedded MonoScript (!u!115) in scene file! All MonoBehaviours must be defined in their own matching .cs files.");
                return false;
            }

            Debug.Log("[ThrissurWorldValidator] Thrissur Master Scene loaded successfully with 0 embedded MonoScripts.");
            return true;
        }

        private static bool ValidateSakthanBusSpawnAndReset()
        {
            Debug.Log("[ThrissurWorldValidator] Validating bus spawn in Sakthan Stand terminal bay...");
            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            if (busGo == null)
            {
                Debug.LogError("[ThrissurWorldValidator] 'Kerala_Bus_Prototype' missing from scene!");
                return false;
            }

            var busController = busGo.GetComponent<BusController>();
            if (busController == null)
            {
                Debug.LogError("[ThrissurWorldValidator] BusController missing on bus!");
                return false;
            }

            // Verify spawn location near Sakthan Bay (X: ~25.0, Z: ~ -169.3)
            Vector3 pos = busGo.transform.position;
            float distToSakthanBay = Vector2.Distance(new Vector2(pos.x, pos.z), new Vector2(25.0f, -169.3f));
            Debug.Log($"[ThrissurWorldValidator] Bus spawn position: {pos}, distance to Sakthan bay: {distToSakthanBay:F2}m");
            if (distToSakthanBay > 5.0f)
            {
                Debug.LogError($"[ThrissurWorldValidator] Bus spawned too far from Sakthan Stand starting bay! Position={pos}");
                return false;
            }

            // Simulate Awake() and Start() invocation on BusController
            MethodInfo awakeMethod = typeof(BusController).GetMethod("Awake", BindingFlags.NonPublic | BindingFlags.Instance);
            if (awakeMethod != null)
            {
                awakeMethod.Invoke(busController, null);
            }

            MethodInfo startMethod = typeof(BusController).GetMethod("Start", BindingFlags.NonPublic | BindingFlags.Instance);
            if (startMethod != null)
            {
                startMethod.Invoke(busController, null);
            }

            // Verify initial state: Gear P (Park) and Handbrake Engaged
            if (busController.gearMode != TransmissionGear.Park)
            {
                Debug.LogError($"[ThrissurWorldValidator] Bus gear mode is {busController.gearMode}, expected Park!");
                return false;
            }
            if (!busController.isHandbrakeActive)
            {
                Debug.LogError("[ThrissurWorldValidator] Bus parking brake is not engaged at spawn!");
                return false;
            }

            // Test ResetBus() function
            busGo.transform.position = new Vector3(500f, 10f, 500f);
            busController.ResetBus();

            Vector3 resetPos = busGo.transform.position;
            float resetDist = Vector2.Distance(new Vector2(resetPos.x, resetPos.z), new Vector2(25.0f, -169.3f));
            if (resetDist > 0.5f || busController.gearMode != TransmissionGear.Park || !busController.isHandbrakeActive)
            {
                Debug.LogError($"[ThrissurWorldValidator] ResetBus() failed to return bus to Sakthan starting bay! Pos={resetPos}, Gear={busController.gearMode}");
                return false;
            }

            // Verify camera view at spawn is 100% unobstructed (no canopy roofs or walls in the line of sight)
            GameObject camObj = GameObject.FindWithTag("MainCamera");
            if (camObj != null && busGo != null)
            {
                Vector3 camPos = camObj.transform.position;
                Vector3 busCenter = busGo.transform.position + Vector3.up * 1.8f;
                RaycastHit[] camHits = Physics.RaycastAll(busCenter, (camPos - busCenter).normalized, Vector3.Distance(busCenter, camPos));
                foreach (var h in camHits)
                {
                    if (h.collider != null && !h.collider.isTrigger && !h.transform.IsChildOf(busGo.transform))
                    {
                        Debug.LogError($"[ThrissurWorldValidator] Obstacle '{h.collider.name}' is blocking the camera line of sight at spawn!");
                        return false;
                    }
                }
                Debug.Log("[ThrissurWorldValidator] Camera line of sight at Sakthan spawn verified completely unobstructed.");
            }

            Debug.Log("[ThrissurWorldValidator] Sakthan spawn & reset validation PASSED: Bus safely initialized in P gear with handbrake engaged.");
            return true;
        }

        private static bool ValidateSixWheelPhysics()
        {
            Debug.Log("[ThrissurWorldValidator] Validating 6-wheel physics configuration...");
            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            var busController = busGo.GetComponent<BusController>();

            if (busController.frontLeftCollider == null || busController.frontRightCollider == null ||
                busController.midLeftCollider == null || busController.midRightCollider == null ||
                busController.rearLeftCollider == null || busController.rearRightCollider == null)
            {
                Debug.LogError("[ThrissurWorldValidator] BusController is missing one or more of the 6 physical WheelColliders!");
                return false;
            }

            var rb = busGo.GetComponent<Rigidbody>();
            if (rb == null || rb.mass < 10000f)
            {
                Debug.LogError($"[ThrissurWorldValidator] Rigidbody mass is {rb?.mass}, expected 10,500 kg!");
                return false;
            }

            var wheels = busGo.GetComponentsInChildren<BusWheel>(true);
            if (wheels.Length < 6)
            {
                Debug.LogError($"[ThrissurWorldValidator] Found {wheels.Length} BusWheel synchronizers, expected 6!");
                return false;
            }

            Debug.Log("[ThrissurWorldValidator] 6-Wheel physics configuration PASSED.");
            return true;
        }

        private static bool ValidateLandmarks()
        {
            Debug.Log("[ThrissurWorldValidator] Validating presence of all regional landmarks...");
            string[] requiredLandmarks = new[]
            {
                "Landmark_Sakthan_Thampuran_Bus_Stand",
                "Landmark_Vadakke_Bus_Stand",
                "Landmark_Thekkinkadu_Maidan",
                "Landmark_Puthanpalli_Dolours_Basilica",
                "Landmark_Paliyekkara_Toll_Plaza",
                "Landmark_Peechi_Dam_Structure",
                "Landmark_Guruvayur_Sree_Krishna_Temple",
                "Landmark_Triprayar_Sree_Rama_Temple"
            };

            foreach (var lmName in requiredLandmarks)
            {
                var lm = GameObject.Find(lmName);
                if (lm == null)
                {
                    Debug.LogError($"[ThrissurWorldValidator] Required landmark '{lmName}' not found in scene!");
                    return false;
                }
            }

            // Verify Puthanpalli Bible Tower
            var church = GameObject.Find("Landmark_Puthanpalli_Dolours_Basilica");
            var bibleTower = church.transform.Find("Bible_Tower_260ft");
            if (bibleTower == null || bibleTower.localScale.y < 35f)
            {
                Debug.LogError("[ThrissurWorldValidator] Puthanpalli Bible Tower (260ft) missing or improperly scaled!");
                return false;
            }

            // Verify Peechi Reservoir & Coastal Beaches (if present in build)
            if (GameObject.Find("Peechi_Water_Surface") == null)
            {
                Debug.Log("[ThrissurWorldValidator] Peechi Dam reservoir water surface skipped (environment stripped per user request).");
            }
            if (GameObject.Find("Chavakkad_Beach_Shoreline") == null || GameObject.Find("Triprayar_Beach_Shoreline") == null)
            {
                Debug.Log("[ThrissurWorldValidator] Coastal shoreline scenery skipped (environment stripped per user request).");
            }

            Debug.Log("[ThrissurWorldValidator] All regional landmarks verified successfully.");
            return true;
        }

        private static bool ValidateRoadNetwork()
        {
            Debug.Log("[ThrissurWorldValidator] Validating connected Blender OSM road network and colliders...");
            var roadsRoot = GameObject.Find("Thrissur_Road_Network");
            if (roadsRoot == null)
            {
                Debug.LogError("[ThrissurWorldValidator] 'Thrissur_Road_Network' missing!");
                return false;
            }

            string[] requiredBlenderRoads = new[]
            {
                "ROAD_SWARAJ_ROUND",
                "ROAD_SAKTHAN_TO_SWARAJ",
                "ROAD_SWARAJ_TO_VADAKKE",
                "ROAD_PUTHANPALLI_LINK",
                "ROAD_SWARAJ_TO_MANNUTHY",
                "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
                "ROAD_SAKTHAN_TO_OLLUR",
                "ROAD_MANNUTHY_TO_PEECHI_DAM",
                "ROAD_SWARAJ_TO_WEST_FORT",
                "ROAD_WEST_FORT_TO_KANJANI",
                "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
                "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
                "ROAD_SH61_TRIPRAYAR_TO_KANJANI"
            };

            var mcs = roadsRoot.GetComponentsInChildren<MeshCollider>(true);
            if (mcs.Length < requiredBlenderRoads.Length)
            {
                Debug.LogError($"[ThrissurWorldValidator] Found {mcs.Length} Blender road mesh colliders, expected at least {requiredBlenderRoads.Length}!");
                return false;
            }

            foreach (var roadId in requiredBlenderRoads)
            {
                bool found = false;
                foreach (var mc in mcs)
                {
                    if (mc != null && mc.gameObject.name.IndexOf(roadId, StringComparison.OrdinalIgnoreCase) >= 0)
                    {
                        found = true;
                        break;
                    }
                }
                if (!found)
                {
                    Debug.LogError($"[ThrissurWorldValidator] Required Blender OSM road '{roadId}' missing from Thrissur_Road_Network!");
                    return false;
                }
            }

            // Verify 0 trees exist in scene ("for now no trees. just build realistic Thrissur roads only")
            var allTransforms = UnityEngine.Object.FindObjectsByType<Transform>(FindObjectsSortMode.None);
            foreach (var t in allTransforms)
            {
                if (t == null) continue;
                if (t.name.IndexOf("Coconut_Palm", StringComparison.OrdinalIgnoreCase) >= 0 ||
                    t.name.IndexOf("Kerala_Rain_Tree", StringComparison.OrdinalIgnoreCase) >= 0 ||
                    t.name.StartsWith("Palm_", StringComparison.OrdinalIgnoreCase))
                {
                    Debug.LogError($"[ThrissurWorldValidator] Found prohibited tree object '{t.name}' in scene!");
                    return false;
                }
            }

            Debug.Log($"[ThrissurWorldValidator] Blender OSM road network verified: {mcs.Length} driveable road mesh colliders, 0 trees.");
            return true;
        }

        private static bool ValidateIndianSignage()
        {
            Debug.Log("[ThrissurWorldValidator] Validating Indian road signs...");
            var signsRoot = GameObject.Find("Indian_Traffic_Signs");
            if (signsRoot == null)
            {
                Debug.Log("[ThrissurWorldValidator] 'Indian_Traffic_Signs' not present in scene (roadside boards removed per user request).");
                return true;
            }

            // Zero legacy TextMesh assertion (prevents GUI/Text Shader crash in URP on mobile)
            var legacyTextMeshes = signsRoot.GetComponentsInChildren<TextMesh>();
            if (legacyTextMeshes.Length > 0)
            {
                Debug.LogError($"[ThrissurWorldValidator] Found {legacyTextMeshes.Length} legacy TextMesh components! Must be 0 to avoid URP Vulkan/GLES crash.");
                return false;
            }

            var signTexts = signsRoot.GetComponentsInChildren<UnityEngine.UI.Text>();
            if (signTexts.Length < 6)
            {
                Debug.LogError($"[ThrissurWorldValidator] Found only {signTexts.Length} signage boards, expected at least 6!");
                return false;
            }

            // Verify Malayalam text present
            bool foundMalayalam = false;
            foreach (var txt in signTexts)
            {
                if (txt.text.Contains("റൗണ്ട്") || txt.text.Contains("ടോൾ") || txt.text.Contains("ക്ഷേത്രം") || txt.text.Contains("പീച്ചി") || txt.text.Contains("സ്റ്റാൻഡ്"))
                {
                    foundMalayalam = true;
                    break;
                }
            }

            if (!foundMalayalam)
            {
                Debug.LogError("[ThrissurWorldValidator] No Malayalam script found on Indian road signage!");
                return false;
            }

            Debug.Log($"[ThrissurWorldValidator] Indian signage verified: {signTexts.Length} URP World-Space text elements with bilingual Malayalam/English guidance and 0 legacy TextMeshes.");
            return true;
        }

        private static bool ValidateHUDAndInput()
        {
            Debug.Log("[ThrissurWorldValidator] Validating HUD canvas and InputSystem integration...");
            var esGo = GameObject.Find("EventSystem");
            if (esGo == null || esGo.GetComponent<UnityEngine.InputSystem.UI.InputSystemUIInputModule>() == null)
            {
                Debug.LogError("[ThrissurWorldValidator] EventSystem is missing InputSystemUIInputModule!");
                return false;
            }

            var hudGo = GameObject.Find("BusDashboardHUD");
            if (hudGo == null)
            {
                Debug.LogError("[ThrissurWorldValidator] BusDashboardHUD missing from scene!");
                return false;
            }

            var hud = hudGo.GetComponent<BusDashboardHUD>();
            MethodInfo awakeMethod = typeof(BusDashboardHUD).GetMethod("Awake", BindingFlags.NonPublic | BindingFlags.Instance);
            if (awakeMethod != null) awakeMethod.Invoke(hud, null);

            var canvasGo = GameObject.Find("HUD_Canvas");
            if (canvasGo == null)
            {
                Debug.LogError("[ThrissurWorldValidator] 'HUD_Canvas' not instantiated!");
                return false;
            }

            Transform statusText = canvasGo.transform.Find("SafeAreaContainer/TopBar/StatusCapsule/StatusText");
            if (statusText == null)
            {
                Debug.LogError("[ThrissurWorldValidator] StatusCapsule/StatusText missing for wallet display!");
                return false;
            }

            Debug.Log("[ThrissurWorldValidator] HUD and input system verified.");
            return true;
        }

        private static bool ValidateBuildSettings()
        {
            Debug.Log("[ThrissurWorldValidator] Validating EditorBuildSettings scenes...");
            BuildScript.UpdateBuildSettingsScenes();

            if (EditorBuildSettings.scenes.Length == 0 ||
                EditorBuildSettings.scenes[0].path != ThrissurScenePath)
            {
                Debug.LogError($"[ThrissurWorldValidator] Primary scene in build is not {ThrissurScenePath}!");
                return false;
            }

            Debug.Log("[ThrissurWorldValidator] Build settings verified with Thrissur Master Scene as primary startup scene.");
            return true;
        }
    }
}
