using System;
using UnityEngine;
using UnityEditor;
using UnityEditor.SceneManagement;
using BharatBusSim.Vehicles;
using BharatBusSim.UI;

namespace BharatBusSim.Editor
{
    public static class CameraSystemTests
    {
        [MenuItem("BharatBusSim/Tests/Run Camera System Tests")]
        public static void RunAllTests()
        {
            Debug.Log("\n=======================================================");
            Debug.Log("[CameraSystemTests] STARTING 3-CAMERA SYSTEM TESTS");
            Debug.Log("=======================================================");

            bool allPassed = true;

            allPassed &= TestPresetsStructure();
            allPassed &= TestCyclingSequence();
            allPassed &= TestOrbitControls();
            allPassed &= TestHUDIntegration();
            allPassed &= TestCam2CenterAlignment();
            allPassed &= TestCam1RealisticCockpit();
            allPassed &= TestTargetAutoRecovery();
            allPassed &= TestCockpitVisibilityAcrossModes();

            if (allPassed)
            {
                Debug.Log("\n[CameraSystemTests] ALL 3-CAMERA SYSTEM TESTS PASSED SUCCESSFULLY!");
            }
            else
            {
                Debug.LogError("\n[CameraSystemTests] ONE OR MORE CAMERA TESTS FAILED!");
                throw new Exception("Camera system test suite failed!");
            }
        }

        private static bool TestPresetsStructure()
        {
            Debug.Log("[CameraSystemTests] Test 1: Validating 3 Camera Presets...");

            var go = new GameObject("TestCameraGo");
            var cam = go.AddComponent<Camera>();
            var ctrl = go.AddComponent<BusCameraController>();

            var busGo = new GameObject("TestBus");
            ctrl.target = busGo.transform;
            ctrl.SnapToTarget();

            if (ctrl.TotalPresets != 3)
            {
                Debug.LogError($"[CameraSystemTests] Expected exactly 3 presets, but got {ctrl.TotalPresets}");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            // Verify Preset 0: Static Rear Chase
            ctrl.SetCameraPreset(0);
            if (ctrl.CurrentMode != BusCameraController.CameraMode.RearChase)
            {
                Debug.LogError($"[CameraSystemTests] Preset 0 mode expected RearChase, got {ctrl.CurrentMode}");
                return false;
            }

            // Verify Preset 1: Driver Cockpit
            ctrl.SetCameraPreset(1);
            if (ctrl.CurrentMode != BusCameraController.CameraMode.DriverCockpit)
            {
                Debug.LogError($"[CameraSystemTests] Preset 1 mode expected DriverCockpit, got {ctrl.CurrentMode}");
                return false;
            }

            // Verify Preset 2: 360 Orbit
            ctrl.SetCameraPreset(2);
            if (ctrl.CurrentMode != BusCameraController.CameraMode.Orbit360)
            {
                Debug.LogError($"[CameraSystemTests] Preset 2 mode expected Orbit360, got {ctrl.CurrentMode}");
                return false;
            }

            UnityEngine.Object.DestroyImmediate(go);
            UnityEngine.Object.DestroyImmediate(busGo);
            Debug.Log("[CameraSystemTests] Test 1 PASSED: Exactly 3 presets configured correctly.");
            return true;
        }

        private static bool TestCyclingSequence()
        {
            Debug.Log("[CameraSystemTests] Test 2: Validating Cycling Sequence (1 -> 2 -> 3 -> 1)...");

            var go = new GameObject("TestCameraGo");
            var cam = go.AddComponent<Camera>();
            var ctrl = go.AddComponent<BusCameraController>();
            var busGo = new GameObject("TestBus");
            ctrl.target = busGo.transform;
            ctrl.SnapToTarget();

            ctrl.SetCameraPreset(0);
            if (ctrl.CurrentPresetIndex != 0) { Debug.LogError("Failed to start at preset 0"); return false; }

            ctrl.CycleCamera();
            if (ctrl.CurrentPresetIndex != 1) { Debug.LogError($"After 1st cycle, expected index 1, got {ctrl.CurrentPresetIndex}"); return false; }

            ctrl.CycleCamera();
            if (ctrl.CurrentPresetIndex != 2) { Debug.LogError($"After 2nd cycle, expected index 2, got {ctrl.CurrentPresetIndex}"); return false; }

            ctrl.CycleCamera();
            if (ctrl.CurrentPresetIndex != 0) { Debug.LogError($"After 3rd cycle, expected wrap to index 0, got {ctrl.CurrentPresetIndex}"); return false; }

            UnityEngine.Object.DestroyImmediate(go);
            UnityEngine.Object.DestroyImmediate(busGo);
            Debug.Log("[CameraSystemTests] Test 2 PASSED: Cycle wraps cleanly 0 -> 1 -> 2 -> 0.");
            return true;
        }

        private static bool TestOrbitControls()
        {
            Debug.Log("[CameraSystemTests] Test 3: Validating 360 Orbit Touch Drag...");

            var go = new GameObject("TestCameraGo");
            var cam = go.AddComponent<Camera>();
            var ctrl = go.AddComponent<BusCameraController>();
            var busGo = new GameObject("TestBus");
            ctrl.target = busGo.transform;

            ctrl.SetCameraPreset(2); // Orbit mode
            ctrl.SnapToTarget();

            Vector3 initialPos = go.transform.position;
            ctrl.OnOrbitDrag(new Vector2(100f, 20f));

            // Run LateUpdate simulation
            var dynMethod = typeof(BusCameraController).GetMethod("LateUpdate", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);
            if (dynMethod != null)
            {
                for (int i = 0; i < 10; i++)
                {
                    dynMethod.Invoke(ctrl, null);
                }
            }

            Vector3 newPos = go.transform.position;
            float posDelta = Vector3.Distance(initialPos, newPos);
            if (posDelta < 0.1f)
            {
                Debug.LogError($"[CameraSystemTests] Camera did not orbit! Initial={initialPos}, New={newPos}, Delta={posDelta}");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            UnityEngine.Object.DestroyImmediate(go);
            UnityEngine.Object.DestroyImmediate(busGo);
            Debug.Log($"[CameraSystemTests] Test 3 PASSED: Orbit drag delta verified (Camera moved {posDelta:F2}m around bus).");
            return true;
        }

        private static bool TestHUDIntegration()
        {
            Debug.Log("[CameraSystemTests] Test 4: Validating HUD Camera Integration...");

            // Open Thrissur Master Scene and check scene references
            string scenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
            var scene = EditorSceneManager.OpenScene(scenePath, OpenSceneMode.Single);

            // Verify only ChaseCamera exists in the scene
            var allCams = UnityEngine.Object.FindObjectsByType<Camera>(FindObjectsSortMode.None);
            if (allCams.Length != 1)
            {
                Debug.LogError($"Found {allCams.Length} cameras in Thrissur scene! Exactly 1 (ChaseCamera) must exist.");
                foreach (var c in allCams) Debug.LogError("  Extraneous Camera: " + c.gameObject.name);
                return false;
            }

            var camGo = GameObject.Find("ChaseCamera");
            if (camGo == null) { Debug.LogError("ChaseCamera missing from Thrissur scene!"); return false; }
            var ctrl = camGo.GetComponent<BusCameraController>();
            if (ctrl == null) { Debug.LogError("BusCameraController missing from ChaseCamera!"); return false; }

            var hudGo = GameObject.Find("BusDashboardHUD");
            if (hudGo == null) { Debug.LogError("BusDashboardHUD missing from Thrissur scene!"); return false; }
            var hud = hudGo.GetComponent<BusDashboardHUD>();
            if (hud == null) { Debug.LogError("BusDashboardHUD component missing!"); return false; }

            if (hud.cameraController != ctrl)
            {
                Debug.LogError("HUD cameraController reference does not match ChaseCamera!");
                return false;
            }

            // Verify orbit zone exists under SafeAreaContainer
            var orbitZone = hudGo.GetComponentInChildren<UICameraOrbitZone>(true);
            if (orbitZone == null)
            {
                // In scene at edit time, HUD generates procedural UI at runtime Awake().
                // Let's instantiate HUD in test to verify runtime hierarchy:
                var testHudGo = new GameObject("TestHUD");
                var testHud = testHudGo.AddComponent<BusDashboardHUD>();
                testHud.cameraController = ctrl;

                var awakeMethod = typeof(BusDashboardHUD).GetMethod("Awake", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);
                if (awakeMethod != null) awakeMethod.Invoke(testHud, null);

                var runtimeOrbitZone = UnityEngine.Object.FindFirstObjectByType<UICameraOrbitZone>();
                if (runtimeOrbitZone == null)
                {
                    Debug.LogError("UICameraOrbitZone was not created inside HUD hierarchy!");
                    UnityEngine.Object.DestroyImmediate(testHudGo);
                    return false;
                }
                var canvasGo = GameObject.Find("HUD_Canvas");
                if (canvasGo != null) UnityEngine.Object.DestroyImmediate(canvasGo);
                UnityEngine.Object.DestroyImmediate(testHudGo);
            }

            Debug.Log("[CameraSystemTests] Test 4 PASSED: HUD, Camera Controller, and UICameraOrbitZone verified.");
            return true;
        }

        private static bool TestCam2CenterAlignment()
        {
            Debug.Log("[CameraSystemTests] Test 5: Validating Cam 2 (Rear Chase) Center Alignment & Stability...");

            var go = new GameObject("TestCam2Go");
            var cam = go.AddComponent<Camera>();
            var ctrl = go.AddComponent<BusCameraController>();
            var busGo = new GameObject("TestBusAngled");

            // Position bus at an angle (65 degrees yaw), as when parked in Thrissur terminal bay
            busGo.transform.position = new Vector3(100f, 0f, 100f);
            busGo.transform.rotation = Quaternion.Euler(0f, 65f, 0f);

            ctrl.target = busGo.transform;
            ctrl.SetCameraPreset(0); // Cam 1: Static Rear Chase
            ctrl.SnapToTarget();

            // Initial check: local position relative to bus
            Vector3 initialLocalPos = busGo.transform.InverseTransformPoint(go.transform.position);
            Debug.Log($"[CameraSystemTests] Initial Cam 1 Local Offset: X={initialLocalPos.x:F3}, Y={initialLocalPos.y:F3}, Z={initialLocalPos.z:F3}");

            if (Mathf.Abs(initialLocalPos.x) > 0.05f)
            {
                Debug.LogError($"[CameraSystemTests] Cam 1 is not centered! Lateral offset X={initialLocalPos.x:F3}m (expected ~0)");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            if (initialLocalPos.z >= 0f)
            {
                Debug.LogError($"[CameraSystemTests] Cam 1 is not behind bus! Local Z={initialLocalPos.z:F3}m (expected negative)");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            // Simulate 30 frames of LateUpdate to ensure no oscillation, drift or vibration occurs
            var lateUpdateMethod = typeof(BusCameraController).GetMethod("LateUpdate", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);
            Vector3 lastPos = go.transform.position;
            float maxOscillation = 0f;

            for (int i = 0; i < 30; i++)
            {
                if (lateUpdateMethod != null) lateUpdateMethod.Invoke(ctrl, null);
                float stepDiff = Vector3.Distance(go.transform.position, lastPos);
                if (i > 10 && stepDiff > maxOscillation)
                {
                    maxOscillation = stepDiff;
                }
                lastPos = go.transform.position;
            }

            Vector3 finalLocalPos = busGo.transform.InverseTransformPoint(go.transform.position);
            Debug.Log($"[CameraSystemTests] Converged Cam 1 Local Offset: X={finalLocalPos.x:F3}, Y={finalLocalPos.y:F3}, Z={finalLocalPos.z:F3}, MaxOscillation={maxOscillation:F5}");

            if (Mathf.Abs(finalLocalPos.x) > 0.05f)
            {
                Debug.LogError($"[CameraSystemTests] Cam 1 drifted off-center after updates! X={finalLocalPos.x:F3}m");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            if (Mathf.Abs(finalLocalPos.y - 3.8f) > 0.25f)
            {
                Debug.LogError($"[CameraSystemTests] Cam 1 height expected ~3.8m, got {finalLocalPos.y:F3}m");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            if (maxOscillation > 0.001f)
            {
                Debug.LogError($"[CameraSystemTests] Cam 2 is oscillating/vibrating! MaxOscillation={maxOscillation:F5}m");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            UnityEngine.Object.DestroyImmediate(go);
            UnityEngine.Object.DestroyImmediate(busGo);
            Debug.Log("[CameraSystemTests] Test 5 PASSED: Cam 2 is perfectly centered on bus centerline and rock-solid stable.");
            return true;
        }

        private static bool TestCam1RealisticCockpit()
        {
            Debug.Log("[CameraSystemTests] Test 6: Validating Cam 1 (Driver Cockpit) Realistic Cabin Alignment & Stability...");

            var go = new GameObject("TestCockpitCamGo");
            var cam = go.AddComponent<Camera>();
            var ctrl = go.AddComponent<BusCameraController>();
            var busGo = new GameObject("TestBus");

            busGo.transform.position = new Vector3(0f, 0f, 0f);
            busGo.transform.rotation = Quaternion.identity;

            ctrl.target = busGo.transform;
            ctrl.SetCameraPreset(1); // Cam 2: Driver Cockpit
            ctrl.SnapToTarget();

            Vector3 localPos = busGo.transform.InverseTransformPoint(go.transform.position);
            Debug.Log($"[CameraSystemTests] Cam 1 Eye Local Offset: X={localPos.x:F3}, Y={localPos.y:F3}, Z={localPos.z:F3}");

            // Validate RHD Driver Position: X ~ 0.70m, Y ~ 1.62m, Z ~ 4.65m
            if (Mathf.Abs(localPos.x - 0.70f) > 0.05f)
            {
                Debug.LogError($"[CameraSystemTests] Cam 1 X expected ~0.70m (RHD driver side), got {localPos.x:F3}m");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            if (Mathf.Abs(localPos.y - 2.35f) > 0.05f)
            {
                Debug.LogError($"[CameraSystemTests] Cam 1 Y expected ~2.35f (driver eye level), got {localPos.y:F3}m");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            if (Mathf.Abs(localPos.z - 4.65f) > 0.10f)
            {
                Debug.LogError($"[CameraSystemTests] Cam 1 Z expected ~4.65m (driver seat position), got {localPos.z:F3}m");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            // Validate Downward Pitch Angle (Expected around 6.5 degrees down)
            float eulerX = go.transform.eulerAngles.x;
            if (eulerX > 180f) eulerX -= 360f;
            Debug.Log($"[CameraSystemTests] Cam 1 Downward Pitch: {eulerX:F2} degrees");

            if (eulerX < 3f || eulerX > 15f)
            {
                Debug.LogError($"[CameraSystemTests] Cam 1 downward pitch angle expected between 3 and 15 degrees down, got {eulerX:F2}");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            // Validate Cam 1 Free Look-Around towards both doors (Driver Door right, Passenger Door left)
            ctrl.ResetCockpitView();
            ctrl.SetCockpitLook(0f, 0f);

            var lateUpdateMethod = typeof(BusCameraController).GetMethod("LateUpdate", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);
            if (lateUpdateMethod != null) lateUpdateMethod.Invoke(ctrl, null);

            // Drag Right -> Turn Head towards Driver Door (+ Yaw)
            ctrl.OnCockpitLookDrag(new Vector2(300f, 0f));
            if (ctrl.CockpitLookYaw <= 10f)
            {
                Debug.LogError($"[CameraSystemTests] Expected positive yaw looking right at driver door, got {ctrl.CockpitLookYaw:F1}");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            // Simulate frames to verify smooth convergence
            for (int i = 0; i < 20; i++)
            {
                if (lateUpdateMethod != null) lateUpdateMethod.Invoke(ctrl, null);
            }
            if (ctrl.CurrentCockpitYaw <= 5f)
            {
                Debug.LogError($"[CameraSystemTests] CurrentCockpitYaw did not converge towards driver door: {ctrl.CurrentCockpitYaw:F1}");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            // Drag Left -> Turn Head towards Passenger Entry Door (- Yaw)
            ctrl.OnCockpitLookDrag(new Vector2(-700f, 0f));
            if (ctrl.CockpitLookYaw >= -10f)
            {
                Debug.LogError($"[CameraSystemTests] Expected negative yaw looking left at passenger door, got {ctrl.CockpitLookYaw:F1}");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            for (int i = 0; i < 20; i++)
            {
                if (lateUpdateMethod != null) lateUpdateMethod.Invoke(ctrl, null);
            }
            if (ctrl.CurrentCockpitYaw >= -5f)
            {
                Debug.LogError($"[CameraSystemTests] CurrentCockpitYaw did not converge towards passenger door: {ctrl.CurrentCockpitYaw:F1}");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            // Test ResetCockpitView() recenters view forward
            ctrl.ResetCockpitView();
            if (Mathf.Abs(ctrl.CockpitLookYaw) > 0.001f || Mathf.Abs(ctrl.CockpitLookPitch) > 0.001f)
            {
                Debug.LogError($"[CameraSystemTests] ResetCockpitView failed to reset target yaw/pitch to 0!");
                UnityEngine.Object.DestroyImmediate(go);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            for (int i = 0; i < 30; i++)
            {
                if (lateUpdateMethod != null) lateUpdateMethod.Invoke(ctrl, null);
            }

            UnityEngine.Object.DestroyImmediate(go);
            UnityEngine.Object.DestroyImmediate(busGo);
            Debug.Log("[CameraSystemTests] Test 6 PASSED: Cam 1 realistic RHD cockpit framing and smooth left/right door look-around verified.");
            return true;
        }

        private static bool TestTargetAutoRecovery()
        {
            Debug.Log("[CameraSystemTests] Test 7: Validating Target Auto-Recovery when target is null...");

            var busGo = new GameObject("Kerala_Bus_Prototype");
            busGo.tag = "Player";
            var busCtrl = busGo.AddComponent<BusController>();

            var camGo = new GameObject("TestCamAutoTargetGo");
            var cam = camGo.AddComponent<Camera>();
            var ctrl = camGo.AddComponent<BusCameraController>();

            // Explicitly set target to null
            ctrl.target = null;
            ctrl.EnsureTarget();

            if (ctrl.target != busGo.transform)
            {
                Debug.LogError($"[CameraSystemTests] Target auto-recovery failed! Expected {busGo.transform}, got {ctrl.target}");
                UnityEngine.Object.DestroyImmediate(camGo);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            ctrl.SnapToTarget();
            if (Vector3.Distance(camGo.transform.position, Vector3.zero) < 0.01f)
            {
                Debug.LogError("[CameraSystemTests] Camera did not move to target after auto-recovery!");
                UnityEngine.Object.DestroyImmediate(camGo);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            UnityEngine.Object.DestroyImmediate(camGo);
            UnityEngine.Object.DestroyImmediate(busGo);
            Debug.Log("[CameraSystemTests] Test 7 PASSED: Target auto-recovery successfully bound camera to bus.");
            return true;
        }

        private static bool TestCockpitVisibilityAcrossModes()
        {
            Debug.Log("[CameraSystemTests] Test 8: Validating Dynamic Cockpit Visibility Across Camera Modes...");

            var busGo = new GameObject("Kerala_Bus_Prototype");
            var visualsGo = new GameObject("KeralaBus12m_Visuals");
            visualsGo.transform.SetParent(busGo.transform, false);

            var cockpitGo = new GameObject("RHD_DriverCockpit");
            cockpitGo.transform.SetParent(visualsGo.transform, false);

            var dasborGo = new GameObject("dasbor");
            dasborGo.transform.SetParent(visualsGo.transform, false);

            var camGo = new GameObject("ChaseCamera");
            var cam = camGo.AddComponent<Camera>();
            var ctrl = camGo.AddComponent<BusCameraController>();
            ctrl.target = busGo.transform;

            // Mode 0: Cam 1 (Static Rear Chase) -> Realistic Cockpit must be INACTIVE (Zero exterior overlay!), legacy interior ACTIVE
            ctrl.SetCameraPreset(0);
            ctrl.SnapToTarget();

            if (cockpitGo.activeSelf)
            {
                Debug.LogError("[CameraSystemTests] RHD_DriverCockpit should be INACTIVE in Cam 1 (Static Rear Chase)!");
                UnityEngine.Object.DestroyImmediate(camGo);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }
            if (!dasborGo.activeSelf)
            {
                Debug.LogError("[CameraSystemTests] Legacy dasbor should be ACTIVE in Cam 1 (Static Rear Chase)!");
                UnityEngine.Object.DestroyImmediate(camGo);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            // Mode 1: Cam 2 (Driver Cockpit) -> Realistic Cockpit must be ACTIVE, legacy interior INACTIVE
            ctrl.SetCameraPreset(1);
            ctrl.SnapToTarget();

            if (!cockpitGo.activeSelf)
            {
                Debug.LogError("[CameraSystemTests] RHD_DriverCockpit should be ACTIVE in Cam 2 (Driver Cockpit)!");
                UnityEngine.Object.DestroyImmediate(camGo);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }
            if (dasborGo.activeSelf)
            {
                Debug.LogError("[CameraSystemTests] Legacy dasbor should be INACTIVE in Cam 2 (Driver Cockpit)!");
                UnityEngine.Object.DestroyImmediate(camGo);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            // Mode 2: Cam 3 (360 Orbit) -> Realistic Cockpit must be INACTIVE (Zero exterior overlay!), legacy interior ACTIVE
            ctrl.SetCameraPreset(2);
            ctrl.SnapToTarget();

            if (cockpitGo.activeSelf)
            {
                Debug.LogError("[CameraSystemTests] RHD_DriverCockpit should be INACTIVE in Cam 3 (360 Orbit)!");
                UnityEngine.Object.DestroyImmediate(camGo);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }
            if (!dasborGo.activeSelf)
            {
                Debug.LogError("[CameraSystemTests] Legacy dasbor should be ACTIVE in Cam 3 (360 Orbit)!");
                UnityEngine.Object.DestroyImmediate(camGo);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            // Cycle Camera: Cam 3 (Orbit) -> Cam 1 (Static Rear Chase) -> Cam 2 (Cockpit) -> Cockpit should become ACTIVE
            ctrl.CycleCamera(); // 2 -> 0
            ctrl.CycleCamera(); // 0 -> 1
            if (ctrl.CurrentMode != BusCameraController.CameraMode.DriverCockpit || !cockpitGo.activeSelf)
            {
                Debug.LogError($"[CameraSystemTests] After cycling to Cam 2, Cockpit active={cockpitGo.activeSelf}, mode={ctrl.CurrentMode}");
                UnityEngine.Object.DestroyImmediate(camGo);
                UnityEngine.Object.DestroyImmediate(busGo);
                return false;
            }

            UnityEngine.Object.DestroyImmediate(camGo);
            UnityEngine.Object.DestroyImmediate(busGo);
            Debug.Log("[CameraSystemTests] Test 8 PASSED: Cockpit visibility toggles dynamically with camera modes (Cam 1 only; clean exterior in Cam 2 and 3).");
            return true;
        }
    }
}
