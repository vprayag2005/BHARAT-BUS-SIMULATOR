using System;
using System.IO;
using System.Collections.Generic;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using BharatBusSim.Vehicles;

namespace BharatBusSim.Editor
{
    /// <summary>
    /// Automated verification suite for the Mannuthy-Ollur RoadKit corridor:
    /// executes physics simulation of the 12m Kerala bus prototype traversing
    /// the newly assembled modular road tiles and captures verification screenshots.
    /// </summary>
    public static class MannuthyOllurDriveTest
    {
        public const string ScenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
        public const string ArtifactsDir = "C:/Project/BHARAT BUS SIMULATOR/artifacts/roadkit_shots";

        public static void RunFromCommandLine()
        {
            try
            {
                bool passed = RunTest();
                if (passed)
                {
                    Debug.Log("[MannuthyOllurDriveTest] ALL ROADKIT CORRIDOR TESTS PASSED!");
                    EditorApplication.Exit(0);
                }
                else
                {
                    Debug.LogError("[MannuthyOllurDriveTest] DRIVE TEST FAILED!");
                    EditorApplication.Exit(1);
                }
            }
            catch (Exception ex)
            {
                Debug.LogError($"[MannuthyOllurDriveTest] FATAL EXCEPTION: {ex.Message}\n{ex.StackTrace}");
                EditorApplication.Exit(1);
            }
        }

        [MenuItem("BharatBusSim/RoadKit/4. Run Corridor Drive Test")]
        public static bool RunTest()
        {
            Debug.Log("==================================================================");
            Debug.Log("[MannuthyOllurDriveTest] STARTING AUTONOMOUS BUS DRIVABILITY TEST");
            Debug.Log("==================================================================");

            if (!Directory.Exists(ArtifactsDir))
            {
                Directory.CreateDirectory(ArtifactsDir);
            }

            var scene = EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
            if (!scene.IsValid())
            {
                Debug.LogError($"[DriveTest] Failed to open scene at {ScenePath}");
                return false;
            }

            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            float highwayYaw = 140.7f;
            Vector3 forward = new Vector3(Mathf.Sin(highwayYaw * Mathf.Deg2Rad), 0f, Mathf.Cos(highwayYaw * Mathf.Deg2Rad)).normalized;
            Vector3 right = new Vector3(Mathf.Cos(highwayYaw * Mathf.Deg2Rad), 0f, -Mathf.Sin(highwayYaw * Mathf.Deg2Rad)).normalized;

            // Spawn bus at Mannuthy start of 4-lane highway, on left driving lane (offset -3.6m from median)
            Vector3 spawnPos = MannuthyOllurRoadKitBuilder.MannuthyJunctionAnchor + forward * 28.0f - right * 3.6f;
            spawnPos.y = 1.65f; // Raised to settle gently onto road surface
            Quaternion spawnRot = Quaternion.Euler(0f, highwayYaw, 0f);

            if (busGo == null)
            {
                Debug.Log("[DriveTest] Spawning Kerala_Bus_Prototype for corridor test...");
                busGo = HinoBusFactory.CreateHinoBus(spawnPos, spawnRot, initializeInPark: false);
            }
            else
            {
                busGo.transform.position = spawnPos;
                busGo.transform.rotation = spawnRot;
                var rb = busGo.GetComponent<Rigidbody>();
                if (rb != null)
                {
#if UNITY_6000_0_OR_NEWER
                    rb.linearVelocity = Vector3.zero;
                    rb.angularVelocity = Vector3.zero;
#else
                    rb.velocity = Vector3.zero;
                    rb.angularVelocity = Vector3.zero;
#endif
                }
            }

            var busController = busGo.GetComponent<BusController>();
            var busRb = busGo.GetComponent<Rigidbody>();
            var wheels = busGo.GetComponentsInChildren<BusWheel>();
            var colliders = busGo.GetComponentsInChildren<WheelCollider>();

            foreach (var bw in wheels) bw.Initialize();

            bool origSim = Physics.simulationMode != SimulationMode.Script;
            Physics.simulationMode = SimulationMode.Script;

            try
            {
                const float dt = 0.02f;

                // 1. Suspension Settling Phase (40 steps = 0.8s)
                Debug.Log("[DriveTest] Settling suspension on RoadKit asphalt...");
                for (int i = 0; i < 40; i++)
                {
                    busController.StepPhysics();
                    Physics.Simulate(dt);
                }

                // Check wheel contact
                int groundedCount = colliders.Count(c => c.isGrounded);
                Debug.Log($"[DriveTest] Post-settle: {groundedCount}/{colliders.Length} wheels grounded.");
                if (groundedCount < 4)
                {
                    Debug.LogError("[DriveTest] Insufficient wheel contact after settling!");
                    return false;
                }

                // 2. Engage Drive and Apply Throttle
                busController.TrySetGear(TransmissionGear.Drive, out _);
                busController.SetUIThrottle(1.0f);
                busController.SetUIBrake(0.0f);
                busController.SetUIHandbrake(false);

                float maxSpeed = 0f;
                float startDist = Vector3.Distance(busGo.transform.position, MannuthyOllurRoadKitBuilder.OllurGoreAnchor);
                float prevDistToOllur = startDist;
                int totalSteps = 1200; // 24 seconds of physics simulation
                bool reached40Kmh = false;
                int snagCount = 0;

                Debug.Log($"[DriveTest] Starting corridor acceleration run ({totalSteps} steps)...");
                Vector3 lastPos = busGo.transform.position;

                for (int step = 0; step < totalSteps; step++)
                {
                    // Steer along highway corridor heading with subtle lane-keeping
                    Vector3 currentForward = busGo.transform.forward;
                    float currentHeading = Mathf.Atan2(currentForward.x, currentForward.z) * Mathf.Rad2Deg;
                    float headingError = Mathf.DeltaAngle(currentHeading, highwayYaw);
                    float steerInput = Mathf.Clamp(headingError * 0.04f, -0.3f, 0.3f);
                    busController.SetUISteer(steerInput);

                    busController.StepPhysics();
                    Physics.Simulate(dt);

#if UNITY_6000_0_OR_NEWER
                    float speedKmh = busRb.linearVelocity.magnitude * 3.6f;
#else
                    float speedKmh = busRb.velocity.magnitude * 3.6f;
#endif
                    if (speedKmh > maxSpeed) maxSpeed = speedKmh;
                    if (speedKmh >= 40.0f) reached40Kmh = true;

                    // Stall detection: sharp velocity loss while under full throttle
                    float stepDist = Vector3.Distance(busGo.transform.position, lastPos);
                    lastPos = busGo.transform.position;
                    if (step > 150 && speedKmh < 5.0f && stepDist < 0.01f)
                    {
                        snagCount++;
                    }

                    if (step % 200 == 0)
                    {
                        float distToOllur = Vector3.Distance(busGo.transform.position, MannuthyOllurRoadKitBuilder.OllurGoreAnchor);
                        int gCount = colliders.Count(c => c.isGrounded);
                        Debug.Log($"[DriveTest] Step {step,4}: Speed = {speedKmh,5:F1} km/h | DistToOllur = {distToOllur,6:F1} m | Grounded = {gCount}/{colliders.Length} | Pos = {busGo.transform.position:F1}");
                    }
                }

                float finalDistToOllur = Vector3.Distance(busGo.transform.position, MannuthyOllurRoadKitBuilder.OllurGoreAnchor);
                float distanceTraversed = startDist - finalDistToOllur;

                Debug.Log("------------------------------------------------------------------");
                Debug.Log($"[DriveTest] TELEMETRY SUMMARY:");
                Debug.Log($"  Max Speed Attained: {maxSpeed:F1} km/h (Pass Target: >= 40.0 km/h)");
                Debug.Log($"  Distance Traversed: {distanceTraversed:F1} m along NH 544 corridor");
                Debug.Log($"  Final Position:     {busGo.transform.position:F1}");
                Debug.Log($"  Tile Snags/Stalls:  {snagCount}");
                Debug.Log("------------------------------------------------------------------");

                bool pass = reached40Kmh && (snagCount == 0) && (distanceTraversed > 250.0f);
                if (!pass)
                {
                    Debug.LogError($"[DriveTest] Criteria failed: reached40Kmh={reached40Kmh}, snags={snagCount}, dist={distanceTraversed:F1}m");
                    return false;
                }

                // 3. Render Verification Screenshots
                RenderVerificationShots(busGo.transform);

                return true;
            }
            finally
            {
                Physics.simulationMode = origSim ? SimulationMode.FixedUpdate : SimulationMode.Script;
            }
        }

        private static void RenderVerificationShots(Transform busTransform)
        {
            Debug.Log("[DriveTest] Rendering verification screenshots...");

            // Shot 1: Top-Down Plan of Mannuthy 4-Way Cross Junction & RoadKit Highway Start
            Vector3 junc = MannuthyOllurRoadKitBuilder.MannuthyJunctionAnchor;
            Render("01_mannuthy_roadkit_junction_top.png", junc + Vector3.up * 85f, junc, 45f);

            // Shot 2: Oblique View of Mannuthy Junction with RoadKit Markings & Curbs
            Render("02_mannuthy_junction_oblique.png", junc + new Vector3(35f, 22f, 40f), junc, 50f);

            // Shot 3: NH 544 4-Lane Highway Corridor with Median Street Lights
            Vector3 nhMid = Vector3.Lerp(MannuthyOllurRoadKitBuilder.MannuthyJunctionAnchor, MannuthyOllurRoadKitBuilder.OllurGoreAnchor, 0.4f);
            Render("03_nh544_4lane_corridor_stretch.png", nhMid + new Vector3(-25f, 18f, -20f), nhMid + new Vector3(60f, 1f, -80f), 55f);

            // Shot 4: Bus Driving on RoadKit Surface (Chase Camera)
            Vector3 busPos = busTransform.position;
            Vector3 busForward = busTransform.forward;
            Render("04_bus_driving_chase.png", busPos - busForward * 18f + Vector3.up * 6.5f, busPos + Vector3.up * 1.5f, 55f);

            // Shot 5: Ollur 4-to-2 Transition & Loop
            Vector3 ollur = MannuthyOllurRoadKitBuilder.OllurGoreAnchor;
            Render("05_ollur_transition_and_loop.png", ollur + new Vector3(30f, 32f, 35f), ollur, 50f);

            Debug.Log($"[DriveTest] Screenshots successfully saved to {ArtifactsDir}");
        }

        private static void Render(string filename, Vector3 pos, Vector3 lookAt, float fov)
        {
            var camGo = new GameObject("ShotCam");
            var cam = camGo.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = new Color(0.55f, 0.68f, 0.82f);
            cam.fieldOfView = fov;
            cam.nearClipPlane = 0.5f;
            cam.farClipPlane = 4000f;
            camGo.transform.position = pos;
            camGo.transform.LookAt(lookAt);

            int width = 1920;
            int height = 1080;
            var rt = RenderTexture.GetTemporary(width, height, 24, RenderTextureFormat.ARGB32);
            var prevRt = RenderTexture.active;
            var prevTarget = cam.targetTexture;

            try
            {
                cam.targetTexture = rt;
                RenderTexture.active = rt;
                cam.Render();

                var tex = new Texture2D(width, height, TextureFormat.RGB24, false);
                tex.ReadPixels(new Rect(0, 0, width, height), 0, 0);
                tex.Apply();

                string filePath = Path.Combine(ArtifactsDir, filename);
                File.WriteAllBytes(filePath, tex.EncodeToPNG());
                UnityEngine.Object.DestroyImmediate(tex);
                Debug.Log($"[DriveTest] Captured: {filename}");
            }
            finally
            {
                cam.targetTexture = prevTarget;
                RenderTexture.active = prevRt;
                RenderTexture.ReleaseTemporary(rt);
                UnityEngine.Object.DestroyImmediate(camGo);
            }
        }
    }
}
