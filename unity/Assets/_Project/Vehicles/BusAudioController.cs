using UnityEngine;

namespace BharatBusSim.Vehicles
{
    /// <summary>
    /// Comprehensive vehicle audio management for Bharat Bus Simulator.
    /// Drives dynamic multi-cylinder diesel engine acoustics, reverse warning siren,
    /// turn signal / hazard blinker clicking, and pneumatic air brake discharge.
    /// </summary>
    public class BusAudioController : MonoBehaviour
    {
        [Header("Bus Controller Reference")]
        public BusController busController;

        [Header("Audio Clips")]
        public AudioClip dieselEngineClip;
        public AudioClip reverseWarningClip;
        public AudioClip indicatorClip;
        public AudioClip airBrakeClip;
        public AudioClip wiperClip;

        [Header("Audio Sources")]
        public AudioSource engineAudioSource;
        public AudioSource reverseAudioSource;
        public AudioSource indicatorAudioSource;
        public AudioSource airBrakeAudioSource;
        public AudioSource wiperAudioSource;

        [Header("Engine Audio Tuning")]
        [Tooltip("Minimum engine idle pitch")]
        public float idlePitch = 0.82f;

        [Tooltip("Maximum engine high-speed pitch")]
        public float maxPitch = 1.55f;

        [Tooltip("Idle engine volume")]
        public float idleVolume = 0.38f;

        [Tooltip("Full throttle engine volume")]
        public float fullThrottleVolume = 0.85f;

        private bool _lastBlinkState = false;
        private bool _lastAirBrakeTriggered = false;
        private TransmissionGear _lastGear = TransmissionGear.Park;

        private void Awake()
        {
            if (busController == null)
            {
                busController = GetComponent<BusController>();
            }

            SetupAudioSources();
        }

        private void Start()
        {
            if (engineAudioSource != null && dieselEngineClip != null && !engineAudioSource.isPlaying)
            {
                engineAudioSource.clip = dieselEngineClip;
                engineAudioSource.loop = true;
                engineAudioSource.volume = idleVolume;
                engineAudioSource.pitch = idlePitch;
                engineAudioSource.Play();
            }
        }

        private void Update()
        {
            if (busController == null) return;

            UpdateEngineAudio();
            UpdateReverseAudio();
            UpdateIndicatorAudio();
            UpdateAirBrakeAudio();
        }

        private void UpdateEngineAudio()
        {
            if (engineAudioSource == null) return;

            float speedKmh = Mathf.Abs(busController.currentSpeedKmh);
            float normalizedSpeed = Mathf.Clamp01(speedKmh / 90.0f);
            float throttle = Mathf.Max(0f, busController.currentThrottle);

            // Compute target pitch and volume
            float targetPitch = Mathf.Lerp(idlePitch, maxPitch, (normalizedSpeed * 0.7f) + (throttle * 0.3f));
            float targetVolume = Mathf.Lerp(idleVolume, fullThrottleVolume, (throttle * 0.6f) + (normalizedSpeed * 0.4f));

            float dt = Time.deltaTime;
            engineAudioSource.pitch = Mathf.Lerp(engineAudioSource.pitch, targetPitch, 4.0f * dt);
            engineAudioSource.volume = Mathf.Lerp(engineAudioSource.volume, targetVolume, 5.0f * dt);
        }

        private void UpdateReverseAudio()
        {
            if (reverseAudioSource == null || reverseWarningClip == null) return;

            bool isReverse = busController.gearMode == TransmissionGear.Reverse;

            if (isReverse)
            {
                if (!reverseAudioSource.isPlaying)
                {
                    reverseAudioSource.clip = reverseWarningClip;
                    reverseAudioSource.loop = true;
                    reverseAudioSource.volume = 0.70f;
                    reverseAudioSource.Play();
                }
            }
            else
            {
                if (reverseAudioSource.isPlaying)
                {
                    reverseAudioSource.Stop();
                }
            }
        }

        private void UpdateIndicatorAudio()
        {
            if (indicatorAudioSource == null || indicatorClip == null) return;

            bool isIndicatorActive = busController.hazardsActive || busController.turnLeftActive || busController.turnRightActive;
            bool currentBlink = busController.indicatorBlinkState && isIndicatorActive;

            // Trigger on each rising edge of the blinker pulse
            if (currentBlink && !_lastBlinkState)
            {
                indicatorAudioSource.pitch = 1.0f;
                indicatorAudioSource.PlayOneShot(indicatorClip, 0.60f);
            }

            _lastBlinkState = currentBlink;
        }

        private void UpdateAirBrakeAudio()
        {
            if (airBrakeAudioSource == null || airBrakeClip == null) return;

            bool isBraking = busController.currentBrake > 0.4f || busController.isHandbrakeActive || busController.gearMode == TransmissionGear.Park;

            // Trigger pneumatic hiss on release or park engagement
            if (_lastAirBrakeTriggered && !isBraking)
            {
                airBrakeAudioSource.pitch = Random.Range(0.95f, 1.05f);
                airBrakeAudioSource.PlayOneShot(airBrakeClip, 0.75f);
            }
            else if (busController.gearMode == TransmissionGear.Park && _lastGear != TransmissionGear.Park)
            {
                airBrakeAudioSource.pitch = Random.Range(0.95f, 1.05f);
                airBrakeAudioSource.PlayOneShot(airBrakeClip, 0.85f);
            }

            _lastAirBrakeTriggered = isBraking;
            _lastGear = busController.gearMode;
        }

        public void SetupAudioSources()
        {
            if (engineAudioSource == null)
            {
                var engineGo = new GameObject("EngineAudio");
                engineGo.transform.SetParent(transform, false);
                engineGo.transform.localPosition = new Vector3(0f, 1.0f, -4.5f); // Rear engine bay
                engineAudioSource = engineGo.AddComponent<AudioSource>();
                engineAudioSource.spatialBlend = 0.7f;
                engineAudioSource.minDistance = 3f;
                engineAudioSource.maxDistance = 60f;
                engineAudioSource.playOnAwake = false;
            }

            if (reverseAudioSource == null)
            {
                var revGo = new GameObject("ReverseBeeperAudio");
                revGo.transform.SetParent(transform, false);
                revGo.transform.localPosition = new Vector3(0f, 1.2f, -5.5f); // Rear bumper
                reverseAudioSource = revGo.AddComponent<AudioSource>();
                reverseAudioSource.spatialBlend = 0.8f;
                reverseAudioSource.minDistance = 2f;
                reverseAudioSource.maxDistance = 50f;
                reverseAudioSource.playOnAwake = false;
            }

            if (indicatorAudioSource == null)
            {
                var indGo = new GameObject("IndicatorClickAudio");
                indGo.transform.SetParent(transform, false);
                indGo.transform.localPosition = new Vector3(0.70f, 2.0f, 4.8f); // Driver dash
                indicatorAudioSource = indGo.AddComponent<AudioSource>();
                indicatorAudioSource.spatialBlend = 0.3f;
                indicatorAudioSource.minDistance = 1f;
                indicatorAudioSource.maxDistance = 25f;
                indicatorAudioSource.playOnAwake = false;
            }

            if (airBrakeAudioSource == null)
            {
                var airGo = new GameObject("AirBrakeAudio");
                airGo.transform.SetParent(transform, false);
                airGo.transform.localPosition = new Vector3(0f, 0.6f, 1.0f); // Underbody reservoir
                airBrakeAudioSource = airGo.AddComponent<AudioSource>();
                airBrakeAudioSource.spatialBlend = 0.6f;
                airBrakeAudioSource.minDistance = 2f;
                airBrakeAudioSource.maxDistance = 40f;
                airBrakeAudioSource.playOnAwake = false;
            }

            if (wiperAudioSource == null)
            {
                var wipGo = new GameObject("WiperSweepAudio");
                wipGo.transform.SetParent(transform, false);
                wipGo.transform.localPosition = new Vector3(0f, 2.1f, 5.5f); // Windshield
                wiperAudioSource = wipGo.AddComponent<AudioSource>();
                wiperAudioSource.spatialBlend = 0.4f;
                wiperAudioSource.minDistance = 1.5f;
                wiperAudioSource.maxDistance = 30f;
                wiperAudioSource.playOnAwake = false;
            }
        }
    }
}
