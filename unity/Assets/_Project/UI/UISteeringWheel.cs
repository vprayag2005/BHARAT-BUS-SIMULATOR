using UnityEngine;
using UnityEngine.EventSystems;

namespace BharatBusSim.UI
{
    /// <summary>
    /// Virtual mobile steering wheel inspired by BUSSID and commercial coach driving simulators.
    /// Tracks pointer drag angles, rotates the wheel visual, and springs back to center on release.
    /// </summary>
    public class UISteeringWheel : MonoBehaviour, IPointerDownHandler, IDragHandler, IPointerUpHandler
    {
        [Tooltip("Wheel visual RectTransform that rotates")]
        public RectTransform wheelGraphic;

        [Tooltip("Maximum steering rotation angle in degrees (540° for 1.5 turns each way, 3.0 turns lock-to-lock)")]
        [SerializeField] private float maxSteerAngle = 540f;

        [Tooltip("Speed in degrees per second at which wheel auto-centers when released")]
        [SerializeField] private float centerReturnSpeed = 540f;

        [Tooltip("Smoothing speed when tracking touch drag rotation (1/s)")]
        [SerializeField] private float dragLerpSpeed = 18f;

        [Tooltip("Easing rate when auto-centering after release (1/s)")]
        [SerializeField] private float centerReturnEaseSpeed = 6f;

        [Tooltip("Deadzone radius around center in screen pixels to avoid erratic jumps")]
        [SerializeField] private float deadzoneRadius = 15f;

        public float SteerValue { get; private set; } // -1.0 (Full Left) to +1.0 (Full Right)
        public float CurrentAngle { get; private set; } // In degrees: negative is left, positive is right
        public bool IsBeingControlled { get; private set; }

        private Canvas _parentCanvas;
        private Camera _uiCamera;
        private Vector2 _wheelCenterScreen;
        private float _previousDragAngle;
        private float _targetAngle;
        private int _activePointerId = -1;

        private void Awake()
        {
            if (wheelGraphic == null)
            {
                wheelGraphic = GetComponent<RectTransform>();
            }
            _parentCanvas = GetComponentInParent<Canvas>();
            UpdateUICamera();
        }

        private void Update()
        {
            float dt = Time.unscaledDeltaTime;
            if (!IsBeingControlled)
            {
                // Smooth hydraulic ease-out auto-center toward 0°
                float easeT = 1f - Mathf.Exp(-centerReturnEaseSpeed * dt);
                float easedTarget = Mathf.Lerp(_targetAngle, 0f, easeT);
                _targetAngle = Mathf.MoveTowards(_targetAngle, easedTarget, centerReturnSpeed * dt);
                if (Mathf.Abs(_targetAngle) < 0.05f)
                {
                    _targetAngle = 0f;
                }
            }

            float lerpRate = IsBeingControlled ? dragLerpSpeed : centerReturnEaseSpeed * 1.8f;
            float t = 1f - Mathf.Exp(-lerpRate * dt);
            CurrentAngle = Mathf.Lerp(CurrentAngle, _targetAngle, t);
            if (!IsBeingControlled && Mathf.Abs(_targetAngle) < 0.01f && Mathf.Abs(CurrentAngle) < 0.05f)
            {
                CurrentAngle = 0f;
            }
            ApplyWheelAngle(CurrentAngle);
        }

        public void OnPointerDown(PointerEventData eventData)
        {
            if (_activePointerId != -1) return;

            _activePointerId = eventData.pointerId;
            IsBeingControlled = true;
            _targetAngle = CurrentAngle;
            CalculateWheelCenter();

            Vector2 dir = eventData.position - _wheelCenterScreen;
            if (dir.sqrMagnitude >= deadzoneRadius * deadzoneRadius)
            {
                _previousDragAngle = Vector2.SignedAngle(Vector2.up, dir);
            }
        }

        public void OnDrag(PointerEventData eventData)
        {
            if (eventData.pointerId != _activePointerId) return;

            Vector2 dir = eventData.position - _wheelCenterScreen;
            if (dir.sqrMagnitude < deadzoneRadius * deadzoneRadius) return;

            float currentTouchAngle = Vector2.SignedAngle(Vector2.up, dir);
            float deltaAngle = Mathf.DeltaAngle(_previousDragAngle, currentTouchAngle);

            // Invert delta because clockwise touch drag rotates wheel to the right (+Angle in our coord)
            _targetAngle = Mathf.Clamp(_targetAngle - deltaAngle, -maxSteerAngle, maxSteerAngle);
            _previousDragAngle = currentTouchAngle;
        }

        public void OnPointerUp(PointerEventData eventData)
        {
            if (eventData.pointerId == _activePointerId)
            {
                _activePointerId = -1;
                IsBeingControlled = false;
            }
        }

        private void ApplyWheelAngle(float angle)
        {
            if (wheelGraphic != null)
            {
                wheelGraphic.localEulerAngles = new Vector3(0f, 0f, -angle);
            }
            SteerValue = Mathf.Clamp(angle / maxSteerAngle, -1f, 1f);
        }

        private void CalculateWheelCenter()
        {
            UpdateUICamera();
            if (RectTransformUtility.ScreenPointToWorldPointInRectangle(wheelGraphic, Vector2.zero, _uiCamera, out Vector3 worldPos))
            {
                _wheelCenterScreen = RectTransformUtility.WorldToScreenPoint(_uiCamera, wheelGraphic.position);
            }
            else
            {
                _wheelCenterScreen = new Vector2(Screen.width * 0.15f, Screen.height * 0.25f);
            }
        }

        private void UpdateUICamera()
        {
            if (_parentCanvas != null && _parentCanvas.renderMode != RenderMode.ScreenSpaceOverlay)
            {
                _uiCamera = _parentCanvas.worldCamera;
            }
            else
            {
                _uiCamera = null;
            }
        }

        private void OnDisable()
        {
            _activePointerId = -1;
            IsBeingControlled = false;
            _targetAngle = 0f;
            CurrentAngle = 0f;
            ApplyWheelAngle(0f);
        }
    }
}
