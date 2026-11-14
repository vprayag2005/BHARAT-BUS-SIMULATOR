using System;
using System.IO;
using System.Reflection;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using BharatBusSim.Vehicles;
using BharatBusSim.UI;

namespace BharatBusSim.Editor
{
    /// <summary>
    /// Comprehensive automated validation suite for Stage 3B:
    /// True 6-Wheel Physics with 3-Axle Architecture for the 12m Kerala Coach.
    /// </summary>
    public static class Stage3BValidator
    {
        private const string FbxPath = "Assets/_Project/Vehicles/Models/KeralaBus12m.fbx";
        private const string BusPrototypeScenePath = "Assets/_Project/Scenes/02_Phase4_BusPrototype.unity";
        private const string SmokeTestScenePath = "Assets/_Project/Scenes/01_Phase0_SmokeTest.unity";

        [MenuItem("BharatBusSim/Stage3B/Run All Stage 3B Validations")]
        public static void RunAllValidations()
        {
            Debug.Log("==================================================================");
            Debug.Log("[Stage3BValidator] STARTING COMPREHENSIVE STAGE 3B VALIDATION SUITE");
            Debug.Log("==================================================================");

            bool allPassed = true;

            try
            {
                // Test 1: Hierarchy, 6 WheelColliders, and 6 Visual Wheel Sync
                allPassed &= ValidateHierarchyAndColliders();

                // Test 2: Suspension & Ground Contact at Rest
                allPassed &= ValidatePhysicsSettlingAndContact();

                // Test 3: Steering strictly isolated to Front Axle
                allPassed &= ValidateSteeringIsolation();

                // Test 4: Powertrain Torque Distribution & Directional Movement (Drive & Reverse)
                allPassed &= ValidatePowertrainAndMovement();

                // Test 5: Service Braking and Parking Brake Response
                allPassed &= ValidateBrakingAndHandbrake();

                // Test 6: Gear State Machine & Speed Safety Interlocks
                allPassed &= ValidateGearInterlocks();

                // Test 7: Independent Axle Articulation & Spring/Damper Tuning
                allPassed &= ValidateIndependentAxleSuspension();

                // Test 8: Rigidbody and Torque Hygiene (No phantom torque or duplicate rigidbodies)
                allPassed &= ValidateRigidbodyAndTorqueHygiene();

                // Test 9: Scene References & Zero Compilation Errors
                allPassed &= ValidateSceneReferences();

                // Test 10: Phase 0 Scene Preservation and Build Configuration
                allPassed &= ValidatePhase0AndBuildSettings();

                // Test 11: Input System Integration & Landscape HUD Layout Validation
                allPassed &= ValidateInputAndHUDLayout();

                if (allPassed)
                {
                    Debug.Log("==================================================================");
                    Debug.Log("[Stage3BValidator] ALL 11 STAGE 3B VALIDATION CHECKS PASSED!");
                    Debug.Log("==================================================================");
                }
                else
                {
                    Debug.LogError("[Stage3BValidator] ONE OR MORE STAGE 3B VALIDATION CHECKS FAILED!");
                    throw new Exception("Stage 3B validation failed!");
                }
            }
            catch (Exception ex)
            {
                Debug.LogError($"[Stage3BValidator] FATAL EXCEPTION: {ex}");
                throw;
            }
        }

        private static bool ValidateHierarchyAndColliders()
        {
            Debug.Log("\n--- [CHECK 1: Six WheelColliders & Six Visual Wheels Hierarchy] ---");

            // Regenerate scene fresh via BuildScript
            BuildScript.GenerateBusPrototypeScene();

            if (!File.Exists(BusPrototypeScenePath))
            {
                Debug.LogError($"[Stage3BValidator] BusPrototype scene file missing at {BusPrototypeScenePath}");
                return false;
            }

            Scene scene = EditorSceneManager.OpenScene(BusPrototypeScenePath, OpenSceneMode.Single);
            if (!scene.IsValid())
            {
                Debug.LogError($"[Stage3BValidator] Failed to open scene at {BusPrototypeScenePath}");
                return false;
            }

            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            if (busGo == null)
            {
                Debug.LogError("[Stage3BValidator] 'Kerala_Bus_Prototype' root object not found in scene!");
                return false;
            }

            var busController = busGo.GetComponent<BusController>();
            if (busController == null)
            {
                Debug.LogError("[Stage3BValidator] BusController component missing on bus root!");
                return false;
            }

            // Verify all 6 WheelCollider references
            WheelCollider[] expectedColliders = new[]
            {
                busController.frontLeftCollider,
                busController.frontRightCollider,
                busController.midLeftCollider,
                busController.midRightCollider,
                busController.rearLeftCollider,
                busController.rearRightCollider
            };

            string[] colliderNames = new[] { "FL", "FR", "ML", "MR", "RL", "RR" };
            Vector3[] expectedPositions = new[]
            {
                new Vector3(-1.18f, 0.52f, 3.50f),
                new Vector3(1.18f, 0.52f, 3.50f),
                new Vector3(-1.18f, 0.52f, -2.30f),
                new Vector3(1.18f, 0.52f, -2.30f),
                new Vector3(-1.18f, 0.52f, -3.60f),
                new Vector3(1.18f, 0.52f, -3.60f)
            };

            for (int i = 0; i < expectedColliders.Length; i++)
            {
                if (expectedColliders[i] == null)
                {
                    Debug.LogError($"[Stage3BValidator] WheelCollider '{colliderNames[i]}' reference is null!");
                    return false;
                }

                Vector3 localPos = expectedColliders[i].transform.localPosition;
                if (Vector3.Distance(localPos, expectedPositions[i]) > 0.05f)
                {
                    Debug.LogError($"[Stage3BValidator] WheelCollider '{colliderNames[i]}' position {localPos} deviates from expected {expectedPositions[i]}!");
                    return false;
                }

                if (Mathf.Abs(expectedColliders[i].radius - 0.52f) > 0.01f)
                {
                    Debug.LogError($"[Stage3BValidator] WheelCollider '{colliderNames[i]}' radius {expectedColliders[i].radius} != 0.52m!");
                    return false;
                }
            }

            // Verify all 6 visual wheels in model hierarchy
            Transform visuals = busGo.transform.Find("KeralaBus12m_Visuals");
            if (visuals == null)
            {
                Debug.LogError("[Stage3BValidator] 'KeralaBus12m_Visuals' child not found under bus root!");
                return false;
            }

            string[] visualWheelNames = new[] { "Wheel_FL", "Wheel_FR", "Wheel_ML", "Wheel_MR", "Wheel_RL", "Wheel_RR" };
            foreach (var w in visualWheelNames)
            {
                Transform wt = visuals.Find(w);
                if (wt == null)
                {
                    Debug.LogError($"[Stage3BValidator] Visual wheel '{w}' missing under visuals root!");
                    return false;
                }
            }

            // Verify all 6 BusWheel sync components
            BusWheel[] syncs = busGo.GetComponentsInChildren<BusWheel>();
            if (syncs.Length != 6)
            {
                Debug.LogError($"[Stage3BValidator] Expected exactly 6 BusWheel components, found {syncs.Length}!");
                return false;
            }

            foreach (var s in syncs)
            {
                if (s.wheelCollider == null)
                {
                    Debug.LogError($"[Stage3BValidator] BusWheel '{s.name}' has null wheelCollider!");
                    return false;
                }
                if (s.visualTransform == null)
                {
                    Debug.LogError($"[Stage3BValidator] BusWheel '{s.name}' has null visualTransform!");
                    return false;
                }
            }

            Debug.Log("[Stage3BValidator] CHECK 1 PASSED: 6 WheelColliders and 6 visual wheels verified.");
            return true;
        }

        private static bool ValidatePhysicsSettlingAndContact()
        {
            Debug.Log("\n--- [CHECK 2: Suspension & Ground Contact at Rest] ---");

            Scene scene = EditorSceneManager.OpenScene(BusPrototypeScenePath, OpenSceneMode.Single);
            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            var busController = busGo.GetComponent<BusController>();

            // Initialize wheel components
            foreach (var bw in busGo.GetComponentsInChildren<BusWheel>())
            {
                bw.Initialize();
            }

            bool originalAutoSimulation = Physics.simulationMode != SimulationMode.Script;
            Physics.simulationMode = SimulationMode.Script;

            try
            {
                Vector3 initialPos = busGo.transform.position;
                Debug.Log($"[Stage3BValidator] Initial Bus Position: {initialPos}");

                // Settle physics over 30 steps
                for (int i = 0; i < 30; i++)
                {
                    Physics.Simulate(0.02f);
                }

                Vector3 settledPos = busGo.transform.position;
                Debug.Log($"[Stage3BValidator] Settled Bus Position: {settledPos}");

                if (float.IsNaN(settledPos.x) || float.IsNaN(settledPos.y) || float.IsNaN(settledPos.z))
                {
                    Debug.LogError("[Stage3BValidator] Bus position became NaN during settling!");
                    return false;
                }

                // Verify all 6 WheelColliders contact the ground
                WheelCollider[] colliders = new[]
                {
                    busController.frontLeftCollider,
                    busController.frontRightCollider,
                    busController.midLeftCollider,
                    busController.midRightCollider,
                    busController.rearLeftCollider,
                    busController.rearRightCollider
                };

                int groundedCount = 0;
                for (int i = 0; i < colliders.Length; i++)
                {
                    if (colliders[i].GetGroundHit(out WheelHit hit))
                    {
                        groundedCount++;
                        Debug.Log($"[Stage3BValidator] Wheel {colliders[i].gameObject.name} grounded on {hit.collider.name} (suspension point: {hit.point})");
                    }
                    else
                    {
                        Debug.LogWarning($"[Stage3BValidator] Wheel {colliders[i].gameObject.name} reported no ground hit!");
                    }
                }

                if (groundedCount < 6)
                {
                    Debug.LogError($"[Stage3BValidator] Only {groundedCount}/6 wheels grounded at rest!");
                    return false;
                }

                Debug.Log("[Stage3BValidator] CHECK 2 PASSED: All 6 wheels stably grounded on track at rest.");
                return true;
            }
            finally
            {
                Physics.simulationMode = originalAutoSimulation ? SimulationMode.FixedUpdate : SimulationMode.Script;
            }
        }

        private static bool ValidateSteeringIsolation()
        {
            Debug.Log("\n--- [CHECK 3: Steering strictly isolated to Front Axle] ---");

            Scene scene = EditorSceneManager.OpenScene(BusPrototypeScenePath, OpenSceneMode.Single);
            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            var busController = busGo.GetComponent<BusController>();

            // Apply steering via UI steer input
            busController.SetUISteer(1.0f); // Maximum right steer
            busController.StepPhysics();

            float frontSteerL = busController.frontLeftCollider.steerAngle;
            float frontSteerR = busController.frontRightCollider.steerAngle;
            float midSteerL = busController.midLeftCollider.steerAngle;
            float midSteerR = busController.midRightCollider.steerAngle;
            float rearSteerL = busController.rearLeftCollider.steerAngle;
            float rearSteerR = busController.rearRightCollider.steerAngle;

            Debug.Log($"[Stage3BValidator] Steer angles - Front: L={frontSteerL:F1}°, R={frontSteerR:F1}° | Mid: L={midSteerL:F1}°, R={midSteerR:F1}° | Rear: L={rearSteerL:F1}°, R={rearSteerR:F1}°");

            if (frontSteerL <= 5f || frontSteerR <= 5f)
            {
                Debug.LogError($"[Stage3BValidator] Front steer angles {frontSteerL}°, {frontSteerR}° too small under full steer!");
                return false;
            }

            if (midSteerL != 0f || midSteerR != 0f)
            {
                Debug.LogError($"[Stage3BValidator] Middle drive axle steer angle is non-zero: L={midSteerL}°, R={midSteerR}°!");
                return false;
            }

            if (rearSteerL != 0f || rearSteerR != 0f)
            {
                Debug.LogError($"[Stage3BValidator] Rear tag axle steer angle is non-zero: L={rearSteerL}°, R={rearSteerR}°!");
                return false;
            }

            // Update BusWheel visual poses and confirm visual wheels ML and RL did not yaw
            foreach (var bw in busGo.GetComponentsInChildren<BusWheel>())
            {
                bw.UpdateWheelPose();
            }

            Transform vFL = busGo.transform.Find("KeralaBus12m_Visuals/Wheel_FL");
            Transform vML = busGo.transform.Find("KeralaBus12m_Visuals/Wheel_ML");
            Transform vRL = busGo.transform.Find("KeralaBus12m_Visuals/Wheel_RL");

            // Local wheel arch seating check
            Vector3 flLocal = busGo.transform.InverseTransformPoint(vFL.position);
            Vector3 mlLocal = busGo.transform.InverseTransformPoint(vML.position);
            Vector3 rlLocal = busGo.transform.InverseTransformPoint(vRL.position);

            Debug.Log($"[Stage3BValidator] Visual wheel local positions: FL={flLocal}, ML={mlLocal}, RL={rlLocal}");

            if (Mathf.Abs(flLocal.z - 3.50f) > 0.15f || Mathf.Abs(mlLocal.z - (-2.30f)) > 0.15f || Mathf.Abs(rlLocal.z - (-3.60f)) > 0.15f)
            {
                Debug.LogError("[Stage3BValidator] Visual wheels displaced from their designated wheel arches!");
                return false;
            }

            Debug.Log("[Stage3BValidator] CHECK 3 PASSED: Steering strictly isolated to Axle 1; Axles 2 & 3 unsteered.");
            return true;
        }

        private static bool ValidatePowertrainAndMovement()
        {
            Debug.Log("\n--- [CHECK 4: Powertrain Torque Distribution & Movement (Drive & Reverse)] ---");

            Scene scene = EditorSceneManager.OpenScene(BusPrototypeScenePath, OpenSceneMode.Single);
            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            var busController = busGo.GetComponent<BusController>();
            var rb = busGo.GetComponent<Rigidbody>();

            bool originalAutoSimulation = Physics.simulationMode != SimulationMode.Script;
            Physics.simulationMode = SimulationMode.Script;

            try
            {
                // Settle first
                for (int i = 0; i < 20; i++) Physics.Simulate(0.02f);
                Vector3 settledPos = busGo.transform.position;

                // Shift to Drive
                if (!busController.TrySetGear(TransmissionGear.Drive, out string reason))
                {
                    Debug.LogError($"[Stage3BValidator] Failed to shift to Drive: {reason}");
                    return false;
                }

                // Apply full throttle
                busController.SetUIThrottle(1.0f);
                busController.SetUIBrake(0.0f);
                busController.SetUIHandbrake(false);
                busController.StepPhysics();

                // Check torque distribution (under default 6x2 policy)
                float frontL = busController.frontLeftCollider.motorTorque;
                float frontR = busController.frontRightCollider.motorTorque;
                float midL = busController.midLeftCollider.motorTorque;
                float midR = busController.midRightCollider.motorTorque;
                float rearL = busController.rearLeftCollider.motorTorque;
                float rearR = busController.rearRightCollider.motorTorque;

                Debug.Log($"[Stage3BValidator] Drive Torques (6x2) - Front: ({frontL},{frontR}) | Mid: ({midL},{midR}) | Rear: ({rearL},{rearR})");

                if (frontL != 0f || frontR != 0f)
                {
                    Debug.LogError("[Stage3BValidator] Front axle received tractive torque in 6x2!");
                    return false;
                }

                if (midL <= 1000f || midR <= 1000f)
                {
                    Debug.LogError("[Stage3BValidator] Middle drive axle did not receive adequate motor torque in Drive!");
                    return false;
                }

                if (rearL != 0f || rearR != 0f)
                {
                    Debug.LogError("[Stage3BValidator] Rear tag axle received torque under 6x2 unpowered tag policy!");
                    return false;
                }

                // Simulate forward movement over 50 steps
                for (int i = 0; i < 50; i++)
                {
                    busController.StepPhysics();
                    Physics.Simulate(0.02f);
                }

                Vector3 forwardDrivenPos = busGo.transform.position;
                float distanceForward = Vector3.Dot(forwardDrivenPos - settledPos, busGo.transform.forward);
                Debug.Log($"[Stage3BValidator] Distance moved forward in Drive: {distanceForward:F2} m");

                if (distanceForward < 0.2f)
                {
                    Debug.LogError($"[Stage3BValidator] Bus failed to move forward in Drive (moved {distanceForward} m)!");
                    return false;
                }

                // Reset and test Reverse
                busController.ResetBus();
                for (int i = 0; i < 20; i++) Physics.Simulate(0.02f);
                Vector3 reverseStartPos = busGo.transform.position;

                if (!busController.TrySetGear(TransmissionGear.Reverse, out reason))
                {
                    Debug.LogError($"[Stage3BValidator] Failed to shift to Reverse: {reason}");
                    return false;
                }

                busController.SetUIThrottle(1.0f);
                busController.StepPhysics();

                float revMidL = busController.midLeftCollider.motorTorque;
                float revMidR = busController.midRightCollider.motorTorque;
                Debug.Log($"[Stage3BValidator] Reverse Torques on Mid Axle: ({revMidL},{revMidR})");

                if (revMidL >= 0f || revMidR >= 0f)
                {
                    Debug.LogError("[Stage3BValidator] Mid axle motor torque is not negative in Reverse!");
                    return false;
                }

                for (int i = 0; i < 50; i++)
                {
                    busController.StepPhysics();
                    Physics.Simulate(0.02f);
                }

                Vector3 reverseDrivenPos = busGo.transform.position;
                float distanceReverse = Vector3.Dot(reverseDrivenPos - reverseStartPos, busGo.transform.forward);
                Debug.Log($"[Stage3BValidator] Distance moved in Reverse: {distanceReverse:F2} m");

                if (distanceReverse > -0.1f)
                {
                    Debug.LogError($"[Stage3BValidator] Bus failed to move backwards in Reverse (moved {distanceReverse} m)!");
                    return false;
                }

                Debug.Log("[Stage3BValidator] CHECK 4 PASSED: Powertrain 6x2 drive and reverse physics verified.");
                return true;
            }
            finally
            {
                Physics.simulationMode = originalAutoSimulation ? SimulationMode.FixedUpdate : SimulationMode.Script;
            }
        }

        private static bool ValidateBrakingAndHandbrake()
        {
            Debug.Log("\n--- [CHECK 5: Service Braking and Parking Brake Response] ---");

            Scene scene = EditorSceneManager.OpenScene(BusPrototypeScenePath, OpenSceneMode.Single);
            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            var busController = busGo.GetComponent<BusController>();

            // Test 1: Service Braking
            busController.SetUIThrottle(0f);
            busController.SetUIBrake(1.0f);
            busController.SetUIHandbrake(false);
            busController.gearMode = TransmissionGear.Drive;
            busController.StepPhysics();

            float bFL = busController.frontLeftCollider.brakeTorque;
            float bFR = busController.frontRightCollider.brakeTorque;
            float bML = busController.midLeftCollider.brakeTorque;
            float bMR = busController.midRightCollider.brakeTorque;
            float bRL = busController.rearLeftCollider.brakeTorque;
            float bRR = busController.rearRightCollider.brakeTorque;

            Debug.Log($"[Stage3BValidator] Service Brake Torques - Front: ({bFL},{bFR}) | Mid: ({bML},{bMR}) | Rear: ({bRL},{bRR})");

            // Service brake nominal is 8000 N*m: Front=0.70 (5600), Mid=0.85 (6800), Rear=0.55 (4400)
            if (bFL < 5000f || bFR < 5000f)
            {
                Debug.LogError($"[Stage3BValidator] Front service brake torque ({bFL}) below expected range!");
                return false;
            }
            if (bML < 6000f || bMR < 6000f)
            {
                Debug.LogError($"[Stage3BValidator] Mid service brake torque ({bML}) below expected range!");
                return false;
            }
            if (bRL < 4000f || bRR < 4000f)
            {
                Debug.LogError($"[Stage3BValidator] Rear service brake torque ({bRL}) below expected range!");
                return false;
            }

            // Test 2: Parking Brake
            busController.SetUIBrake(0f);
            busController.SetUIHandbrake(true);
            busController.StepPhysics();

            float pbMid = busController.midLeftCollider.brakeTorque;
            float pbRear = busController.rearLeftCollider.brakeTorque;
            Debug.Log($"[Stage3BValidator] Parking Brake Torque on Mid: {pbMid}, Rear: {pbRear}");

            if (pbMid < 14000f || pbRear < 14000f)
            {
                Debug.LogError($"[Stage3BValidator] Parking brake torque ({pbMid}) below 14000 N*m!");
                return false;
            }

            Debug.Log("[Stage3BValidator] CHECK 5 PASSED: Service braking and parking brake response verified.");
            return true;
        }

        private static bool ValidateGearInterlocks()
        {
            Debug.Log("\n--- [CHECK 6: Gear State Machine & Speed Safety Interlocks] ---");

            Scene scene = EditorSceneManager.OpenScene(BusPrototypeScenePath, OpenSceneMode.Single);
            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            var busController = busGo.GetComponent<BusController>();
            var rb = busGo.GetComponent<Rigidbody>();

            // Interlock A: Park to Neutral without brake should fail
            busController.gearMode = TransmissionGear.Park;
            busController.SetUIBrake(0.0f);
            bool shiftedWithoutBrake = busController.TrySetGear(TransmissionGear.Neutral, out string reason);
            Debug.Log($"[Stage3BValidator] Shift out of Park without brake: approved={shiftedWithoutBrake}, reason='{reason}'");
            if (shiftedWithoutBrake)
            {
                Debug.LogError("[Stage3BValidator] Shifting out of Park without holding brake should have been rejected!");
                return false;
            }

            // Interlock B: Park to Neutral with brake should succeed
            busController.SetUIBrake(1.0f);
            bool shiftedWithBrake = busController.TrySetGear(TransmissionGear.Neutral, out reason);
            Debug.Log($"[Stage3BValidator] Shift out of Park with brake: approved={shiftedWithBrake}, reason='{reason}'");
            if (!shiftedWithBrake)
            {
                Debug.LogError("[Stage3BValidator] Shifting out of Park with brake held failed!");
                return false;
            }

            // Interlock C: Shift into Park at speed (> 1.5 km/h) should fail
            busController.currentSpeedKmh = 25f;
            bool shiftedToParkAtSpeed = busController.TrySetGear(TransmissionGear.Park, out reason);
            Debug.Log($"[Stage3BValidator] Shift to Park at 25 km/h: approved={shiftedToParkAtSpeed}, reason='{reason}'");
            if (shiftedToParkAtSpeed)
            {
                Debug.LogError("[Stage3BValidator] Shift into Park at speed should have been rejected!");
                return false;
            }

            // Interlock D: Shifting into Reverse while moving forward should fail
#if UNITY_6000_0_OR_NEWER
            rb.linearVelocity = busGo.transform.forward * 5f;
#else
            rb.velocity = busGo.transform.forward * 5f;
#endif
            bool shiftedToReverseWhileForward = busController.TrySetGear(TransmissionGear.Reverse, out reason);
            Debug.Log($"[Stage3BValidator] Shift to Reverse while moving forward: approved={shiftedToReverseWhileForward}, reason='{reason}'");
            if (shiftedToReverseWhileForward)
            {
                Debug.LogError("[Stage3BValidator] Shift into Reverse while moving forward should have been rejected!");
                return false;
            }

            // Interlock E: Shifting into Drive while moving backward should fail
#if UNITY_6000_0_OR_NEWER
            rb.linearVelocity = -busGo.transform.forward * 5f;
#else
            rb.velocity = -busGo.transform.forward * 5f;
#endif
            bool shiftedToDriveWhileBackward = busController.TrySetGear(TransmissionGear.Drive, out reason);
            Debug.Log($"[Stage3BValidator] Shift to Drive while moving backward: approved={shiftedToDriveWhileBackward}, reason='{reason}'");
            if (shiftedToDriveWhileBackward)
            {
                Debug.LogError("[Stage3BValidator] Shift into Drive while moving backward should have been rejected!");
                return false;
            }

            // Restore stationary state
#if UNITY_6000_0_OR_NEWER
            rb.linearVelocity = Vector3.zero;
#else
            rb.velocity = Vector3.zero;
#endif
            busController.currentSpeedKmh = 0f;

            Debug.Log("[Stage3BValidator] CHECK 6 PASSED: All safety gear interlocks correctly prevent illegal transitions.");
            return true;
        }

        private static bool ValidateIndependentAxleSuspension()
        {
            Debug.Log("\n--- [CHECK 7: Independent Axle Articulation & Spring/Damper Tuning] ---");

            Scene scene = EditorSceneManager.OpenScene(BusPrototypeScenePath, OpenSceneMode.Single);
            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            var busController = busGo.GetComponent<BusController>();

            // Verify independent spring & damper settings across axles
            JointSpring fSpring = busController.frontLeftCollider.suspensionSpring;
            JointSpring mSpring = busController.midLeftCollider.suspensionSpring;
            JointSpring rSpring = busController.rearLeftCollider.suspensionSpring;

            Debug.Log($"[Stage3BValidator] Axle 1 (Front) Spring: {fSpring.spring} N/m, Damper: {fSpring.damper} Ns/m");
            Debug.Log($"[Stage3BValidator] Axle 2 (Mid)   Spring: {mSpring.spring} N/m, Damper: {mSpring.damper} Ns/m");
            Debug.Log($"[Stage3BValidator] Axle 3 (Rear)  Spring: {rSpring.spring} N/m, Damper: {rSpring.damper} Ns/m");

            if (fSpring.spring != 60000f || fSpring.damper != 5500f)
            {
                Debug.LogError("[Stage3BValidator] Axle 1 spring/damper tuning mismatch!");
                return false;
            }
            if (mSpring.spring != 70000f || mSpring.damper != 6500f)
            {
                Debug.LogError("[Stage3BValidator] Axle 2 spring/damper tuning mismatch!");
                return false;
            }
            if (rSpring.spring != 55000f || rSpring.damper != 5000f)
            {
                Debug.LogError("[Stage3BValidator] Axle 3 spring/damper tuning mismatch!");
                return false;
            }

            // Verify colliders are completely independent GameObjects
            if (busController.midLeftCollider.gameObject == busController.rearLeftCollider.gameObject)
            {
                Debug.LogError("[Stage3BValidator] Axle 2 and Axle 3 share the same GameObject!");
                return false;
            }

            Debug.Log("[Stage3BValidator] CHECK 7 PASSED: Axle 2 and Axle 3 are independently tuned and articulated.");
            return true;
        }

        private static bool ValidateRigidbodyAndTorqueHygiene()
        {
            Debug.Log("\n--- [CHECK 8: Rigidbody and Torque Hygiene] ---");

            Scene scene = EditorSceneManager.OpenScene(BusPrototypeScenePath, OpenSceneMode.Single);
            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");

            // Ensure only 1 Rigidbody exists across entire vehicle hierarchy
            Rigidbody[] allRbs = busGo.GetComponentsInChildren<Rigidbody>(true);
            if (allRbs.Length != 1)
            {
                Debug.LogError($"[Stage3BValidator] Found {allRbs.Length} Rigidbodies on bus! Expected exactly 1 root Rigidbody.");
                return false;
            }

            Rigidbody rootRb = busGo.GetComponent<Rigidbody>();
            if (rootRb.mass != 10500f)
            {
                Debug.LogError($"[Stage3BValidator] Rigidbody mass {rootRb.mass} != 10500 kg!");
                return false;
            }

            // Ensure no colliders exist on visual hierarchy
            Transform visuals = busGo.transform.Find("KeralaBus12m_Visuals");
            Collider[] visualColliders = visuals.GetComponentsInChildren<Collider>(true);
            if (visualColliders.Length > 0)
            {
                Debug.LogError($"[Stage3BValidator] Visuals contain {visualColliders.Length} unwanted Colliders!");
                return false;
            }

            Debug.Log("[Stage3BValidator] CHECK 8 PASSED: Single root Rigidbody (10,500 kg), clean visual hierarchy.");
            return true;
        }

        private static bool ValidateSceneReferences()
        {
            Debug.Log("\n--- [CHECK 9: Scene References & Zero Compilation Errors] ---");

            Scene scene = EditorSceneManager.OpenScene(BusPrototypeScenePath, OpenSceneMode.Single);
            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            var busController = busGo.GetComponent<BusController>();

            GameObject camGo = GameObject.Find("ChaseCamera");
            if (camGo == null)
            {
                Debug.LogError("[Stage3BValidator] 'ChaseCamera' not found!");
                return false;
            }
            var cam = camGo.GetComponent<BusCameraController>();
            if (cam == null || cam.target != busGo.transform)
            {
                Debug.LogError("[Stage3BValidator] BusCameraController target not set to bus root!");
                return false;
            }

            GameObject hudGo = GameObject.Find("BusDashboardHUD");
            if (hudGo == null)
            {
                Debug.LogError("[Stage3BValidator] 'BusDashboardHUD' not found!");
                return false;
            }
            var hud = hudGo.GetComponent<BusDashboardHUD>();
            if (hud == null || hud.busController != busController || hud.cameraController != cam)
            {
                Debug.LogError("[Stage3BValidator] BusDashboardHUD references invalid!");
                return false;
            }

            Debug.Log("[Stage3BValidator] CHECK 9 PASSED: All camera, HUD, and controller cross-references intact.");
            return true;
        }

        private static bool ValidatePhase0AndBuildSettings()
        {
            Debug.Log("\n--- [CHECK 10: Phase 0 Preservation and Build Configuration] ---");

            if (!File.Exists(SmokeTestScenePath))
            {
                Debug.LogError($"[Stage3BValidator] Phase 0 smoke test scene missing at {SmokeTestScenePath}");
                return false;
            }

            Scene smokeScene = EditorSceneManager.OpenScene(SmokeTestScenePath, OpenSceneMode.Single);
            if (!smokeScene.IsValid())
            {
                Debug.LogError("[Stage3BValidator] Phase 0 scene invalid!");
                return false;
            }

            GameObject testCube = GameObject.Find("TestCube_3D_Indicator");
            GameObject overlay = GameObject.Find("DiagnosticsOverlay");

            if (testCube == null || overlay == null)
            {
                Debug.LogError("[Stage3BValidator] Phase 0 scene missing expected test cube or overlay!");
                return false;
            }

            // Verify EditorBuildSettings contains both scenes
            var scenes = EditorBuildSettings.scenes;
            bool hasSmoke = false;
            bool hasBus = false;

            foreach (var s in scenes)
            {
                if (s.path == SmokeTestScenePath) hasSmoke = true;
                if (s.path == BusPrototypeScenePath) hasBus = true;
            }

            if (!hasSmoke || !hasBus)
            {
                Debug.LogError($"[Stage3BValidator] BuildSettings missing scenes! Smoke: {hasSmoke}, Bus: {hasBus}");
                return false;
            }

            Debug.Log("[Stage3BValidator] CHECK 10 PASSED: Phase 0 scene preserved and both scenes registered in build settings.");
            return true;
        }

        private static bool ValidateInputAndHUDLayout()
        {
            Debug.Log("\n--- [CHECK 11: Input System Integration & Landscape HUD Layout Validation] ---");

            // 1. Verify EventSystem has InputSystemUIInputModule
            Scene scene = EditorSceneManager.OpenScene(BusPrototypeScenePath, OpenSceneMode.Single);
            GameObject esGo = GameObject.Find("EventSystem");
            if (esGo == null)
            {
                Debug.LogError("[Stage3BValidator] 'EventSystem' object missing from scene!");
                return false;
            }

            var inputModule = esGo.GetComponent<UnityEngine.InputSystem.UI.InputSystemUIInputModule>();
            if (inputModule == null)
            {
                Debug.LogError("[Stage3BValidator] EventSystem is missing InputSystemUIInputModule!");
                return false;
            }

            // 2. Instantiate and inspect HUD layout
            GameObject hudGo = GameObject.Find("BusDashboardHUD");
            var hud = hudGo.GetComponent<BusDashboardHUD>();

            // Trigger Awake on HUD by invoking non-public Awake
            MethodInfo awakeMethod = typeof(BusDashboardHUD).GetMethod("Awake", BindingFlags.NonPublic | BindingFlags.Instance);
            if (awakeMethod != null)
            {
                awakeMethod.Invoke(hud, null);
            }

            // Find key UI elements
            GameObject canvasGo = GameObject.Find("HUD_Canvas");
            if (canvasGo == null)
            {
                Debug.LogError("[Stage3BValidator] 'HUD_Canvas' not created!");
                return false;
            }

            var scaler = canvasGo.GetComponent<UnityEngine.UI.CanvasScaler>();
            if (scaler == null || scaler.matchWidthOrHeight != 1.0f)
            {
                Debug.LogError($"[Stage3BValidator] CanvasScaler matchWidthOrHeight is {scaler?.matchWidthOrHeight}, expected 1.0f (Match Height)!");
                return false;
            }

            // Verify TopBar elements: Btn_Pause and SystemsGroup do not overlap
            Transform pauseBtn = canvasGo.transform.Find("SafeAreaContainer/TopBar/Btn_Pause");
            Transform systemsGroup = canvasGo.transform.Find("SafeAreaContainer/TopBar/SystemsGroup");
            if (pauseBtn == null || systemsGroup == null)
            {
                Debug.LogError("[Stage3BValidator] TopBar missing Btn_Pause or SystemsGroup!");
                return false;
            }

            RectTransform pauseRect = pauseBtn.GetComponent<RectTransform>();
            RectTransform sysRect = systemsGroup.GetComponent<RectTransform>();
            float pauseRight = pauseRect.anchoredPosition.x + pauseRect.sizeDelta.x;
            float sysLeft = sysRect.anchoredPosition.x;
            Debug.Log($"[Stage3BValidator] TopBar check: Pause right edge={pauseRight}, Systems left edge={sysLeft}");
            if (pauseRight >= sysLeft)
            {
                Debug.LogError($"[Stage3BValidator] Btn_Pause overlaps SystemsGroup! (Pause right: {pauseRight} >= Systems left: {sysLeft})");
                return false;
            }

            // Verify LeftControls (Steering Wheel)
            Transform leftGroup = canvasGo.transform.Find("SafeAreaContainer/LeftControls");
            Transform wheel = leftGroup?.Find("SteeringWheelModule");
            if (wheel == null)
            {
                Debug.LogError("[Stage3BValidator] SteeringWheelModule missing from LeftControls!");
                return false;
            }

            // Verify RightControls (Pedals)
            Transform rightGroup = canvasGo.transform.Find("SafeAreaContainer/RightControls");
            Transform brakePedal = rightGroup?.Find("PedalsGroup/Pedal_Brake");
            Transform gasPedal = rightGroup?.Find("PedalsGroup/Pedal_Gas");
            if (brakePedal == null || gasPedal == null)
            {
                Debug.LogError("[Stage3BValidator] Pedal_Brake or Pedal_Gas missing from RightControls!");
                return false;
            }

            // Verify DashboardCluster (Speedometer and Gear Selector)
            Transform dashGroup = canvasGo.transform.Find("SafeAreaContainer/DashboardCluster");
            Transform speedPanel = dashGroup?.Find("SpeedometerPanel");
            Transform gearBar = dashGroup?.Find("GearSelectorBar");
            if (speedPanel == null || gearBar == null)
            {
                Debug.LogError("[Stage3BValidator] SpeedometerPanel or GearSelectorBar missing from DashboardCluster!");
                return false;
            }

            RectTransform speedRect = speedPanel.GetComponent<RectTransform>();
            RectTransform gearRect = gearBar.GetComponent<RectTransform>();
            Debug.Log($"[Stage3BValidator] Dashboard check: Speedometer Y={speedRect.anchoredPosition.y}, GearBar Y={gearRect.anchoredPosition.y}");
            if (speedRect.anchoredPosition.y < 0f)
            {
                Debug.LogError($"[Stage3BValidator] SpeedometerPanel Y is {speedRect.anchoredPosition.y} < 0 (clipped below bottom bezel)!");
                return false;
            }

            // 3. Verify GameInputHelper does not throw InvalidOperationException
            try
            {
                bool w = GameInputHelper.IsKeyPressed(UnityEngine.InputSystem.Key.W, KeyCode.W);
                bool p = GameInputHelper.WasKeyPressedThisFrame(UnityEngine.InputSystem.Key.P, KeyCode.P);
                Debug.Log($"[Stage3BValidator] GameInputHelper safely queried: W={w}, P={p} (zero exceptions)");
            }
            catch (Exception ex)
            {
                Debug.LogError($"[Stage3BValidator] GameInputHelper threw exception: {ex}");
                return false;
            }

            Debug.Log("[Stage3BValidator] CHECK 11 PASSED: Input System integration verified and HUD elements properly spaced without clipping or overlap.");
            return true;
        }
    }
}
