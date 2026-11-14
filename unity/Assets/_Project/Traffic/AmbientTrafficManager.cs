using System;
using System.Collections.Generic;
using UnityEngine;

namespace BharatBusSim.Traffic
{
    public class AmbientTrafficManager : MonoBehaviour
    {
        [Header("Traffic Population")]
        [Range(2, 10)]
        public int maxVehicles = 6;

        public Transform playerBusTransform;

        private List<GameObject> _activeVehicles = new List<GameObject>();

        public void InitializeTraffic(Transform parent, Material carMat, Material busMat, Material rickshawMat)
        {
            var trafficGroup = new GameObject("Ambient_Traffic_System");
            trafficGroup.transform.SetParent(parent, false);

            // Waypoint loops for ambient traffic:
            // Loop 1: NH 544 Highway cruising loop (Mannuthy -> Ollur -> Amballur return)
            var highwayWaypoints = new[]
            {
                new Vector3(950f, 0.4f, 180f),
                new Vector3(960f, 0.4f, -300f),
                new Vector3(980f, 0.4f, -900f),
                new Vector3(990f, 0.4f, -1400f),
                new Vector3(990f, 0.4f, -900f),
                new Vector3(960f, 0.4f, -300f)
            };

            // Loop 2: Swaraj Round circular traffic loop
            var roundWaypoints = new[]
            {
                new Vector3(0f, 0.4f, -30f),
                new Vector3(30f, 0.4f, 0f),
                new Vector3(0f, 0.4f, 30f),
                new Vector3(-30f, 0.4f, 0f)
            };

            // Spawn 3 Highway Vehicles
            CreateAmbientVehicle(trafficGroup.transform, "AI_Kerala_KSRTC_Bus", busMat, new Vector3(3.2f, 3.4f, 10.5f), highwayWaypoints, 14f, 0);
            CreateAmbientVehicle(trafficGroup.transform, "AI_Highway_Sedan_White", carMat, new Vector3(2.0f, 1.5f, 4.6f), highwayWaypoints, 18f, 2);
            CreateAmbientVehicle(trafficGroup.transform, "AI_Highway_Goods_Truck", carMat, new Vector3(2.5f, 3.0f, 7.5f), highwayWaypoints, 12f, 4);

            // Spawn 2 Swaraj Round City Vehicles (Auto-rickshaw and city car)
            CreateAmbientVehicle(trafficGroup.transform, "AI_Thrissur_AutoRickshaw", rickshawMat, new Vector3(1.5f, 1.8f, 2.7f), roundWaypoints, 8f, 1);
            CreateAmbientVehicle(trafficGroup.transform, "AI_City_Car", carMat, new Vector3(1.9f, 1.5f, 4.4f), roundWaypoints, 10f, 3);
        }

        private void CreateAmbientVehicle(Transform parent, string name, Material mat, Vector3 scale, Vector3[] waypoints, float speed, int startWaypointIdx)
        {
            var vehicle = GameObject.CreatePrimitive(PrimitiveType.Cube);
            vehicle.name = name;
            vehicle.transform.SetParent(parent, false);
            vehicle.transform.position = waypoints[startWaypointIdx % waypoints.Length];
            vehicle.transform.localScale = scale;
            vehicle.GetComponent<Renderer>().sharedMaterial = mat;

            var agent = vehicle.AddComponent<SimpleTrafficAgent>();
            agent.waypoints = waypoints;
            agent.currentWaypointIndex = startWaypointIdx % waypoints.Length;
            agent.cruiseSpeed = speed;

            _activeVehicles.Add(vehicle);
        }
    }
}
