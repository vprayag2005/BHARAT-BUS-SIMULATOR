using System;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using BharatBusSim.Vehicles;

namespace BharatBusSim.Editor
{
    public static class AccelerationBenchmark
    {
        public static void Measure()
        {
            const string scenePath = "Assets/_Project/Scenes/02_Phase4_BusPrototype.unity";
            EditorSceneManager.OpenScene(scenePath, OpenSceneMode.Single);

            GameObject busGo = GameObject.Find("Kerala_Bus_Prototype");
            if (busGo == null)
            {
                Debug.LogError("[AccelerationBenchmark] Bus prototype not found!");
                return;
            }

            var busController = busGo.GetComponent<BusController>();
            var rb = busGo.GetComponent<Rigidbody>();

            foreach (var bw in busGo.GetComponentsInChildren<BusWheel>())
            {
                bw.Initialize();
            }

            bool origSim = Physics.simulationMode != SimulationMode.Script;
            Physics.simulationMode = SimulationMode.Script;

            try
            {
                // Settle
                for (int i = 0; i < 30; i++) Physics.Simulate(0.02f);

                busController.TrySetGear(TransmissionGear.Drive, out _);
                busController.SetUIThrottle(1.0f);
                busController.SetUIBrake(0.0f);
                busController.SetUIHandbrake(false);

                float t20 = -1f, t40 = -1f, t60 = -1f;
                float timeElapsed = 0f;
                const float dt = 0.02f;

                Debug.Log("[AccelerationBenchmark] Starting acceleration run (Full Throttle in Drive)...");

                for (int step = 0; step < 2500; step++) // Max 50 seconds
                {
                    busController.StepPhysics();
                    Physics.Simulate(dt);
                    timeElapsed += dt;

#if UNITY_6000_0_OR_NEWER
                    float speedKmh = rb.linearVelocity.magnitude * 3.6f;
#else
                    float speedKmh = rb.velocity.magnitude * 3.6f;
#endif

                    if (t20 < 0f && speedKmh >= 20f) t20 = timeElapsed;
                    if (t40 < 0f && speedKmh >= 40f) t40 = timeElapsed;
                    if (t60 < 0f && speedKmh >= 60f)
                    {
                        t60 = timeElapsed;
                        break;
                    }
                }

                Debug.Log($"[AccelerationBenchmark] RESULTS: 0-20 km/h: {(t20 > 0 ? $"{t20:F2}s" : "DNF")}, " +
                          $"0-40 km/h: {(t40 > 0 ? $"{t40:F2}s" : "DNF")}, " +
                          $"0-60 km/h: {(t60 > 0 ? $"{t60:F2}s" : "DNF")}");
            }
            finally
            {
                Physics.simulationMode = origSim ? SimulationMode.FixedUpdate : SimulationMode.Script;
            }
        }
    }
}
