using System;
using UnityEngine;
using UnityEngine.InputSystem;
using BharatBusSim.UI;

namespace BharatBusSim.Vehicles
{
    public enum TransmissionGear
    {
        Park,
        Reverse,
        Neutral,
        Drive
    }

    public enum AxleDrivePolicy
    {
        [Tooltip("6x2 Multi-Axle: Middle Axle 2 is primary drive (100% engine torque); Rear Tag Axle 3 is unpowered trailing")]
        MidAxleDrive_6x2,

        [Tooltip("6x4 Heavy Tandem: Both Axle 2 and Axle 3 driven equally (50% / 50% split)")]
        AllRearAxles_6x4
    }

    [RequireComponent(typeof(Rigidbody))]
    public class BusController : MonoBehaviour
    {
        [Header("Wheel Colliders (6-Wheel 3-Axle Configuration)")]
        [Tooltip("Front steer axle left (Axle 1)")]
        public WheelCollider frontLeftCollider;
        [Tooltip("Front steer axle right (Axle 1)")]
        public WheelCollider frontRightCollider;

        [Tooltip("Middle drive axle left (Axle 2 - Primary Drive)")]
        public WheelCollider midLeftCollider;
        [Tooltip("Middle drive axle right (Axle 2 - Primary Drive)")]
        public WheelCollider midRightCollider;

        [Tooltip("Rear tag axle left (Axle 3 - Trailing Tag)")]
        public WheelCollider rearLeftCollider;
        [Tooltip("Rear tag axle right (Axle 3 - Trailing Tag)")]
        public WheelCollider rearRightCollider;

        [Header("6-Wheel Powertrain & Brake Distribution")]
        [Tooltip("Tractive torque split policy across driven axles")]
        [SerializeField] private AxleDrivePolicy drivePolicy = AxleDrivePolicy.MidAxleDrive_6x2;

        [Tooltip("Ratio of service braking applied to front steer axle (Axle 1)")]
        [SerializeField] private float frontBrakeRatio = 0.70f;

        [Tooltip("Ratio of service braking applied to middle drive axle (Axle 2)")]
        [SerializeField] private float midBrakeRatio = 0.85f;

        [Tooltip("Ratio of service braking applied to rear tag axle (Axle 3)")]
        [SerializeField] private float rearBrakeRatio = 0.55f;

        [Header("Tuning Parameters (Realistic Powertrain & Braking)")]
        [Tooltip("Nominal bus mass in kg (baseline ~10,500 kg for standard single-deck bus)")]
        [SerializeField] private float busMass = 10500f;

        [Tooltip("Center of mass vertical offset (lowered to prevent unrealistic rolling)")]
        [SerializeField] private Vector3 centerOfMassOffset = new Vector3(0f, -0.6f, 0.2f);

        [Tooltip("Maximum launch motor torque applied to drive wheels at low speeds (N*m)")]
        [SerializeField] private float launchMotorTorque = 5400f;

        [Tooltip("Mid-range motor torque applied during intermediate acceleration (N*m)")]
        [SerializeField] private float midMotorTorque = 4200f;

        [Tooltip("Cruising motor torque applied at highway speeds (N*m)")]
        [SerializeField] private float cruiseMotorTorque = 3500f;

        [Tooltip("Maximum reverse motor torque (N*m)")]
        [SerializeField] private float maxReverseTorque = 2800f;

        [Tooltip("Service brake torque applied to all 4 wheels (N*m)")]
        [SerializeField] private float maxBrakeTorque = 8000f;

        [Tooltip("Parking / handbrake torque applied to rear wheels (N*m)")]
        [SerializeField] private float maxHandbrakeTorque = 15000f;

        [Tooltip("Maximum front wheel steer angle at low speeds (degrees)")]
        [SerializeField] private float maxSteerAngle = 33f;

        [Tooltip("High-speed steer angle limiter (degrees at top speed)")]
        [SerializeField] private float highSpeedSteerAngle = 14f;

        [Tooltip("Top speed governor in km/h")]
        [SerializeField] private float topSpeedKmh = 75f;

        [Header("Secondary Vehicle Equipment")]
        [Tooltip("Physical spotlights/headlights for night illumination")]
        public Light[] headlamps;

        [Tooltip("AudioSource for pneumatic horn sound")]
        public AudioSource hornAudioSource;

        [Header("Runtime State")]
        public float currentSpeedKmh;
        public TransmissionGear gearMode = TransmissionGear.Neutral;
        public string currentGear = "N"; // String representation for backward compatibility
        public float currentThrottle;
        public float currentBrake;
        public float currentSteer;
        public bool isHandbrakeActive;
        public bool initializeInPark = false;

        // Vehicle systems state
        public bool headlightsActive { get; private set; }
        public bool hazardsActive { get; private set; }
        public bool wipersActive { get; private set; }
        public bool hornActive { get; private set; }
        public bool turnLeftActive { get; private set; }
        public bool turnRightActive { get; private set; }
        public bool indicatorBlinkState { get; private set; }

        private float _blinkTimer;
        private const float BlinkInterval = 0.4f;

        // Spawn point for reset
        private Vector3 _spawnPosition;
        private Quaternion _spawnRotation;
        private Rigidbody _rb;

        // UI inputs from on-screen pedals/buttons
        private float _uiThrottle;
        private float _uiBrake;
        private float _uiSteer;
        private bool _uiHandbrake;

        public Rigidbody Rigidbody => EnsureRigidbody();

        public Rigidbody EnsureRigidbody()
        {
            if (_rb == null)
            {
                _rb = GetComponent<Rigidbody>();
                if (_rb != null)
                {
                    _rb.mass = busMass;
                    _rb.centerOfMass = centerOfMassOffset;
                    _rb.interpolation = RigidbodyInterpolation.Interpolate;
                }
            }
            return _rb;
        }

        private void Awake()
        {
            EnsureRigidbody();

            _spawnPosition = transform.position;
            _spawnRotation = transform.rotation;

            // Initialize audio synthesizer fallback for ₹0 horn if no clip assigned
            SetupProceduralHorn();
            SetupDefaultHeadlights();
        }

        private void Start()
        {
            if (initializeInPark)
            {
                gearMode = TransmissionGear.Park;
                currentGear = "P";
                isHandbrakeActive = true;
                _uiHandbrake = true;
            }
        }

        private void Update()
        {
            ProcessInput();
            CalculateSpeed();
            UpdateBlinkers();

            // Keyboard shortcut for reset (safely checking InputSystem Keyboard and legacy fallback)
            if (GameInputHelper.WasKeyPressedThisFrame(Key.R, KeyCode.R))
            {
                ResetBus();
            }
        }

        private void FixedUpdate()
        {
            StepPhysics();
        }

        /// <summary>
        /// Executes one physics step of steering, motor torque, and braking distribution.
        /// </summary>
        public void StepPhysics()
        {
            ApplySteering();
            ApplyMotorAndBraking();
        }

        public void SyncInputs()
        {
            // Keyboard / Axis input queried safely via GameInputHelper (supports new InputSystem & legacy)
            float kbThrottle = 0f;
            float kbBrake = 0f;
            float kbSteer = 0f;
            bool kbHandbrake = false;

            if (GameInputHelper.IsKeyPressed(Key.W, KeyCode.W) || GameInputHelper.IsKeyPressed(Key.UpArrow, KeyCode.UpArrow)) kbThrottle = 1f;
            if (GameInputHelper.IsKeyPressed(Key.S, KeyCode.S) || GameInputHelper.IsKeyPressed(Key.DownArrow, KeyCode.DownArrow)) kbBrake = 1f;
            if (GameInputHelper.IsKeyPressed(Key.A, KeyCode.A) || GameInputHelper.IsKeyPressed(Key.LeftArrow, KeyCode.LeftArrow)) kbSteer -= 1f;
            if (GameInputHelper.IsKeyPressed(Key.D, KeyCode.D) || GameInputHelper.IsKeyPressed(Key.RightArrow, KeyCode.RightArrow)) kbSteer += 1f;
            if (GameInputHelper.IsKeyPressed(Key.Space, KeyCode.Space)) kbHandbrake = true;

            // Combine keyboard and UI touch inputs
            currentThrottle = Mathf.Clamp01(Mathf.Max(kbThrottle, _uiThrottle));
            currentBrake = Mathf.Clamp01(Mathf.Max(kbBrake, _uiBrake));
            currentSteer = Mathf.Clamp(kbSteer + _uiSteer, -1f, 1f);
            isHandbrakeActive = kbHandbrake || _uiHandbrake || (gearMode == TransmissionGear.Park);
        }

        private void ProcessInput()
        {
            // Gear shift keyboard shortcuts
            if (GameInputHelper.WasKeyPressedThisFrame(Key.P, KeyCode.P)) TrySetGear(TransmissionGear.Park, out _);
            if (GameInputHelper.WasKeyPressedThisFrame(Key.R, KeyCode.R)) TrySetGear(TransmissionGear.Reverse, out _);
            if (GameInputHelper.WasKeyPressedThisFrame(Key.N, KeyCode.N)) TrySetGear(TransmissionGear.Neutral, out _);
            if (GameInputHelper.WasKeyPressedThisFrame(Key.D, KeyCode.D)) TrySetGear(TransmissionGear.Drive, out _);

            // Vehicle systems shortcuts
            if (GameInputHelper.WasKeyPressedThisFrame(Key.L, KeyCode.L)) ToggleHeadlights();
            if (GameInputHelper.WasKeyPressedThisFrame(Key.J, KeyCode.J)) ToggleHazards();
            if (GameInputHelper.IsKeyPressed(Key.H, KeyCode.H)) SetHorn(true);
            else if (GameInputHelper.WasKeyReleasedThisFrame(Key.H, KeyCode.H)) SetHorn(false);

            SyncInputs();
        }

        private void CalculateSpeed()
        {
            EnsureRigidbody();
#if UNITY_6000_0_OR_NEWER
            Vector3 vel = _rb != null ? _rb.linearVelocity : Vector3.zero;
#else
            Vector3 vel = _rb != null ? _rb.velocity : Vector3.zero;
#endif
            currentSpeedKmh = vel.magnitude * 3.6f;

            // Update string representation
            switch (gearMode)
            {
                case TransmissionGear.Park: currentGear = "P"; break;
                case TransmissionGear.Reverse: currentGear = "R"; break;
                case TransmissionGear.Neutral: currentGear = "N"; break;
                case TransmissionGear.Drive: currentGear = "D"; break;
            }
        }

        private void UpdateBlinkers()
        {
            if (hazardsActive || turnLeftActive || turnRightActive)
            {
                _blinkTimer += Time.deltaTime;
                if (_blinkTimer >= BlinkInterval)
                {
                    _blinkTimer = 0f;
                    indicatorBlinkState = !indicatorBlinkState;
                }
            }
            else
            {
                _blinkTimer = 0f;
                indicatorBlinkState = false;
            }
        }

        /// <summary>
        /// Safe P/R/N/D gear state machine with safety interlocks.
        /// </summary>
        public bool TrySetGear(TransmissionGear targetGear, out string failReason)
        {
            failReason = string.Empty;
            if (gearMode == targetGear) return true;

            EnsureRigidbody();
#if UNITY_6000_0_OR_NEWER
            float forwardVelocity = _rb != null ? Vector3.Dot(transform.forward, _rb.linearVelocity) : 0f;
#else
            float forwardVelocity = _rb != null ? Vector3.Dot(transform.forward, _rb.velocity) : 0f;
#endif

            // Interlock 1: Shifting into Park requires vehicle to be nearly stationary (< 1.5 km/h)
            if (targetGear == TransmissionGear.Park)
            {
                if (currentSpeedKmh > 1.5f)
                {
                    failReason = "Stop vehicle before shifting to Park";
                    return false;
                }
            }

            // Interlock 2: Shifting out of Park requires pressing the brake pedal
            if (gearMode == TransmissionGear.Park)
            {
                if (currentBrake < 0.05f)
                {
                    failReason = "Hold Brake to shift out of Park";
                    return false;
                }
            }

            // Interlock 3: Shifting into Reverse requires forward speed to be near zero (< 1.5 km/h)
            if (targetGear == TransmissionGear.Reverse)
            {
                if (forwardVelocity > 0.4f)
                {
                    failReason = "Stop vehicle before shifting to Reverse";
                    return false;
                }
            }

            // Interlock 4: Shifting into Drive requires reverse speed to be near zero (< 1.5 km/h)
            if (targetGear == TransmissionGear.Drive)
            {
                if (forwardVelocity < -0.4f)
                {
                    failReason = "Stop vehicle before shifting to Drive";
                    return false;
                }
            }

            // Transition approved
            gearMode = targetGear;
            if (gearMode == TransmissionGear.Park)
            {
                _uiHandbrake = true;
            }
            else
            {
                _uiHandbrake = false;
            }

            CalculateSpeed();
            return true;
        }

        private void ApplySteering()
        {
            if (frontLeftCollider == null || frontRightCollider == null) return;

            // Scale steer angle down smoothly at higher speeds to maintain bus stability
            float speedFactor = Mathf.Clamp01(currentSpeedKmh / topSpeedKmh);
            float effectiveMaxSteer = Mathf.Lerp(maxSteerAngle, highSpeedSteerAngle, speedFactor);
            float steer = currentSteer * effectiveMaxSteer;

            frontLeftCollider.steerAngle = steer;
            frontRightCollider.steerAngle = steer;

            // Middle drive and rear tag axles must strictly remain unsteered
            if (midLeftCollider != null) midLeftCollider.steerAngle = 0f;
            if (midRightCollider != null) midRightCollider.steerAngle = 0f;
            if (rearLeftCollider != null) rearLeftCollider.steerAngle = 0f;
            if (rearRightCollider != null) rearRightCollider.steerAngle = 0f;
        }

        private void ApplyMotorAndBraking()
        {
            float motor = 0f;
            float brake = 0f;

            // Handbrake or Park overrides everything
            if (isHandbrakeActive || gearMode == TransmissionGear.Park)
            {
                brake = maxHandbrakeTorque;
                motor = 0f;
            }
            else
            {
                // Service Brakes apply in all gears when brake pedal is pressed
                if (currentBrake > 0.05f)
                {
                    brake = currentBrake * maxBrakeTorque;
                }
                else
                {
                    // Natural engine rolling resistance
                    brake = 250f;
                }

                // Throttle application governed strictly by current gear
                if (currentThrottle > 0.05f)
                {
                    switch (gearMode)
                    {
                        case TransmissionGear.Drive:
                            if (currentSpeedKmh < topSpeedKmh)
                            {
                                // Progressive automatic transmission torque curve:
                                // High launch torque at 0-18 km/h, steady mid torque at 18-45 km/h, efficient cruising torque up to 75 km/h
                                float effectiveTorque;
                                if (currentSpeedKmh < 18f)
                                {
                                    effectiveTorque = Mathf.Lerp(launchMotorTorque, midMotorTorque, currentSpeedKmh / 18f);
                                }
                                else if (currentSpeedKmh < 45f)
                                {
                                    effectiveTorque = Mathf.Lerp(midMotorTorque, cruiseMotorTorque, (currentSpeedKmh - 18f) / 27f);
                                }
                                else
                                {
                                    effectiveTorque = cruiseMotorTorque;
                                }

                                float speedHeadroom = Mathf.Clamp01((topSpeedKmh - currentSpeedKmh) / 8f);
                                motor = currentThrottle * effectiveTorque * speedHeadroom;
                                brake = 0f; // Disengage rolling resistance when throttle applied
                            }
                            break;

                        case TransmissionGear.Reverse:
                            if (currentSpeedKmh < 25f) // Reverse speed governor: 25 km/h
                            {
                                float speedHeadroom = Mathf.Clamp01((25f - currentSpeedKmh) / 5f);
                                motor = -currentThrottle * maxReverseTorque * speedHeadroom;
                                brake = 0f;
                            }
                            break;

                        case TransmissionGear.Neutral:
                        case TransmissionGear.Park:
                            motor = 0f; // No drive in N or P
                            break;
                    }
                }
            }

            // 1. Apply motor torque according to documented drive policy
            float midMotor = 0f;
            float rearMotor = 0f;

            if (drivePolicy == AxleDrivePolicy.MidAxleDrive_6x2)
            {
                // 6x2 Multi-Axle: Middle Axle 2 is primary drive (100% engine torque); Axle 3 is unpowered tag
                midMotor = motor;
                rearMotor = 0f;
            }
            else if (drivePolicy == AxleDrivePolicy.AllRearAxles_6x4)
            {
                // 6x4 Heavy Tandem: Both rear bogie axles driven with equal torque distribution
                midMotor = motor * 0.5f;
                rearMotor = motor * 0.5f;
            }

            if (midLeftCollider != null) midLeftCollider.motorTorque = midMotor;
            if (midRightCollider != null) midRightCollider.motorTorque = midMotor;
            if (rearLeftCollider != null) rearLeftCollider.motorTorque = rearMotor;
            if (rearRightCollider != null) rearRightCollider.motorTorque = rearMotor;

            // Front steer axle is strictly non-driven
            if (frontLeftCollider != null) frontLeftCollider.motorTorque = 0f;
            if (frontRightCollider != null) frontRightCollider.motorTorque = 0f;

            // 2. Apply proportional brake torque across all 6 wheels
            if (isHandbrakeActive || gearMode == TransmissionGear.Park)
            {
                // Parking brake locks middle drive and rear tag axles, plus helper front retard
                if (midLeftCollider != null) midLeftCollider.brakeTorque = maxHandbrakeTorque;
                if (midRightCollider != null) midRightCollider.brakeTorque = maxHandbrakeTorque;
                if (rearLeftCollider != null) rearLeftCollider.brakeTorque = maxHandbrakeTorque;
                if (rearRightCollider != null) rearRightCollider.brakeTorque = maxHandbrakeTorque;
                if (frontLeftCollider != null) frontLeftCollider.brakeTorque = maxHandbrakeTorque * 0.5f;
                if (frontRightCollider != null) frontRightCollider.brakeTorque = maxHandbrakeTorque * 0.5f;
            }
            else
            {
                // Balanced 3-axle service braking
                if (frontLeftCollider != null) frontLeftCollider.brakeTorque = brake * frontBrakeRatio;
                if (frontRightCollider != null) frontRightCollider.brakeTorque = brake * frontBrakeRatio;
                if (midLeftCollider != null) midLeftCollider.brakeTorque = brake * midBrakeRatio;
                if (midRightCollider != null) midRightCollider.brakeTorque = brake * midBrakeRatio;
                if (rearLeftCollider != null) rearLeftCollider.brakeTorque = brake * rearBrakeRatio;
                if (rearRightCollider != null) rearRightCollider.brakeTorque = brake * rearBrakeRatio;
            }
        }

        // Public API for Mobile Touch Controls
        public void SetUIThrottle(float value)
        {
            _uiThrottle = Mathf.Clamp01(value);
            SyncInputs();
        }

        public void SetUIBrake(float value)
        {
            _uiBrake = Mathf.Clamp01(value);
            SyncInputs();
        }

        public void SetUISteer(float value)
        {
            _uiSteer = Mathf.Clamp(value, -1f, 1f);
            SyncInputs();
        }

        public void SetUIHandbrake(bool active)
        {
            _uiHandbrake = active;
            SyncInputs();
        }

        // Vehicle Systems Public API
        public void ToggleHeadlights()
        {
            headlightsActive = !headlightsActive;
            if (headlamps != null)
            {
                foreach (var light in headlamps)
                {
                    if (light != null) light.enabled = headlightsActive;
                }
            }
        }

        public void ToggleHazards()
        {
            hazardsActive = !hazardsActive;
            if (hazardsActive)
            {
                turnLeftActive = false;
                turnRightActive = false;
            }
        }

        public void ToggleTurnLeft()
        {
            turnLeftActive = !turnLeftActive;
            if (turnLeftActive)
            {
                turnRightActive = false;
                hazardsActive = false;
            }
        }

        public void ToggleTurnRight()
        {
            turnRightActive = !turnRightActive;
            if (turnRightActive)
            {
                turnLeftActive = false;
                hazardsActive = false;
            }
        }

        public void ToggleWipers()
        {
            wipersActive = !wipersActive;
        }

        public void SetHorn(bool active)
        {
            hornActive = active;
            if (hornAudioSource != null)
            {
                if (active && !hornAudioSource.isPlaying)
                {
                    hornAudioSource.Play();
                }
                else if (!active && hornAudioSource.isPlaying)
                {
                    hornAudioSource.Stop();
                }
            }
        }

        private void SetupDefaultHeadlights()
        {
            if (headlamps == null || headlamps.Length == 0)
            {
                // Create two forward-facing spotlights on the bus front bumper (Z = 6.05m for 12m Kerala coach)
                var lLeft = CreateHeadlamp("Headlight_FL", new Vector3(-0.95f, 0.95f, 6.05f));
                var lRight = CreateHeadlamp("Headlight_FR", new Vector3(0.95f, 0.95f, 6.05f));
                headlamps = new Light[] { lLeft, lRight };
            }
        }

        private Light CreateHeadlamp(string name, Vector3 localPos)
        {
            var go = new GameObject(name);
            go.transform.SetParent(transform, false);
            go.transform.localPosition = localPos;
            go.transform.localRotation = Quaternion.Euler(5f, 0f, 0f); // Slight down-angle onto road

            var l = go.AddComponent<Light>();
            l.type = LightType.Spot;
            l.color = new Color(1f, 0.96f, 0.88f);
            l.intensity = 2.5f;
            l.range = 45f;
            l.spotAngle = 60f;
            l.enabled = false;
            return l;
        }

        private void SetupProceduralHorn()
        {
            if (hornAudioSource == null)
            {
                hornAudioSource = GetComponent<AudioSource>();
                if (hornAudioSource == null)
                {
                    hornAudioSource = gameObject.AddComponent<AudioSource>();
                }
                hornAudioSource.playOnAwake = false;
                hornAudioSource.loop = true;
                hornAudioSource.spatialBlend = 0.5f; // Semi-spatialized
                hornAudioSource.volume = 0.85f;

                // Procedural twin-tone commercial pneumatic air horn clip (380 Hz + 460 Hz)
                if (hornAudioSource.clip == null)
                {
                    hornAudioSource.clip = GenerateAirHornClip();
                }
            }
        }

        private AudioClip GenerateAirHornClip()
        {
            int sampleRate = 44100;
            float duration = 1.0f;
            int numSamples = (int)(sampleRate * duration);
            float[] samples = new float[numSamples];

            for (int i = 0; i < numSamples; i++)
            {
                float t = (float)i / sampleRate;
                // Dual tone commercial coach pneumatic blast (380Hz and 460Hz) with slight harmonic grit
                float tone1 = Mathf.Sin(2f * Mathf.PI * 380f * t);
                float tone2 = Mathf.Sin(2f * Mathf.PI * 460f * t);
                float tone3 = Mathf.Sin(2f * Mathf.PI * 760f * t) * 0.25f;
                samples[i] = (tone1 * 0.45f + tone2 * 0.45f + tone3) * 0.8f;
            }

            AudioClip clip = AudioClip.Create("ProceduralAirHorn", numSamples, 1, sampleRate, false);
            clip.SetData(samples, 0);
            return clip;
        }

        /// <summary>
        /// Safely resets and uprights the bus on the track.
        /// </summary>
        public void ResetBus()
        {
            Debug.Log("[BusController] Resetting bus to safe starting bay position...");

            EnsureRigidbody();
            if (_rb != null)
            {
#if UNITY_6000_0_OR_NEWER
                _rb.linearVelocity = Vector3.zero;
#else
                _rb.velocity = Vector3.zero;
#endif
                _rb.angularVelocity = Vector3.zero;
            }

            if (_spawnPosition == Vector3.zero && transform.position != Vector3.zero)
            {
                _spawnPosition = transform.position;
                _spawnRotation = transform.rotation;
            }

            Vector3 currentPos = transform.position;
            Vector3 safePos = initializeInPark ? _spawnPosition : new Vector3(currentPos.x, Mathf.Max(currentPos.y, _spawnPosition.y) + 1.2f, currentPos.z);
            Quaternion safeRot = initializeInPark ? _spawnRotation : Quaternion.Euler(0f, transform.eulerAngles.y, 0f);

            transform.SetPositionAndRotation(safePos, safeRot);

            if (frontLeftCollider != null) { frontLeftCollider.brakeTorque = 0; frontLeftCollider.motorTorque = 0; }
            if (frontRightCollider != null) { frontRightCollider.brakeTorque = 0; frontRightCollider.motorTorque = 0; }
            if (midLeftCollider != null) { midLeftCollider.brakeTorque = 0; midLeftCollider.motorTorque = 0; }
            if (midRightCollider != null) { midRightCollider.brakeTorque = 0; midRightCollider.motorTorque = 0; }
            if (rearLeftCollider != null) { rearLeftCollider.brakeTorque = 0; rearLeftCollider.motorTorque = 0; }
            if (rearRightCollider != null) { rearRightCollider.brakeTorque = 0; rearRightCollider.motorTorque = 0; }

            if (initializeInPark)
            {
                gearMode = TransmissionGear.Park;
                currentGear = "P";
                isHandbrakeActive = true;
                _uiHandbrake = true;
            }
            else
            {
                // Reset gear to Neutral
                gearMode = TransmissionGear.Neutral;
                currentGear = "N";
                isHandbrakeActive = false;
                _uiHandbrake = false;
            }
        }
    }
}
