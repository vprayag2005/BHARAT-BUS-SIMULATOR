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

        [Tooltip("Maximum steering rotation angle in degrees (e.g. 450° for 1.25 turns, or 180° for direct steering)")]
        [SerializeField] private float maxSteerAngle = 450f;

        [Tooltip("Speed in degrees per second at which wheel auto-centers when released")]
        [SerializeField] private float centerReturnSpeed = 600f;

        [Tooltip("Deadzone radius around center in screen pixels to avoid erratic jumps")]
        [SerializeField] private float deadzoneRadius = 15f;

        public float SteerValue { get; private set; } // -1.0 (Full Left) to +1.0 (Full Right)
        public float CurrentAngle { get; private set; } // In degrees: negative is left, positive is right
        public bool IsBeingControlled { get; private set; }

        private Canvas _parentCanvas;
        private Camera _uiCamera;
        private Vector2 _wheelCenterScreen;
        private float _previousDragAngle;
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
            if (!IsBeingControlled)
            {
                // Auto-center spring
                if (Mathf.Abs(CurrentAngle) > 0.01f)
                {
                    CurrentAngle = Mathf.MoveTowards(CurrentAngle, 0f, centerReturnSpeed * Time.unscaledDeltaTime);
                    ApplyWheelAngle(CurrentAngle);
                }
                else
                {
                    CurrentAngle = 0f;
                    ApplyWheelAngle(0f);
                }
            }
        }

        public void OnPointerDown(PointerEventData eventData)
        {
            if (_activePointerId != -1) return;

            _activePointerId = eventData.pointerId;
            IsBeingControlled = true;
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
            CurrentAngle = Mathf.Clamp(CurrentAngle - deltaAngle, -maxSteerAngle, maxSteerAngle);
            _previousDragAngle = currentTouchAngle;

            ApplyWheelAngle(CurrentAngle);
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
            CurrentAngle = 0f;
            ApplyWheelAngle(0f);
        }
    }
}
