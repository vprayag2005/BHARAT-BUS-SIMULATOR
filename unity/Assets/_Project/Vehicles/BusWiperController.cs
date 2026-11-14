using UnityEngine;

namespace BharatBusSim.Vehicles
{
    /// <summary>
    /// Dual articulated windshield wiper system for Kerala Coach Bus.
    /// Handles realistic sinusoidal sweeping, auto-parking at rest position,
    /// and synchronized wiper rubber-on-glass audio playback.
    /// </summary>
    public class BusWiperController : MonoBehaviour
    {
        [Header("References")]
        public BusController busController;
        public Transform leftWiperPivot;
        public Transform rightWiperPivot;
        public AudioSource wiperAudioSource;

        [Header("Wiper Sweep Settings")]
        [Tooltip("Maximum sweep angle across windshield in degrees")]
        public float maxSweepAngle = 65.0f;

        [Tooltip("Wiper cycle sweep frequency in Hz")]
        public float sweepSpeed = 1.35f;

        [Tooltip("Park speed when returning to rest position")]
        public float parkSpeed = 2.5f;

        private float _currentAngle = 0f;
        private float _sweepPhase = 0f;
        private bool _wasSweeping = false;
        private bool _soundTriggeredThisHalf = false;

        private void Start()
        {
            if (busController == null)
            {
                busController = GetComponent<BusController>();
            }
        }

        private void Update()
        {
            bool isActive = busController != null && busController.wipersActive;
            float dt = Time.deltaTime;

            if (isActive)
            {
                _sweepPhase += dt * sweepSpeed * Mathf.PI * 2f;
                if (_sweepPhase > Mathf.PI * 2f)
                {
                    _sweepPhase -= Mathf.PI * 2f;
                }

                // Smooth sinusoidal sweep: 0 to 1 to 0
                float normalizedSweep = 0.5f - 0.5f * Mathf.Cos(_sweepPhase);
                _currentAngle = normalizedSweep * maxSweepAngle;

                // Trigger sweep sound near peak and trough
                if (normalizedSweep > 0.85f && !_soundTriggeredThisHalf)
                {
                    PlayWiperSound();
                    _soundTriggeredThisHalf = true;
                }
                else if (normalizedSweep < 0.15f && _soundTriggeredThisHalf)
                {
                    PlayWiperSound();
                    _soundTriggeredThisHalf = false;
                }

                _wasSweeping = true;
            }
            else if (_wasSweeping)
            {
                // Smoothly park wiper to rest angle (0 deg)
                _currentAngle = Mathf.MoveTowards(_currentAngle, 0f, parkSpeed * maxSweepAngle * dt);
                if (Mathf.Approximately(_currentAngle, 0f))
                {
                    _currentAngle = 0f;
                    _wasSweeping = false;
                    _sweepPhase = 0f;
                }
            }

            ApplyWiperRotation(_currentAngle);
        }

        private void ApplyWiperRotation(float angle)
        {
            // Left wiper sweeps clockwise (+Z axis on windshield plane), Right wiper sweeps clockwise
            if (leftWiperPivot != null)
            {
                leftWiperPivot.localRotation = Quaternion.Euler(0f, 0f, -angle);
            }

            if (rightWiperPivot != null)
            {
                rightWiperPivot.localRotation = Quaternion.Euler(0f, 0f, -angle);
            }
        }

        private void PlayWiperSound()
        {
            if (wiperAudioSource != null && wiperAudioSource.clip != null)
            {
                wiperAudioSource.pitch = Random.Range(0.95f, 1.05f);
                wiperAudioSource.PlayOneShot(wiperAudioSource.clip, 0.65f);
            }
        }
    }
}
