using System;
using UnityEngine;

namespace BharatBusSim.Vehicles
{
    /// <summary>
    /// Synchronizes 3D cockpit dashboard components (steering wheel, speedometer needle, tachometer needle)
    /// with the live BusController state for high-realism first-person driver cabin immersion.
    /// </summary>
    public class CockpitDashboardSync : MonoBehaviour
    {
        [Header("Bus Controller Reference")]
        public BusController busController;

        [Header("3D Steering Wheel")]
        public Transform steeringWheel;
        [Tooltip("Maximum steering wheel visual rotation in degrees (e.g. 450 = 1.25 turns lock-to-lock)")]
        public float maxSteerWheelRotation = 450.0f;
        public float steerSmoothSpeed = 16.0f;

        [Header("Analog Gauges")]
        public Transform speedoNeedle;
        public float speedoMinAngle = 120.0f;   // 0 km/h needle angle (degrees around local Z)
        public float speedoMaxAngle = -120.0f;  // 140 km/h needle angle
        public float maxSpeedKmh = 140.0f;

        public Transform tachNeedle;
        public float tachMinAngle = 120.0f;     // 0 RPM
        public float tachMaxAngle = -120.0f;    // 3000 RPM
        public float maxRpm = 3000.0f;

        // Current smoothed values
        private float _currentSteerAngle = 0f;
        private float _currentSpeed = 0f;
        private float _currentRpm = 750f; // Idle RPM

        private void Awake()
        {
            if (busController == null)
            {
                busController = GetComponentInParent<BusController>();
            }
        }

        private void Update()
        {
            // Early out when cockpit is inactive (exterior camera modes Cam 2 and Cam 3)
            if (steeringWheel != null && !steeringWheel.gameObject.activeInHierarchy)
            {
                return;
            }

            float dt = Time.deltaTime > 0.0001f ? Mathf.Min(Time.deltaTime, 0.05f) : 0.02f;

            float targetSteerRatio = 0f;
            float targetSpeedKmh = 0f;
            float targetRpm = 750f;

            if (busController != null)
            {
                // currentSteer is in range [-1.0, 1.0]
                targetSteerRatio = Mathf.Clamp(busController.currentSteer, -1.0f, 1.0f);
                targetSpeedKmh = Mathf.Abs(busController.currentSpeedKmh);

                // RPM estimation: baseline idle 750 + throttle boost + road speed
                float throttle = Mathf.Clamp01(busController.currentThrottle);
                float speedContribution = Mathf.Clamp(targetSpeedKmh * 12.0f, 0f, 1200f);
                targetRpm = 750f + (throttle * 1100f) + speedContribution;
            }

            // Smooth values
            _currentSteerAngle = Mathf.Lerp(_currentSteerAngle, targetSteerRatio * maxSteerWheelRotation, steerSmoothSpeed * dt);
            _currentSpeed = Mathf.Lerp(_currentSpeed, targetSpeedKmh, 12.0f * dt);
            _currentRpm = Mathf.Lerp(_currentRpm, targetRpm, 10.0f * dt);

            // 1. Rotate 3D Steering Wheel around its local axis (Z axis faces driver)
            if (steeringWheel != null)
            {
                steeringWheel.localRotation = Quaternion.Euler(0f, 0f, -_currentSteerAngle);
            }

            // 2. Rotate Speedometer Needle
            if (speedoNeedle != null)
            {
                float speedFrac = Mathf.Clamp01(_currentSpeed / maxSpeedKmh);
                float needleAngle = Mathf.Lerp(speedoMinAngle, speedoMaxAngle, speedFrac);
                speedoNeedle.localRotation = Quaternion.Euler(0f, 0f, needleAngle);
            }

            // 3. Rotate Tachometer Needle
            if (tachNeedle != null)
            {
                float rpmFrac = Mathf.Clamp01(_currentRpm / maxRpm);
                float needleAngle = Mathf.Lerp(tachMinAngle, tachMaxAngle, rpmFrac);
                tachNeedle.localRotation = Quaternion.Euler(0f, 0f, needleAngle);
            }
        }
    }
}
