using System;
using UnityEngine;
using UnityEngine.EventSystems;
#if ENABLE_INPUT_SYSTEM
using UnityEngine.InputSystem;
#endif

namespace BharatBusSim.Vehicles
{
    /// <summary>
    /// 3-Mode Camera Controller for Bharat Bus Simulator:
    ///   Cam 1: Driver Cockpit / Interior Cabin View (first-person RHD seat, wide FOV, low near-clip)
    ///   Cam 2: Rear Chase / Exterior Follow View (centered behind bus, smooth damping, anti-clip)
    ///   Cam 3: 360 Orbit Camera (finger touch swipe & mouse drag to rotate entire bus view)
    /// </summary>
    public class BusCameraController : MonoBehaviour
    {
        public enum CameraMode
        {
            DriverCockpit = 0, // Cam 1
            RearChase = 1,     // Cam 2
            Orbit360 = 2       // Cam 3
        }

        [Serializable]
        public struct CameraPreset
        {
            public string name;
            public CameraMode mode;
            public float distance;
            public float height;
            public float lateralOffset;
            public Vector3 lookAtOffset;
            public float positionDamping;
            public float rotationDamping;
            public float fieldOfView;
            public float nearClipPlane;
        }

        [Tooltip("Target bus transform to follow")]
        public Transform target;

        [Header("Starting Camera")]
        [SerializeField] private int initialPresetIndex = 0;

        [Header("Presets (Exactly 3 Angles)")]
        [SerializeField] private CameraPreset[] presets = new CameraPreset[]
        {
            // Cam 1: Driver Cockpit (Interior RHD cabin view, ref Screenshot_20261027_173056)
            new CameraPreset
            {
                name = "Driver Cockpit",
                mode = CameraMode.DriverCockpit,
                distance = 0f,
                height = 2.35f, // Driver eye level seated in RHD cockpit
                lateralOffset = 0.70f, // Seated directly behind RHD steering wheel
                lookAtOffset = new Vector3(0.70f, 1.48f, 12.0f),
                positionDamping = 30.0f,
                rotationDamping = 30.0f,
                fieldOfView = 72.0f,
                nearClipPlane = 0.03f
            },
            // Cam 2: Rear Chase (External follow view, ref Screenshot_20261027_173036)
            new CameraPreset
            {
                name = "Rear Chase",
                mode = CameraMode.RearChase,
                distance = 15.8f,
                height = 4.8f,
                lateralOffset = 0f,
                lookAtOffset = new Vector3(0f, 1.35f, 0.5f),
                positionDamping = 10.0f,
                rotationDamping = 6.0f,
                fieldOfView = 60.0f,
                nearClipPlane = 0.3f
            },
            // Cam 3: 360 Touch Orbit (Free-look rotate entire bus with finger swipe)
            new CameraPreset
            {
                name = "360 Orbit",
                mode = CameraMode.Orbit360,
                distance = 11.5f,
                height = 2.0f,
                lateralOffset = 0f,
                lookAtOffset = new Vector3(0f, 1.7f, 0f),
                positionDamping = 12.0f,
                rotationDamping = 12.0f,
                fieldOfView = 60.0f,
                nearClipPlane = 0.3f
            }
        };

        [Header("Rear Parking Camera (Compatibility)")]
        [SerializeField] private CameraPreset rearPreset = new CameraPreset
        {
            name = "Rear Parking",
            mode = CameraMode.RearChase,
            distance = -9.5f,
            height = 4.2f,
            lateralOffset = 0f,
            lookAtOffset = new Vector3(0f, 1.6f, -2.0f),
            positionDamping = 10.0f,
            rotationDamping = 6.0f,
            fieldOfView = 60.0f,
            nearClipPlane = 0.3f
        };

        [Header("360 Orbit Settings")]
        [SerializeField] private float orbitSensitivityX = 0.22f;
        [SerializeField] private float orbitSensitivityY = 0.18f;
        [SerializeField] private float minPitch = 5.0f;
        [SerializeField] private float maxPitch = 72.0f;

        [Header("Driver Cockpit Settings")]
        [SerializeField] private float cockpitBasePitch = 6.5f;
        [SerializeField] private float cockpitEyeZ = 4.65f;
        [SerializeField] private float minCockpitYaw = -60.0f;   // Look left stops at passenger entrance door
        [SerializeField] private float maxCockpitYaw = 28.0f;    // Look right stops cleanly at driver door & right mirror
        [SerializeField] private float minCockpitPitch = -18.0f; // Look down towards pedals & console
        [SerializeField] private float maxCockpitPitch = 15.0f;  // Look up towards valance & mirror
        [SerializeField] private float cockpitLookSmoothSpeed = 16.0f;

        public int CurrentPresetIndex { get; private set; }
        public int TotalPresets => (presets != null && presets.Length > 0) ? presets.Length : 3;
        public bool IsRearViewActive { get; private set; }
        public CameraMode CurrentMode => presets != null && CurrentPresetIndex >= 0 && CurrentPresetIndex < presets.Length
            ? presets[CurrentPresetIndex].mode
            : (CameraMode)CurrentPresetIndex;

        private Camera _attachedCamera;
        private bool _isInitialized = false;

        // Cockpit and exterior interior mesh references
        private GameObject _cockpitRoot;
        private GameObject[] _legacyInteriorRoots;
        private bool _interiorRefsResolved = false;

        public GameObject CockpitRoot => _cockpitRoot;

        public void EnsureTarget()
        {
            if (target != null) return;

            var bus = FindFirstObjectByType<BusController>();
            if (bus != null)
            {
                target = bus.transform;
                return;
            }

            var player = GameObject.FindWithTag("Player");
            if (player != null)
            {
                target = player.transform;
                return;
            }

            var busGo = GameObject.Find("Kerala_Bus_Prototype");
            if (busGo != null)
            {
                target = busGo.transform;
            }
        }

        // Orbit state
        private float _orbitYaw = 0f;       // Degrees relative to bus heading
        private float _orbitPitch = 20f;    // Degrees elevation
        private float _currentOrbitYaw = 0f;
        private float _currentOrbitPitch = 20f;
        private Vector2 _lastMousePos;
        private bool _isMouseDragging = false;
        private int _activeOrbitTouchId = -1;

        // Chase state (Cam 2)
        private float _chaseYaw = 0f;
        private float _currentChaseDist = 15.8f;

        // Cockpit Look state (Cam 1)
        private float _cockpitLookYaw = 0f;
        private float _cockpitLookPitch = 0f;
        private float _currentCockpitYaw = 0f;
        private float _currentCockpitPitch = 0f;

        public float CockpitLookYaw => _cockpitLookYaw;
        public float CockpitLookPitch => _cockpitLookPitch;
        public float CurrentCockpitYaw => _currentCockpitYaw;
        public float CurrentCockpitPitch => _currentCockpitPitch;

        private void Awake()
        {
            _attachedCamera = GetComponent<Camera>();
            if (presets == null || presets.Length < 3)
            {
                ResetDefaultPresets();
            }
            CurrentPresetIndex = Mathf.Clamp(initialPresetIndex, 0, presets.Length - 1);
            EnsureTarget();
        }

        private void Start()
        {
            EnsureTarget();
            ApplyPresetSettings();
            if (target != null)
            {
                SnapToTarget();
            }
        }

        public void ResetDefaultPresets()
        {
            presets = new CameraPreset[]
            {
                new CameraPreset
                {
                    name = "Driver Cockpit",
                    mode = CameraMode.DriverCockpit,
                    distance = 0f,
                    height = 2.35f,
                    lateralOffset = 0.70f,
                    lookAtOffset = new Vector3(0.70f, 1.48f, 12.0f),
                    positionDamping = 30.0f,
                    rotationDamping = 30.0f,
                    fieldOfView = 72.0f,
                    nearClipPlane = 0.03f
                },
                new CameraPreset
                {
                    name = "Rear Chase",
                    mode = CameraMode.RearChase,
                    distance = 15.8f,
                    height = 4.8f,
                    lateralOffset = 0f,
                    lookAtOffset = new Vector3(0f, 1.35f, 0.5f),
                    positionDamping = 10.0f,
                    rotationDamping = 6.0f,
                    fieldOfView = 60.0f,
                    nearClipPlane = 0.3f
                },
                new CameraPreset
                {
                    name = "360 Orbit",
                    mode = CameraMode.Orbit360,
                    distance = 11.5f,
                    height = 2.0f,
                    lateralOffset = 0f,
                    lookAtOffset = new Vector3(0f, 1.7f, 0f),
                    positionDamping = 12.0f,
                    rotationDamping = 12.0f,
                    fieldOfView = 60.0f,
                    nearClipPlane = 0.3f
                }
            };
        }

        public void SnapToTarget()
        {
            EnsureTarget();
            if (target == null)
            {
                return;
            }
            CameraPreset activePreset = IsRearViewActive ? rearPreset : presets[CurrentPresetIndex];

            if (activePreset.mode == CameraMode.DriverCockpit)
            {
                _cockpitLookYaw = 0f;
                _cockpitLookPitch = 0f;
                _currentCockpitYaw = 0f;
                _currentCockpitPitch = 0f;
                transform.position = target.position + target.TransformDirection(new Vector3(activePreset.lateralOffset, activePreset.height, cockpitEyeZ));
                transform.rotation = target.rotation * Quaternion.Euler(cockpitBasePitch, 0f, 0f);
            }
            else if (activePreset.mode == CameraMode.Orbit360)
            {
                _orbitYaw = 0f;
                _orbitPitch = 20f;
                _currentOrbitYaw = 0f;
                _currentOrbitPitch = 20f;
                Vector3 focal = target.position + target.TransformDirection(activePreset.lookAtOffset);
                Quaternion rot = Quaternion.Euler(_orbitPitch, target.eulerAngles.y + _orbitYaw, 0f);
                transform.position = focal - (rot * Vector3.forward * activePreset.distance);
                transform.LookAt(focal);
            }
            else // RearChase
            {
                float rotY = target.eulerAngles.y + (IsRearViewActive ? 180f : 0f);
                _chaseYaw = rotY;
                _currentChaseDist = activePreset.distance;
                Quaternion currentRotation = Quaternion.Euler(0f, rotY, 0f);
                Vector3 focal = target.position + target.TransformDirection(activePreset.lookAtOffset);
                Vector3 wanted = focal - (currentRotation * Vector3.forward * activePreset.distance) + (currentRotation * Vector3.right * activePreset.lateralOffset);
                wanted.y = target.position.y + activePreset.height;
                transform.position = wanted;
                Vector3 lookDir = focal - wanted;
                if (lookDir.sqrMagnitude > 0.001f)
                {
                    transform.rotation = Quaternion.LookRotation(lookDir.normalized, Vector3.up);
                }
            }

            if (_attachedCamera == null) _attachedCamera = GetComponent<Camera>();
            if (_attachedCamera != null)
            {
                _attachedCamera.nearClipPlane = activePreset.nearClipPlane;
                _attachedCamera.fieldOfView = activePreset.fieldOfView;
            }

            UpdateCockpitVisibility();
            _isInitialized = true;
        }

        private void Update()
        {
            if (CurrentMode == CameraMode.Orbit360 || CurrentMode == CameraMode.DriverCockpit)
            {
                HandleInputDrag();
            }

            if (CurrentMode == CameraMode.DriverCockpit)
            {
                HandleCockpitKeyboardLook();
            }
        }

        private void HandleCockpitKeyboardLook()
        {
#if ENABLE_INPUT_SYSTEM
            var keyboard = Keyboard.current;
            if (keyboard != null)
            {
                float keyTurnSpeed = 100f * Time.unscaledDeltaTime;
                if (keyboard.leftArrowKey.isPressed || keyboard.qKey.isPressed)
                {
                    _cockpitLookYaw = Mathf.Clamp(_cockpitLookYaw - keyTurnSpeed, minCockpitYaw, maxCockpitYaw);
                }
                else if (keyboard.rightArrowKey.isPressed || keyboard.eKey.isPressed)
                {
                    _cockpitLookYaw = Mathf.Clamp(_cockpitLookYaw + keyTurnSpeed, minCockpitYaw, maxCockpitYaw);
                }

                if (keyboard.upArrowKey.isPressed)
                {
                    _cockpitLookPitch = Mathf.Clamp(_cockpitLookPitch + keyTurnSpeed, minCockpitPitch, maxCockpitPitch);
                }
                else if (keyboard.downArrowKey.isPressed)
                {
                    _cockpitLookPitch = Mathf.Clamp(_cockpitLookPitch - keyTurnSpeed, minCockpitPitch, maxCockpitPitch);
                }

                if (keyboard.spaceKey.wasPressedThisFrame)
                {
                    ResetCockpitView();
                }
            }
#endif
        }

        private void HandleInputDrag()
        {
#if ENABLE_INPUT_SYSTEM
            // 1. Mouse Drag (Editor & PC)
            var mouse = Mouse.current;
            if (mouse != null)
            {
                if (mouse.leftButton.wasPressedThisFrame || mouse.rightButton.wasPressedThisFrame)
                {
                    if (EventSystem.current == null || !EventSystem.current.IsPointerOverGameObject())
                    {
                        _isMouseDragging = true;
                        _lastMousePos = mouse.position.ReadValue();
                    }
                }
                else if (_isMouseDragging && (mouse.leftButton.isPressed || mouse.rightButton.isPressed))
                {
                    Vector2 currentPos = mouse.position.ReadValue();
                    Vector2 delta = currentPos - _lastMousePos;
                    _lastMousePos = currentPos;
                    ForwardDrag(delta);
                }
                else if (_isMouseDragging && !mouse.leftButton.isPressed && !mouse.rightButton.isPressed)
                {
                    _isMouseDragging = false;
                }
            }

            // 2. Mobile Touches
            var touchScreen = Touchscreen.current;
            if (touchScreen != null)
            {
                var touches = touchScreen.touches;
                for (int i = 0; i < touches.Count; i++)
                {
                    var t = touches[i];
                    if (t.press.wasPressedThisFrame)
                    {
                        int touchId = t.touchId.ReadValue();
                        if (EventSystem.current == null || !EventSystem.current.IsPointerOverGameObject(touchId))
                        {
                            _activeOrbitTouchId = touchId;
                        }
                    }
                    else if (t.press.isPressed && t.touchId.ReadValue() == _activeOrbitTouchId)
                    {
                        Vector2 delta = t.delta.ReadValue();
                        ForwardDrag(delta);
                    }
                    else if (t.press.wasReleasedThisFrame && t.touchId.ReadValue() == _activeOrbitTouchId)
                    {
                        _activeOrbitTouchId = -1;
                    }
                }
            }
#endif
        }

        private void ForwardDrag(Vector2 delta)
        {
            if (CurrentMode == CameraMode.Orbit360)
            {
                OnOrbitDrag(delta);
            }
            else if (CurrentMode == CameraMode.DriverCockpit)
            {
                OnCockpitLookDrag(delta);
            }
        }

        public void OnOrbitDrag(Vector2 delta)
        {
            _orbitYaw += delta.x * orbitSensitivityX;
            _orbitPitch = Mathf.Clamp(_orbitPitch - delta.y * orbitSensitivityY, minPitch, maxPitch);
        }

        public void OnCockpitLookDrag(Vector2 delta)
        {
            _cockpitLookYaw = Mathf.Clamp(_cockpitLookYaw + delta.x * orbitSensitivityX, minCockpitYaw, maxCockpitYaw);
            _cockpitLookPitch = Mathf.Clamp(_cockpitLookPitch - delta.y * orbitSensitivityY, minCockpitPitch, maxCockpitPitch);
        }

        public void SetCockpitLook(float yaw, float pitch)
        {
            _cockpitLookYaw = Mathf.Clamp(yaw, minCockpitYaw, maxCockpitYaw);
            _cockpitLookPitch = Mathf.Clamp(pitch, minCockpitPitch, maxCockpitPitch);
        }

        public void SetCockpitEyeSettings(float height, float lateralOffset, float eyeZ, float basePitch)
        {
            cockpitEyeZ = eyeZ;
            cockpitBasePitch = Mathf.Abs(basePitch);
            if (presets != null && presets.Length > 0)
            {
                presets[0].height = height;
                presets[0].lateralOffset = lateralOffset;
                presets[0].fieldOfView = 72.0f;
                presets[0].nearClipPlane = 0.03f;
            }
        }

        public void ResetCockpitView()
        {
            _cockpitLookYaw = 0f;
            _cockpitLookPitch = 0f;
        }

        public bool IsCockpitLookOffset => Mathf.Abs(_cockpitLookYaw) > 5f || Mathf.Abs(_cockpitLookPitch) > 5f;

        private void LateUpdate()
        {
            EnsureTarget();
            if (target == null)
            {
                return;
            }

            if (!_isInitialized)
            {
                SnapToTarget();
                return;
            }

            if (!_interiorRefsResolved && target != null)
            {
                UpdateCockpitVisibility();
            }

            CameraPreset activePreset = IsRearViewActive ? rearPreset : presets[CurrentPresetIndex];
            float dt = Time.unscaledDeltaTime > 0.0001f ? Mathf.Min(Time.unscaledDeltaTime, 0.05f) : 0.02f;

            // 1. Camera Mode: Driver Cockpit (Interior cabin view)
            if (activePreset.mode == CameraMode.DriverCockpit)
            {
                _currentCockpitYaw = Mathf.LerpAngle(_currentCockpitYaw, _cockpitLookYaw, cockpitLookSmoothSpeed * dt);
                _currentCockpitPitch = Mathf.Lerp(_currentCockpitPitch, _cockpitLookPitch, cockpitLookSmoothSpeed * dt);

                Vector3 wantedEyePos = target.position + target.TransformDirection(new Vector3(activePreset.lateralOffset, activePreset.height, cockpitEyeZ));
                transform.position = Vector3.Lerp(transform.position, wantedEyePos, activePreset.positionDamping * dt);

                Quaternion headRot = target.rotation * Quaternion.Euler(cockpitBasePitch + _currentCockpitPitch, _currentCockpitYaw, 0f);
                transform.rotation = Quaternion.Slerp(transform.rotation, headRot, activePreset.rotationDamping * dt);
            }
            // 2. Camera Mode: 360 Orbit View (finger touch rotation around entire bus)
            else if (activePreset.mode == CameraMode.Orbit360)
            {
                Vector3 focalPoint = target.position + target.TransformDirection(activePreset.lookAtOffset);

                _currentOrbitYaw = Mathf.LerpAngle(_currentOrbitYaw, _orbitYaw, 20f * dt);
                _currentOrbitPitch = Mathf.Lerp(_currentOrbitPitch, _orbitPitch, 20f * dt);

                float totalYaw = target.eulerAngles.y + _currentOrbitYaw;
                Quaternion orbitRot = Quaternion.Euler(_currentOrbitPitch, totalYaw, 0f);

                Vector3 wantedPos = focalPoint - (orbitRot * Vector3.forward * activePreset.distance);

                // Anti-clipping spherecast protection
                Vector3 camRayDir = wantedPos - focalPoint;
                float camRayDist = camRayDir.magnitude;
                if (camRayDist > 0.5f)
                {
                    camRayDir.Normalize();
                    int layerMask = ~LayerMask.GetMask("Ignore Raycast");
                    RaycastHit[] hits = Physics.SphereCastAll(focalPoint, 0.30f, camRayDir, camRayDist, layerMask, QueryTriggerInteraction.Ignore);
                    float closestDist = camRayDist;
                    for (int h = 0; h < hits.Length; h++)
                    {
                        var hit = hits[h];
                        if (hit.collider == null || hit.collider.isTrigger) continue;
                        if (hit.transform == target || hit.transform.IsChildOf(target)) continue;

                        if (hit.distance < closestDist && hit.distance > 0.2f)
                        {
                            closestDist = hit.distance;
                        }
                    }

                    if (closestDist < camRayDist)
                    {
                        float safeDist = Mathf.Max(4.0f, closestDist - 0.35f);
                        wantedPos = focalPoint + camRayDir * safeDist;
                    }
                }

                transform.position = Vector3.Lerp(transform.position, wantedPos, activePreset.positionDamping * dt);
                transform.LookAt(focalPoint);
            }
            // 3. Camera Mode: Rear Chase View (Follow view centered behind bus)
            else
            {
                float wantedYaw = target.eulerAngles.y + (IsRearViewActive ? 180f : 0f);
                _chaseYaw = Mathf.LerpAngle(_chaseYaw, wantedYaw, activePreset.rotationDamping * dt);

                Quaternion chaseRot = Quaternion.Euler(0f, _chaseYaw, 0f);
                Vector3 targetFocalPoint = target.position + target.TransformDirection(activePreset.lookAtOffset);

                // Anti-Clipping & Occlusion Raycast with smoothed distance
                float targetDist = activePreset.distance;
                Vector3 backDir = -(chaseRot * Vector3.forward);
                int layerMask = ~LayerMask.GetMask("Ignore Raycast");
                RaycastHit[] hits = Physics.SphereCastAll(targetFocalPoint, 0.35f, backDir, activePreset.distance, layerMask, QueryTriggerInteraction.Ignore);
                float closestDist = activePreset.distance;
                for (int h = 0; h < hits.Length; h++)
                {
                    var hit = hits[h];
                    if (hit.collider == null || hit.collider.isTrigger) continue;
                    if (hit.transform == target || hit.transform.IsChildOf(target)) continue;

                    if (hit.distance < closestDist && hit.distance > 0.2f)
                    {
                        closestDist = hit.distance;
                    }
                }

                if (closestDist < activePreset.distance)
                {
                    targetDist = Mathf.Max(6.5f, closestDist - 0.4f);
                }
                _currentChaseDist = Mathf.Lerp(_currentChaseDist, targetDist, 15f * dt);

                Vector3 wantedPosition = targetFocalPoint - (chaseRot * Vector3.forward * _currentChaseDist) + (chaseRot * Vector3.right * activePreset.lateralOffset);
                wantedPosition.y = target.position.y + activePreset.height;

                transform.position = Vector3.Lerp(transform.position, wantedPosition, activePreset.positionDamping * dt);

                Vector3 lookDir = targetFocalPoint - transform.position;
                if (lookDir.sqrMagnitude > 0.001f)
                {
                    transform.rotation = Quaternion.LookRotation(lookDir.normalized, Vector3.up);
                }
            }

            // Smoothly update Camera FOV and NearClipPlane
            if (_attachedCamera != null)
            {
                if (Mathf.Abs(_attachedCamera.fieldOfView - activePreset.fieldOfView) > 0.1f)
                {
                    _attachedCamera.fieldOfView = Mathf.Lerp(_attachedCamera.fieldOfView, activePreset.fieldOfView, 12f * dt);
                }
                _attachedCamera.nearClipPlane = activePreset.nearClipPlane;
            }
        }

        public void CycleCamera()
        {
            if (presets == null || presets.Length == 0) return;
            IsRearViewActive = false;
            CurrentPresetIndex = (CurrentPresetIndex + 1) % presets.Length;
            ApplyPresetSettings();
            SnapToTarget();
        }

        public void SetCameraPreset(int index)
        {
            if (presets == null || presets.Length == 0) return;
            IsRearViewActive = false;
            CurrentPresetIndex = Mathf.Clamp(index, 0, presets.Length - 1);
            ApplyPresetSettings();
            SnapToTarget();
        }

        private void ApplyPresetSettings()
        {
            if (_attachedCamera == null) _attachedCamera = GetComponent<Camera>();
            if (_attachedCamera != null && presets != null && CurrentPresetIndex >= 0 && CurrentPresetIndex < presets.Length)
            {
                _attachedCamera.nearClipPlane = presets[CurrentPresetIndex].nearClipPlane;
                _attachedCamera.fieldOfView = presets[CurrentPresetIndex].fieldOfView;
            }

            if (presets != null && CurrentPresetIndex >= 0 && CurrentPresetIndex < presets.Length)
            {
                var preset = presets[CurrentPresetIndex];
                if (preset.mode == CameraMode.Orbit360)
                {
                    _orbitYaw = 0f;
                    _orbitPitch = 20f;
                    _currentOrbitYaw = 0f;
                    _currentOrbitPitch = 20f;
                }
                else if (preset.mode == CameraMode.RearChase)
                {
                    if (target != null)
                    {
                        _chaseYaw = target.eulerAngles.y + (IsRearViewActive ? 180f : 0f);
                    }
                    _currentChaseDist = preset.distance;
                }
                else if (preset.mode == CameraMode.DriverCockpit)
                {
                    ResetCockpitView();
                }
            }

            UpdateCockpitVisibility();
        }

        public void ResolveInteriorReferences()
        {
            if (target == null) return;

            if (_cockpitRoot == null)
            {
                Transform visuals = target.Find("KeralaBus12m_Visuals");
                Transform cT = visuals != null ? visuals.Find("RHD_DriverCockpit") : null;
                if (cT == null)
                {
                    var allTrans = target.GetComponentsInChildren<Transform>(true);
                    for (int i = 0; i < allTrans.Length; i++)
                    {
                        if (allTrans[i].name == "RHD_DriverCockpit")
                        {
                            cT = allTrans[i];
                            break;
                        }
                    }
                }
                if (cT != null)
                {
                    _cockpitRoot = cT.gameObject;
                }
            }

            if (_legacyInteriorRoots == null || _legacyInteriorRoots.Length == 0)
            {
                Transform visuals = target.Find("KeralaBus12m_Visuals");
                if (visuals != null)
                {
                    string[] names = new[] { "dasbor", "kursi_supir", "stir" };
                    var list = new System.Collections.Generic.List<GameObject>();
                    for (int i = 0; i < names.Length; i++)
                    {
                        Transform t = visuals.Find(names[i]);
                        if (t != null) list.Add(t.gameObject);
                    }
                    if (list.Count > 0)
                    {
                        _legacyInteriorRoots = list.ToArray();
                    }
                }
            }

            _interiorRefsResolved = (_cockpitRoot != null);
        }

        public void UpdateCockpitVisibility()
        {
            ResolveInteriorReferences();

            bool isCockpitMode = (CurrentMode == CameraMode.DriverCockpit);

            if (_cockpitRoot != null)
            {
                if (_cockpitRoot.activeSelf != isCockpitMode)
                {
                    _cockpitRoot.SetActive(isCockpitMode);
                }
            }

            if (_legacyInteriorRoots != null)
            {
                for (int i = 0; i < _legacyInteriorRoots.Length; i++)
                {
                    if (_legacyInteriorRoots[i] != null && _legacyInteriorRoots[i].activeSelf == isCockpitMode)
                    {
                        _legacyInteriorRoots[i].SetActive(!isCockpitMode);
                    }
                }
            }
        }

        public void ToggleRearView()
        {
            IsRearViewActive = !IsRearViewActive;
            if (target != null)
            {
                _chaseYaw = target.eulerAngles.y + (IsRearViewActive ? 180f : 0f);
            }
        }

        public void SetRearView(bool active)
        {
            if (IsRearViewActive != active)
            {
                IsRearViewActive = active;
                if (target != null)
                {
                    _chaseYaw = target.eulerAngles.y + (IsRearViewActive ? 180f : 0f);
                }
            }
        }
    }
}
