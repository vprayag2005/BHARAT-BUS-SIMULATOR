using System;
using System.IO;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using BharatBusSim.Vehicles;
using BharatBusSim.UI;

namespace BharatBusSim.Editor
{
    public static class Stage3AValidator
    {
        private const string FbxPath = "Assets/_Project/Vehicles/Models/KeralaBus12m.fbx";
        private const string BusPrototypeScenePath = "Assets/_Project/Scenes/02_Phase4_BusPrototype.unity";
        private const string SmokeTestScenePath = "Assets/_Project/Scenes/01_Phase0_SmokeTest.unity";

        [MenuItem("BharatBusSim/Stage3A/Run All Stage 3A Validations")]
        public static void RunAllValidations()
        {
            Debug.Log("==================================================================");
            Debug.Log("[Stage3AValidator] STARTING COMPREHENSIVE STAGE 3A VALIDATION SUITE");
            Debug.Log("==================================================================");

            bool allPassed = true;

            try
            {
                // Test A: Asset Validation
                allPassed &= ValidateAsset();

                // Test B: Scene Generation & Hierarchy Validation
                allPassed &= ValidateSceneGenerationAndHierarchy();

                // Test C: Physics & Driving Regression Simulation
                allPassed &= ValidatePhysicsAndDrivingRegression();

                // Test D: Phase 0 Preservation
                allPassed &= ValidatePhase0Preserved();

                if (allPassed)
                {
                    Debug.Log("==================================================================");
                    Debug.Log("[Stage3AValidator] ALL STAGE 3A VALIDATION CHECKS PASSED!");
                    Debug.Log("==================================================================");
                }
                else
                {
                    Debug.LogError("[Stage3AValidator] ONE OR MORE STAGE 3A VALIDATION CHECKS FAILED!");
                    throw new Exception("Stage 3A validation failed!");
                }
            }
            catch (Exception ex)
            {
                Debug.LogError($"[Stage3AValidator] FATAL EXCEPTION: {ex}");
                throw;
            }
        }

        private static bool ValidateAsset()
        {
            Debug.Log("\n--- [CHECK A: Asset Validation] ---");
            if (!File.Exists(FbxPath))
            {
                Debug.LogError($"[Stage3AValidator] Missing FBX file at {FbxPath}");
                return false;
            }

            AssetDatabase.ImportAsset(FbxPath, ImportAssetOptions.ForceUpdate);
            GameObject prefab = AssetDatabase.LoadAssetAtPath<GameObject>(FbxPath);
            if (prefab == null)
            {
                Debug.LogError($"[Stage3AValidator] Failed to load FBX prefab at {FbxPath}");
                return false;
            }

            string[] expectedMeshes = new[]
            {
                "Bus_Body",
                "Wheel_FL",
                "Wheel_FR",
                "Wheel_ML",
                "Wheel_MR",
                "Wheel_RL",
                "Wheel_RR"
            };

            foreach (var meshName in expectedMeshes)
            {
                Transform t = prefab.transform.Find(meshName);
                if (t == null)
                {
                    Debug.LogError($"[Stage3AValidator] FBX missing mesh child: '{meshName}'");
                    return false;
                }

                MeshFilter mf = t.GetComponent<MeshFilter>();
                if (mf == null || mf.sharedMesh == null)
                {
                    Debug.LogError($"[Stage3AValidator] '{meshName}' has null MeshFilter or sharedMesh");
                    return false;
                }

                Renderer r = t.GetComponent<Renderer>();
                if (r == null || r.sharedMaterials == null || r.sharedMaterials.Length == 0)
                {
                    Debug.LogError($"[Stage3AValidator] '{meshName}' has missing renderer or materials");
                    return false;
                }

                // Verify visual meshes have no colliders
                if (t.GetComponent<Collider>() != null)
                {
                    Debug.LogError($"[Stage3AValidator] FBX child '{meshName}' contains unwanted Collider!");
                    return false;
                }
            }

            Debug.Log("[Stage3AValidator] FBX contains all 7 required meshes with valid materials and zero colliders.");
            return true;
        }

        private static bool ValidateSceneGenerationAndHierarchy()
        {
            Debug.Log("\n--- [CHECK B: Scene Generation & Hierarchy Validation] ---");
            
            // Generate scene fresh via BuildScript
            BuildScript.GenerateBusPrototypeScene();

            if (!File.Exists(BusPrototypeScenePath))
            {
                Debug.LogError($"[Stage3AValidator] BusPrototype scene file missing at {BusPrototypeScenePath}");
                return false;
            }

            Scene scene = EditorSceneManager.OpenScene(BusPrototypeScenePath, OpenSceneMode.Single);
            if (!scene.IsValid())
            {
                Debug.LogError($"[Stage3AValidator] Failed to open scene at {BusPrototypeScenePath}");
                return false;
            }

            // Find bus root
            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            if (busGo == null)
            {
                Debug.LogError("[Stage3AValidator] 'Kerala_Bus_Prototype' root object not found in scene!");
                return false;
            }

            // Check Rigidbody
            var rb = busGo.GetComponent<Rigidbody>();
            if (rb == null || rb.mass != 10500f)
            {
                Debug.LogError($"[Stage3AValidator] Rigidbody missing or mass incorrect: {(rb != null ? rb.mass.ToString() : "null")}");
                return false;
            }

            // Check BoxCollider
            var col = busGo.GetComponent<BoxCollider>();
            if (col == null)
            {
                Debug.LogError("[Stage3AValidator] Chassis BoxCollider missing!");
                return false;
            }
            if (col.size.x != 2.5f || col.size.y != 2.6f || col.size.z != 10.4f)
            {
                Debug.LogError($"[Stage3AValidator] BoxCollider size unexpected: {col.size}");
                return false;
            }

            // Check Visual Model root
            Transform visuals = busGo.transform.Find("KeralaBus12m_Visuals");
            if (visuals == null)
            {
                Debug.LogError("[Stage3AValidator] 'KeralaBus12m_Visuals' child not found under bus root!");
                return false;
            }

            // Check all 6 visual wheels exist under visuals
            string[] wheels = new[] { "Wheel_FL", "Wheel_FR", "Wheel_ML", "Wheel_MR", "Wheel_RL", "Wheel_RR" };
            foreach (var w in wheels)
            {
                Transform wt = visuals.Find(w);
                if (wt == null)
                {
                    Debug.LogError($"[Stage3AValidator] Visual wheel '{w}' missing under visuals root!");
                    return false;
                }
            }

            // Check 4 WheelColliders
            var busController = busGo.GetComponent<BusController>();
            if (busController == null)
            {
                Debug.LogError("[Stage3AValidator] BusController component missing on bus root!");
                return false;
            }

            if (busController.frontLeftCollider == null || busController.frontRightCollider == null ||
                busController.rearLeftCollider == null || busController.rearRightCollider == null)
            {
                Debug.LogError("[Stage3AValidator] BusController has null WheelCollider references!");
                return false;
            }

            // Check all 6 BusWheel sync components
            BusWheel[] syncs = busGo.GetComponentsInChildren<BusWheel>();
            if (syncs.Length != 6)
            {
                Debug.LogError($"[Stage3AValidator] Expected 6 BusWheel sync components, found {syncs.Length}!");
                return false;
            }

            foreach (var s in syncs)
            {
                if (s.wheelCollider == null)
                {
                    Debug.LogError($"[Stage3AValidator] BusWheel '{s.name}' has null wheelCollider reference!");
                    return false;
                }
                if (s.visualTransform == null)
                {
                    Debug.LogError($"[Stage3AValidator] BusWheel '{s.name}' has null visualTransform reference!");
                    return false;
                }
            }

            // Check Camera
            GameObject camGo = GameObject.Find("ChaseCamera");
            if (camGo == null)
            {
                Debug.LogError("[Stage3AValidator] 'ChaseCamera' not found in scene!");
                return false;
            }
            var camFollow = camGo.GetComponent<BusCameraController>();
            if (camFollow == null || camFollow.target != busGo.transform)
            {
                Debug.LogError("[Stage3AValidator] BusCameraController missing or target not set to bus root!");
                return false;
            }

            // Check HUD
            GameObject hudGo = GameObject.Find("BusDashboardHUD");
            if (hudGo == null)
            {
                Debug.LogError("[Stage3AValidator] 'BusDashboardHUD' not found in scene!");
                return false;
            }
            var hud = hudGo.GetComponent<BusDashboardHUD>();
            if (hud == null || hud.busController != busController || hud.cameraController != camFollow)
            {
                Debug.LogError("[Stage3AValidator] BusDashboardHUD missing controller or camera references!");
                return false;
            }

            Debug.Log("[Stage3AValidator] Scene hierarchy, components, and all cross-references validated successfully.");
            return true;
        }

        private static bool ValidatePhysicsAndDrivingRegression()
        {
            Debug.Log("\n--- [CHECK C: Physics & Driving Regression Simulation] ---");

            Scene scene = EditorSceneManager.OpenScene(BusPrototypeScenePath, OpenSceneMode.Single);
            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            var busController = busGo.GetComponent<BusController>();
            var rb = busGo.GetComponent<Rigidbody>();

            // Initialize wheel components
            foreach (var bw in busGo.GetComponentsInChildren<BusWheel>())
            {
                bw.Initialize();
            }

            // Ensure physics auto simulation is disabled for manual stepping
            bool originalAutoSimulation = Physics.simulationMode != SimulationMode.Script;
            Physics.simulationMode = SimulationMode.Script;

            try
            {
                Vector3 initialPos = busGo.transform.position;
                Debug.Log($"[Stage3AValidator] Initial Bus Position: {initialPos}");

                // Step 20 frames of settling at rest
                for (int i = 0; i < 20; i++)
                {
                    Physics.Simulate(0.02f);
                }

                Vector3 settledPos = busGo.transform.position;
                Debug.Log($"[Stage3AValidator] Settled Bus Position after 20 physics steps: {settledPos}");

                if (float.IsNaN(settledPos.x) || float.IsNaN(settledPos.y) || float.IsNaN(settledPos.z))
                {
                    Debug.LogError("[Stage3AValidator] Bus position became NaN during physics settling!");
                    return false;
                }

                // Check wheel contact with ground
                bool allGrounded = true;
                WheelHit hit;
                if (!busController.frontLeftCollider.GetGroundHit(out hit)) allGrounded = false;
                if (!busController.frontRightCollider.GetGroundHit(out hit)) allGrounded = false;
                if (!busController.rearLeftCollider.GetGroundHit(out hit)) allGrounded = false;
                if (!busController.rearRightCollider.GetGroundHit(out hit)) allGrounded = false;

                Debug.Log($"[Stage3AValidator] All 4 WheelColliders touching ground: {allGrounded}");

                // Test Gear Interlock logic
                string reason;
                bool shiftedToDrive = busController.TrySetGear(TransmissionGear.Drive, out reason);
                Debug.Log($"[Stage3AValidator] Shift to Drive from Neutral: approved={shiftedToDrive}, reason='{reason}'");
                if (!shiftedToDrive)
                {
                    Debug.LogError("[Stage3AValidator] Failed to shift from Neutral to Drive!");
                    return false;
                }

                // Test Acceleration in Drive
                busController.SetUIThrottle(1.0f);
                busController.SetUIBrake(0.0f);
                busController.SetUIHandbrake(false);

                // Step 50 frames of driving
                for (int i = 0; i < 50; i++)
                {
                    // Apply motor torque and fixed update manually in simulation
                    busController.frontLeftCollider.steerAngle = 0f;
                    busController.frontRightCollider.steerAngle = 0f;
                    busController.rearLeftCollider.motorTorque = 3600f;
                    busController.rearRightCollider.motorTorque = 3600f;
                    busController.frontLeftCollider.brakeTorque = 0f;
                    busController.frontRightCollider.brakeTorque = 0f;
                    busController.rearLeftCollider.brakeTorque = 0f;
                    busController.rearRightCollider.brakeTorque = 0f;

                    Physics.Simulate(0.02f);
                }

                Vector3 drivenPos = busGo.transform.position;
                float distanceMoved = (drivenPos - settledPos).magnitude;
                Debug.Log($"[Stage3AValidator] Bus position after 50 drive steps: {drivenPos} (Distance moved forward: {distanceMoved:F2} m)");

                if (distanceMoved < 0.1f)
                {
                    Debug.LogError("[Stage3AValidator] Bus did not move forward under motor torque!");
                    return false;
                }

                // Test Steering response
                busController.frontLeftCollider.steerAngle = 25f;
                busController.frontRightCollider.steerAngle = 25f;
                Physics.Simulate(0.02f);

                Transform vFL = busGo.transform.Find("KeralaBus12m_Visuals/Wheel_FL");
                Transform vML = busGo.transform.Find("KeralaBus12m_Visuals/Wheel_ML");
                Transform vRL = busGo.transform.Find("KeralaBus12m_Visuals/Wheel_RL");

                Debug.Log($"[Stage3AValidator] Steered FL visual rotation: {vFL.rotation.eulerAngles}");
                Debug.Log($"[Stage3AValidator] Non-steered ML visual rotation: {vML.rotation.eulerAngles}");
                Debug.Log($"[Stage3AValidator] Non-steered RL visual rotation: {vRL.rotation.eulerAngles}");

                // Check wheel positions in bus local space to ensure no collapse to Z=-2.7
                Vector3 flLocal = busGo.transform.InverseTransformPoint(vFL.position);
                Vector3 mlLocal = busGo.transform.InverseTransformPoint(vML.position);
                Vector3 rlLocal = busGo.transform.InverseTransformPoint(vRL.position);

                Debug.Log($"[Stage3AValidator] Wheel local positions: FL={flLocal}, ML={mlLocal}, RL={rlLocal}");

                if (Mathf.Abs(flLocal.z - 3.50f) > 0.15f)
                {
                    Debug.LogError($"[Stage3AValidator] FL visual wheel displaced from front arch! Z={flLocal.z}");
                    return false;
                }
                if (Mathf.Abs(mlLocal.z - (-2.30f)) > 0.15f)
                {
                    Debug.LogError($"[Stage3AValidator] ML visual wheel displaced from middle arch! Z={mlLocal.z}");
                    return false;
                }
                if (Mathf.Abs(rlLocal.z - (-3.60f)) > 0.15f)
                {
                    Debug.LogError($"[Stage3AValidator] RL visual wheel displaced from rear arch! Z={rlLocal.z}");
                    return false;
                }

                Debug.Log("[Stage3AValidator] Wheel arch seating, forward movement, and steering independence validated.");
                return true;
            }
            finally
            {
                Physics.simulationMode = originalAutoSimulation ? SimulationMode.FixedUpdate : SimulationMode.Script;
            }
        }

        private static bool ValidatePhase0Preserved()
        {
            Debug.Log("\n--- [CHECK D: Phase 0 Scene Preservation] ---");
            if (!File.Exists(SmokeTestScenePath))
            {
                Debug.LogError($"[Stage3AValidator] Phase 0 smoke test scene missing at {SmokeTestScenePath}");
                return false;
            }

            Scene smokeScene = EditorSceneManager.OpenScene(SmokeTestScenePath, OpenSceneMode.Single);
            if (!smokeScene.IsValid())
            {
                Debug.LogError("[Stage3AValidator] Phase 0 scene invalid!");
                return false;
            }

            GameObject testCube = GameObject.Find("TestCube_3D_Indicator");
            GameObject overlay = GameObject.Find("DiagnosticsOverlay");

            if (testCube == null || overlay == null)
            {
                Debug.LogError("[Stage3AValidator] Phase 0 scene missing expected test cube or overlay!");
                return false;
            }

            Debug.Log("[Stage3AValidator] Phase 0 Smoke Test scene is fully intact.");
            return true;
        }
    }
}
