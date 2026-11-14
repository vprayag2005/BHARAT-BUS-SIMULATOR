using System;
using UnityEngine;

namespace BharatBusSim.Traffic
{
    public class SimpleTrafficAgent : MonoBehaviour
    {
        public Vector3[] waypoints;
        public int currentWaypointIndex = 0;
        public float cruiseSpeed = 12f;

        private void Update()
        {
            if (waypoints == null || waypoints.Length < 2) return;

            Vector3 target = waypoints[currentWaypointIndex];
            Vector3 delta = target - transform.position;
            delta.y = 0; // Keep horizontal

            float dist = delta.magnitude;
            if (dist < 3.5f)
            {
                currentWaypointIndex = (currentWaypointIndex + 1) % waypoints.Length;
                target = waypoints[currentWaypointIndex];
                delta = target - transform.position;
                delta.y = 0;
            }

            if (delta.sqrMagnitude > 0.01f)
            {
                Quaternion targetRot = Quaternion.LookRotation(delta.normalized, Vector3.up);
                transform.rotation = Quaternion.Slerp(transform.rotation, targetRot, Time.deltaTime * 3.5f);
                transform.position += transform.forward * (cruiseSpeed * Time.deltaTime);
            }
        }
    }
}
