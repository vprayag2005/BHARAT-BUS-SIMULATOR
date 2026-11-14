using System;
using System.Collections;
using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;
using UnityEngine.InputSystem;
using UnityEngine.InputSystem.UI;
using BharatBusSim.Vehicles;

namespace BharatBusSim.UI
{
    /// <summary>
    /// Native Unity uGUI mobile driving HUD & touch controller inspired by the Stitch mobile bus UI reference.
    /// Features:
    ///   - Virtual commercial coach steering wheel with transparent interior, "plus" spokes, center Horn, and top-center amber marker
    ///   - Scaled-up Hold-to-accelerate Gas pedal & Hold-to-brake pedal with metallic grip ribs
    ///   - P/R/N/D gear selector with safe transitions and active amber glow
    ///   - Dedicated Park brake, Turn indicators & Hazard flashers
    ///   - Headlights toggle & Camera switching (Cycle + Rear view)
    ///   - Real Speedometer in km/h (Rigorous Rigidbody velocity calculation)
    ///   - Fuel & Pneumatic Air Brake pressure telemetry
    ///   - Pause / Resume modal overlay with bus reset
    ///   - Responsive 1600x900 canvas scaler for prominent, comfortable mobile touch targets across 16:9 to 21:9
    /// </summary>
    public class BusDashboardHUD : MonoBehaviour
    {
        [Header("Vehicle & Camera References")]
        public BusController busController;
        public BusCameraController cameraController;

        [Header("HUD Configuration")]
        [Tooltip("True for touch steering wheel, false for touch left/right buttons")]
        public bool useSteeringWheel = true;

        // Runtime UI references
        private Canvas _canvas;
        private CanvasScaler _scaler;
        private RectTransform _safeAreaContainer;

        // Top Bar
        private Button _pauseBtn;
        private Button _beamBtn;
        private Image _beamIcon;
        private Text _beamLabel;
        private Button _wiperBtn;
        private Image _wiperIcon;
        private Text _wiperLabel;
        private Button _hazardBtn;
        private Image _hazardIcon;
        private Text _hazardLabel;
        private Button _camCycleBtn;
        private Text _camCycleLabel;
        private Button _camCenterBtn;
        private Button _camRearBtn;
        private Image _camRearIcon;
        private UICameraOrbitZone _orbitZone;

        // Left Controls
        private Button _turnLeftBtn;
        private Image _turnLeftIcon;
        private UISteeringWheel _steeringWheel;
        private UIHoldButton _leftSteerBtn;
        private UIHoldButton _rightSteerBtn;
        private GameObject _wheelContainer;
        private GameObject _buttonsSteerContainer;
        private UIHoldButton _hornButton;

        // Right Controls
        private Button _turnRightBtn;
        private Image _turnRightIcon;
        private Button _parkBrakeBtn;
        private Image _parkBrakeIcon;
        private Text _parkBrakeLabel;
        private UIHoldButton _gasPedal;
        private UIHoldButton _brakePedal;

        // Dashboard Cluster
        private Button[] _gearButtons; // 0=P, 1=R, 2=N, 3=D
        private Text[] _gearLabels;
        private Text _speedValueText;
        private Image _speedBarFill;
        private Text _notificationText;
        private float _notificationTimer;
        private Text _statusText;
        private RectTransform _dashGroup;
        private RectTransform _speedPanelRect;
        private RectTransform _gearButtonsGroup;

        // Pause Modal
        private GameObject _pauseModal;

        // Cached procedural textures and sprites for styling
        private Texture2D _panelBgTex;
        private Texture2D _btnBgTex;
        private Texture2D _whiteTex;
        private Texture2D _circleTex;
        private Texture2D _rimRingTex;
        private Texture2D _pedalRibTex;
        private Texture2D _topMarkerTex;
        private Texture2D _amberGlowTex;

        private Sprite _panelSprite;
        private Sprite _btnSprite;
        private Sprite _whiteSprite;
        private Sprite _circleSprite;
        private Sprite _rimRingSprite;
        private Sprite _pedalRibSprite;
        private Sprite _topMarkerSprite;
        private Sprite _amberGlowSprite;

        // Styling Palette (Dark Automotive Glass with Amber & Accent Highlights)
        private readonly Color ColBgDark = new Color(0.047f, 0.063f, 0.094f, 0.94f); // #0c1018
        private readonly Color ColPanel = new Color(0.086f, 0.114f, 0.161f, 0.90f);  // #161d29
        private readonly Color ColPanelBorder = new Color(1f, 1f, 1f, 0.12f);
        private readonly Color ColAmber = new Color(0.96f, 0.62f, 0.04f, 1.0f);        // #f59e0b
        private readonly Color ColAmberGlow = new Color(0.98f, 0.75f, 0.14f, 1.0f);    // #fbbf24
        private readonly Color ColCyan = new Color(0.00f, 0.76f, 0.85f, 1.0f);         // #00c2d7
        private readonly Color ColRed = new Color(0.92f, 0.28f, 0.28f, 1.0f);          // #ea4848
        private readonly Color ColGreen = new Color(0.20f, 0.82f, 0.45f, 1.0f);        // #33d173
        private readonly Color ColTextMuted = new Color(1f, 1f, 1f, 0.50f);

        private void Awake()
        {
            Application.targetFrameRate = 60;
            Screen.sleepTimeout = SleepTimeout.NeverSleep;

            CreateProceduralTextures();
            FindDependencies();
            BuildUIHierarchy();
        }

        private void Start()
        {
            UpdateGearVisuals();
            UpdateSystemsVisuals();
            UpdateCameraVisuals();

            var tollMgr = BharatBusSim.Toll.TollEconomyManager.Instance ?? FindFirstObjectByType<BharatBusSim.Toll.TollEconomyManager>();
            if (tollMgr != null)
            {
                tollMgr.OnTransactionCompleted += (msg, success) => ShowNotification(msg);
            }
        }

        private void Update()
        {
            if (busController == null)
            {
                busController = FindFirstObjectByType<BusController>();
                if (busController == null) return;
            }

            // Keyboard shortcut for pause toggle
            if (GameInputHelper.WasKeyPressedThisFrame(Key.Escape, KeyCode.Escape))
            {
                if (_pauseModal != null && _pauseModal.activeSelf)
                {
                    OnResumeClicked();
                }
                else
                {
                    OnPauseClicked();
                }
            }

            // Keyboard shortcut for camera cycle (C key)
            if (GameInputHelper.WasKeyPressedThisFrame(Key.C, KeyCode.C))
            {
                OnCamCycleClicked();
            }

            // 1. Process Steering Input
            float steerInput = 0f;
            if (useSteeringWheel && _steeringWheel != null)
            {
                steerInput = _steeringWheel.SteerValue;
            }
            else
            {
                if (_leftSteerBtn != null && _leftSteerBtn.IsPressed) steerInput -= 1f;
                if (_rightSteerBtn != null && _rightSteerBtn.IsPressed) steerInput += 1f;
            }
            busController.SetUISteer(steerInput);

            // 2. Process Pedals
            float throttle = (_gasPedal != null && _gasPedal.IsPressed) ? 1f : 0f;
            float brake = (_brakePedal != null && _brakePedal.IsPressed) ? 1f : 0f;
            busController.SetUIThrottle(throttle);
            busController.SetUIBrake(brake);

            // 3. Process Horn
            if (_hornButton != null)
            {
                busController.SetHorn(_hornButton.IsPressed);
            }

            // 4. Update Speedometer & Dashboard
            float speed = busController.currentSpeedKmh;
            if (_speedValueText != null)
            {
                _speedValueText.text = $"{Mathf.RoundToInt(speed):000}";
            }
            if (_speedBarFill != null)
            {
                _speedBarFill.fillAmount = Mathf.Clamp01(speed / 75f);
            }

            // 5. Update Gear buttons state
            UpdateGearVisuals();

            // 6. Update Turn indicators & Hazard blinkers
            UpdateBlinkersVisual();

            // 7. Update Notification text fade
            if (_notificationTimer > 0f)
            {
                _notificationTimer -= Time.unscaledDeltaTime;
                if (_notificationTimer <= 0f && _notificationText != null)
                {
                    _notificationText.text = string.Empty;
                }
            }

            // 8. Update Wallet Balance Badge
            if (_statusText != null)
            {
                var currentTollMgr = BharatBusSim.Toll.TollEconomyManager.Instance ?? FindFirstObjectByType<BharatBusSim.Toll.TollEconomyManager>();
                if (currentTollMgr != null)
                {
                    int wallet = currentTollMgr.CurrentBalance;
                    _statusText.text = $"WALLET: ₹{wallet:N0}   |   60 FPS   |   PASSENGERS 42 / 48";
                }
            }

            // 9. Update Camera Visuals & Center View button
            UpdateCameraVisuals();
        }

        private void FindDependencies()
        {
            if (busController == null) busController = FindFirstObjectByType<BusController>();
            if (cameraController == null) cameraController = FindFirstObjectByType<BusCameraController>();
            if (_orbitZone != null && _orbitZone.cameraController == null) _orbitZone.cameraController = cameraController;

            // Ensure EventSystem exists and uses InputSystemUIInputModule for multi-touch
            var es = FindFirstObjectByType<EventSystem>();
            if (es == null)
            {
                var esGo = new GameObject("EventSystem");
                es = esGo.AddComponent<EventSystem>();
            }

            var standalone = es.GetComponent<StandaloneInputModule>();
            if (standalone != null)
            {
                DestroyImmediate(standalone);
            }

            if (es.GetComponent<InputSystemUIInputModule>() == null)
            {
                es.gameObject.AddComponent<InputSystemUIInputModule>();
            }
        }

        private void CreateProceduralTextures()
        {
            // 1. 2x2 White Texture for fills
            _whiteTex = new Texture2D(2, 2);
            _whiteTex.SetPixels(new[] { Color.white, Color.white, Color.white, Color.white });
            _whiteTex.Apply();
            _whiteSprite = Sprite.Create(_whiteTex, new Rect(0, 0, 2, 2), new Vector2(0.5f, 0.5f));

            // 2. Rounded panel texture (64x64, 16px corner radius)
            int sz = 64;
            _panelBgTex = new Texture2D(sz, sz, TextureFormat.RGBA32, false);
            for (int y = 0; y < sz; y++)
            {
                for (int x = 0; x < sz; x++)
                {
                    float dx = Mathf.Min(x, sz - 1 - x);
                    float dy = Mathf.Min(y, sz - 1 - y);
                    float radius = 16f;
                    float dist = 0f;
                    if (dx < radius && dy < radius)
                    {
                        dist = Vector2.Distance(new Vector2(dx, dy), new Vector2(radius, radius)) - radius;
                    }
                    float alpha = Mathf.Clamp01(1f - dist);
                    _panelBgTex.SetPixel(x, y, new Color(ColPanel.r, ColPanel.g, ColPanel.b, ColPanel.a * alpha));
                }
            }
            _panelBgTex.Apply();
            _panelSprite = Sprite.Create(_panelBgTex, new Rect(0, 0, sz, sz), new Vector2(0.5f, 0.5f), 100, 0, SpriteMeshType.FullRect, new Vector4(16, 16, 16, 16));

            // 3. Button texture (64x64, 14px corner radius)
            _btnBgTex = new Texture2D(sz, sz, TextureFormat.RGBA32, false);
            for (int y = 0; y < sz; y++)
            {
                for (int x = 0; x < sz; x++)
                {
                    float dx = Mathf.Min(x, sz - 1 - x);
                    float dy = Mathf.Min(y, sz - 1 - y);
                    float radius = 14f;
                    float dist = 0f;
                    if (dx < radius && dy < radius)
                    {
                        dist = Vector2.Distance(new Vector2(dx, dy), new Vector2(radius, radius)) - radius;
                    }
                    float alpha = Mathf.Clamp01(1f - dist);
                    _btnBgTex.SetPixel(x, y, new Color(0.12f, 0.16f, 0.22f, 0.95f * alpha));
                }
            }
            _btnBgTex.Apply();
            _btnSprite = Sprite.Create(_btnBgTex, new Rect(0, 0, sz, sz), new Vector2(0.5f, 0.5f), 100, 0, SpriteMeshType.FullRect, new Vector4(14, 14, 14, 14));

            // 4. Antialiased Solid Circle Texture (64x64)
            int csz = 64;
            _circleTex = new Texture2D(csz, csz, TextureFormat.RGBA32, false);
            float cCenter = (csz - 1) * 0.5f;
            float cRadius = cCenter - 1f;
            for (int y = 0; y < csz; y++)
            {
                for (int x = 0; x < csz; x++)
                {
                    float d = Vector2.Distance(new Vector2(x, y), new Vector2(cCenter, cCenter));
                    float alpha = Mathf.Clamp01(cRadius - d + 0.5f);
                    _circleTex.SetPixel(x, y, new Color(1f, 1f, 1f, alpha));
                }
            }
            _circleTex.Apply();
            _circleSprite = Sprite.Create(_circleTex, new Rect(0, 0, csz, csz), new Vector2(0.5f, 0.5f));

            // 5. Hollow Steering Rim Ring Texture (128x128) - 100% Transparent interior so 3D world shows through
            int rsz = 128;
            _rimRingTex = new Texture2D(rsz, rsz, TextureFormat.RGBA32, false);
            float rcCenter = (rsz - 1) * 0.5f;
            float rOuter = rcCenter - 1f;
            float rInner = rOuter - 22f; // ~22px rim grip thickness

            for (int y = 0; y < rsz; y++)
            {
                for (int x = 0; x < rsz; x++)
                {
                    float d = Vector2.Distance(new Vector2(x, y), new Vector2(rcCenter, rcCenter));
                    float alphaOuter = Mathf.Clamp01(rOuter - d + 0.5f);
                    float alphaInner = Mathf.Clamp01(d - rInner + 0.5f);
                    float ringAlpha = Mathf.Min(alphaOuter, alphaInner);

                    if (ringAlpha > 0.001f)
                    {
                        // Subtle automotive lighting gradient on rim surface
                        float light = Mathf.Lerp(0.85f, 1.20f, (float)y / rsz);
                        _rimRingTex.SetPixel(x, y, new Color(0.11f * light, 0.15f * light, 0.22f * light, ringAlpha * 0.98f));
                    }
                    else
                    {
                        // Completely transparent interior
                        _rimRingTex.SetPixel(x, y, new Color(0f, 0f, 0f, 0f));
                    }
                }
            }
            _rimRingTex.Apply();
            _rimRingSprite = Sprite.Create(_rimRingTex, new Rect(0, 0, rsz, rsz), new Vector2(0.5f, 0.5f));

            // 6. Metallic Pedal Rib Texture (64x16)
            int rw = 64, rh = 16;
            _pedalRibTex = new Texture2D(rw, rh, TextureFormat.RGBA32, false);
            for (int y = 0; y < rh; y++)
            {
                float yNorm = (float)y / (rh - 1);
                float brightness = Mathf.Sin(yNorm * Mathf.PI);
                for (int x = 0; x < rw; x++)
                {
                    float dx = Mathf.Min(x, rw - 1 - x);
                    float endAlpha = Mathf.Clamp01(dx / 4f);
                    float val = Mathf.Lerp(0.12f, 0.45f, brightness);
                    _pedalRibTex.SetPixel(x, y, new Color(val, val * 1.05f, val * 1.15f, endAlpha));
                }
            }
            _pedalRibTex.Apply();
            _pedalRibSprite = Sprite.Create(_pedalRibTex, new Rect(0, 0, rw, rh), new Vector2(0.5f, 0.5f), 100, 0, SpriteMeshType.FullRect, new Vector4(8, 4, 8, 4));

            // 7. Glowing Amber Top Marker Pill (16x32)
            int mw = 16, mh = 32;
            _topMarkerTex = new Texture2D(mw, mh, TextureFormat.RGBA32, false);
            for (int y = 0; y < mh; y++)
            {
                for (int x = 0; x < mw; x++)
                {
                    float dx = Mathf.Min(x, mw - 1 - x);
                    float dy = Mathf.Min(y, mh - 1 - y);
                    float rad = 6f;
                    float d = 0f;
                    if (dx < rad && dy < rad) d = Vector2.Distance(new Vector2(dx, dy), new Vector2(rad, rad)) - rad;
                    float alpha = Mathf.Clamp01(1f - d);
                    _topMarkerTex.SetPixel(x, y, new Color(1f, 1f, 1f, alpha));
                }
            }
            _topMarkerTex.Apply();
            _topMarkerSprite = Sprite.Create(_topMarkerTex, new Rect(0, 0, mw, mh), new Vector2(0.5f, 0.5f));

            // 8. Glowing Amber Active Gear Button Texture (64x64)
            _amberGlowTex = new Texture2D(sz, sz, TextureFormat.RGBA32, false);
            for (int y = 0; y < sz; y++)
            {
                for (int x = 0; x < sz; x++)
                {
                    float dx = Mathf.Min(x, sz - 1 - x);
                    float dy = Mathf.Min(y, sz - 1 - y);
                    float radius = 14f;
                    float dist = 0f;
                    if (dx < radius && dy < radius)
                    {
                        dist = Vector2.Distance(new Vector2(dx, dy), new Vector2(radius, radius)) - radius;
                    }
                    float alpha = Mathf.Clamp01(1f - dist);
                    _amberGlowTex.SetPixel(x, y, new Color(ColAmber.r, ColAmber.g, ColAmber.b, alpha));
                }
            }
            _amberGlowTex.Apply();
            _amberGlowSprite = Sprite.Create(_amberGlowTex, new Rect(0, 0, sz, sz), new Vector2(0.5f, 0.5f), 100, 0, SpriteMeshType.FullRect, new Vector4(14, 14, 14, 14));
        }

        private void BuildUIHierarchy()
        {
            // Root Canvas
            var canvasGo = new GameObject("HUD_Canvas");
            _canvas = canvasGo.AddComponent<Canvas>();
            _canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            _canvas.sortingOrder = 100;

            _scaler = canvasGo.AddComponent<CanvasScaler>();
            _scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            // 1600x900 mobile simulator reference resolution yields comfortable, bold finger touch targets
            _scaler.referenceResolution = new Vector2(1600, 900);
            _scaler.screenMatchMode = CanvasScaler.ScreenMatchMode.MatchWidthOrHeight;
            _scaler.matchWidthOrHeight = 1.0f;

            canvasGo.AddComponent<GraphicRaycaster>();

            // Safe Area Root
            var safeGo = new GameObject("SafeAreaContainer");
            safeGo.transform.SetParent(canvasGo.transform, false);
            _safeAreaContainer = safeGo.AddComponent<RectTransform>();
            _safeAreaContainer.anchorMin = Vector2.zero;
            _safeAreaContainer.anchorMax = Vector2.one;
            _safeAreaContainer.offsetMin = Vector2.zero;
            _safeAreaContainer.offsetMax = Vector2.zero;
            safeGo.AddComponent<UISafeAreaFitter>();

            // Fullscreen transparent orbit touch zone behind all interactive HUD elements
            var orbitRect = CreateEmptyUI("CameraOrbitTouchZone", _safeAreaContainer, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero, new Vector2(0.5f, 0.5f));
            var orbitImg = orbitRect.gameObject.AddComponent<Image>();
            orbitImg.color = Color.clear;
            orbitImg.raycastTarget = true;
            _orbitZone = orbitRect.gameObject.AddComponent<UICameraOrbitZone>();
            _orbitZone.cameraController = cameraController;

            // Build Modules with non-overlapping, edge-anchored coordinates
            BuildTopBar(_safeAreaContainer);
            BuildLeftControls(_safeAreaContainer);
            BuildRightControls(_safeAreaContainer);
            BuildDashboardCluster(_safeAreaContainer);
            BuildPauseModal(canvasGo.transform);
        }

        private void BuildTopBar(RectTransform parent)
        {
            var topBar = CreateEmptyUI("TopBar", parent, new Vector2(0f, 1f), new Vector2(1f, 1f), new Vector2(0f, 0f), new Vector2(0f, 96f), new Vector2(0.5f, 1f));

            // 1. Pause Button (Top-Left corner) - Size 72x72 px
            _pauseBtn = CreateIconButton("Btn_Pause", topBar, new Vector2(20f, -14f), new Vector2(72f, 72f), "||", 32, ColTextMuted, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(0f, 1f));
            _pauseBtn.onClick.AddListener(OnPauseClicked);

            // 2. Systems Capsule: BEAM, WIPER, HAZARD (Positioned to the right of Pause button with comfortable margin)
            // Pause right edge is 20 + 72 = 92px; Systems starts at 104px (12px clean margin)
            var systemsGroup = CreatePanel("SystemsGroup", topBar, new Vector2(104f, -14f), new Vector2(280f, 72f), ColPanel, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(0f, 1f));
            
            _beamBtn = CreateIconButton("Btn_Beam", systemsGroup.transform, new Vector2(8f, -8f), new Vector2(86f, 56f), "BEAM", 13, ColAmber, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(0f, 1f));
            _beamLabel = _beamBtn.GetComponentInChildren<Text>();
            _beamBtn.onClick.AddListener(OnBeamClicked);

            _wiperBtn = CreateIconButton("Btn_Wiper", systemsGroup.transform, new Vector2(98f, -8f), new Vector2(86f, 56f), "WIPER", 13, ColTextMuted, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(0f, 1f));
            _wiperLabel = _wiperBtn.GetComponentInChildren<Text>();
            _wiperBtn.onClick.AddListener(OnWiperClicked);

            _hazardBtn = CreateIconButton("Btn_Hazard", systemsGroup.transform, new Vector2(188f, -8f), new Vector2(84f, 56f), "HAZARD", 12, ColAmber, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(0f, 1f));
            _hazardLabel = _hazardBtn.GetComponentInChildren<Text>();
            _hazardBtn.onClick.AddListener(OnHazardClicked);

            // 3. Audio / Radio Capsule (Positioned next to SystemsGroup)
            var audioCapsule = CreatePanel("AudioCapsule", topBar, new Vector2(396f, -14f), new Vector2(220f, 72f), ColPanel, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(0f, 1f));
            var audioIcon = CreateText("AudioIcon", audioCapsule.transform, new Vector2(-75f, 0f), new Vector2(38f, 38f), "♪", 22, FontStyle.Bold, ColAmber);
            audioIcon.alignment = TextAnchor.MiddleCenter;
            var audioTitle = CreateText("AudioTitle", audioCapsule.transform, new Vector2(22f, 0f), new Vector2(145f, 44f), "AUDIO\nKERALA BUS FM", 11, FontStyle.Bold, Color.white);
            audioTitle.alignment = TextAnchor.MiddleLeft;

            // 4. Top-Center Status Capsule (SIM LINK 60 FPS | 16:45 | PASSENGERS 42/48) - 460x50 px
            var statusCapsule = CreatePanel("StatusCapsule", topBar, new Vector2(0f, -14f), new Vector2(460f, 50f), ColPanel, new Vector2(0.5f, 1f), new Vector2(0.5f, 1f), new Vector2(0.5f, 1f));
            _statusText = CreateText("StatusText", statusCapsule.transform, Vector2.zero, new Vector2(450f, 44f), "WALLET: ₹10,000   |   60 FPS   |   PASSENGERS 42 / 48", 13, FontStyle.Bold, new Color(0.85f, 0.92f, 1.0f));
            _statusText.alignment = TextAnchor.MiddleCenter;

            // 5. Camera Controls (Top-Right corner)
            var camGroup = CreatePanel("CamGroup", topBar, new Vector2(-260f, -14f), new Vector2(212f, 72f), ColPanel, new Vector2(1f, 1f), new Vector2(1f, 1f), new Vector2(1f, 1f));
            
            _camCycleBtn = CreateIconButton("Btn_CamCycle", camGroup.transform, new Vector2(8f, -8f), new Vector2(92f, 56f), "CAM 1/3", 12, ColAmber, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(0f, 1f));
            _camCycleLabel = _camCycleBtn.GetComponentInChildren<Text>();
            _camCycleBtn.onClick.AddListener(OnCamCycleClicked);

            _camCenterBtn = CreateIconButton("Btn_CamCenter", camGroup.transform, new Vector2(108f, -8f), new Vector2(92f, 56f), "CENTER", 11, ColAmber, new Vector2(0f, 1f), new Vector2(0f, 1f), new Vector2(0f, 1f));
            _camCenterBtn.onClick.AddListener(() =>
            {
                if (cameraController != null)
                {
                    cameraController.ResetCockpitView();
                    UpdateCameraVisuals();
                }
            });
            _camCenterBtn.gameObject.SetActive(false);

            // 6. Radar Minimap Box (Top-Right flank, matching reference) - 230x150 px
            var radarBox = CreatePanel("RadarMinimap", topBar, new Vector2(-20f, -14f), new Vector2(230f, 150f), ColPanel, new Vector2(1f, 1f), new Vector2(1f, 1f), new Vector2(1f, 1f));
            var radarHeader = CreateText("RadarHeader", radarBox.transform, new Vector2(0f, 52f), new Vector2(210f, 24f), "● TERMINAL  1.8 KM", 12, FontStyle.Bold, ColAmber);
            radarHeader.alignment = TextAnchor.MiddleLeft;

            var radarSub = CreateText("RadarCompass", radarBox.transform, new Vector2(0f, -52f), new Vector2(210f, 22f), "N 108°", 11, FontStyle.Bold, ColTextMuted);
            radarSub.alignment = TextAnchor.MiddleRight;
        }

        private void BuildLeftControls(RectTransform parent)
        {
            // Anchored strictly to bottom-left with bottom-left pivot (0,0) - Size 420x500 px
            var leftGroup = CreateEmptyUI("LeftControls", parent, new Vector2(0f, 0f), new Vector2(0f, 0f), new Vector2(20f, 20f), new Vector2(420f, 500f), new Vector2(0f, 0f));

            // Turn Indicator Left (Upper section of left controls) - Size 86x72 px
            _turnLeftBtn = CreateIconButton("Btn_TurnLeft", leftGroup, new Vector2(20f, 430f), new Vector2(86f, 72f), "◀", 38, ColAmber, new Vector2(0f, 0f), new Vector2(0f, 0f), new Vector2(0f, 0f));
            _turnLeftIcon = _turnLeftBtn.GetComponent<Image>();
            _turnLeftBtn.onClick.AddListener(() => { if (busController != null) busController.ToggleTurnLeft(); });

            // Steering Wheel Container - Diameter 380 px
            _wheelContainer = new GameObject("SteeringWheelModule");
            _wheelContainer.transform.SetParent(leftGroup, false);
            var wheelRect = _wheelContainer.AddComponent<RectTransform>();
            wheelRect.anchorMin = new Vector2(0f, 0f);
            wheelRect.anchorMax = new Vector2(0f, 0f);
            wheelRect.pivot = new Vector2(0f, 0f);
            wheelRect.anchoredPosition = new Vector2(20f, 20f);
            wheelRect.sizeDelta = new Vector2(380f, 380f);

            // Wheel Graphic (Rotating root: hollow circular rim, "plus" spokes, and top center indicator)
            var rimGo = new GameObject("WheelRim");
            rimGo.transform.SetParent(_wheelContainer.transform, false);
            var rimRect = rimGo.AddComponent<RectTransform>();
            rimRect.anchorMin = new Vector2(0.5f, 0.5f);
            rimRect.anchorMax = new Vector2(0.5f, 0.5f);
            rimRect.pivot = new Vector2(0.5f, 0.5f);
            rimRect.anchoredPosition = Vector2.zero;
            rimRect.sizeDelta = new Vector2(370f, 370f);

            // Hollow Rim Ring Graphic - Interior is 100% transparent so 3D road shows through
            var rimImg = rimGo.AddComponent<Image>();
            rimImg.sprite = _rimRingSprite;
            rimImg.color = Color.white;

            // 3-Spoke Assembly (matching reference: Left, Right, and Bottom spokes centered inside rim forming the "plus" / T-shape)
            CreatePanel("Spoke_Left", rimGo.transform, new Vector2(-80f, 0f), new Vector2(100f, 32f), new Color(0.14f, 0.18f, 0.25f, 1f));
            CreatePanel("Spoke_Right", rimGo.transform, new Vector2(80f, 0f), new Vector2(100f, 32f), new Color(0.14f, 0.18f, 0.25f, 1f));
            CreatePanel("Spoke_Bottom", rimGo.transform, new Vector2(0f, -80f), new Vector2(32f, 100f), new Color(0.14f, 0.18f, 0.25f, 1f));

            // Top Center Amber Marker Tick (matching reference)
            var topMarkerGo = new GameObject("TopCenterMarker");
            topMarkerGo.transform.SetParent(rimGo.transform, false);
            var topMarkerRect = topMarkerGo.AddComponent<RectTransform>();
            topMarkerRect.anchorMin = new Vector2(0.5f, 0.5f);
            topMarkerRect.anchorMax = new Vector2(0.5f, 0.5f);
            topMarkerRect.pivot = new Vector2(0.5f, 0.5f);
            topMarkerRect.anchoredPosition = new Vector2(0f, 170f);
            topMarkerRect.sizeDelta = new Vector2(16f, 32f);
            var topMarkerImg = topMarkerGo.AddComponent<Image>();
            topMarkerImg.sprite = _topMarkerSprite;
            topMarkerImg.color = ColAmberGlow;

            _steeringWheel = _wheelContainer.AddComponent<UISteeringWheel>();
            _steeringWheel.wheelGraphic = rimRect;

            // Center Horn Hub & Air-Horn Emblem - Size 140x140 px
            var hornGo = new GameObject("Btn_Horn");
            hornGo.transform.SetParent(_wheelContainer.transform, false);
            var hornRect = hornGo.AddComponent<RectTransform>();
            hornRect.anchorMin = new Vector2(0.5f, 0.5f);
            hornRect.anchorMax = new Vector2(0.5f, 0.5f);
            hornRect.pivot = new Vector2(0.5f, 0.5f);
            hornRect.anchoredPosition = Vector2.zero;
            hornRect.sizeDelta = new Vector2(140f, 140f);
            var hornImg = hornGo.AddComponent<Image>();
            hornImg.sprite = _circleSprite;
            hornImg.color = new Color(0.12f, 0.16f, 0.22f, 1f);

            _hornButton = hornGo.AddComponent<UIHoldButton>();
            _hornButton.targetGraphic = hornImg;
            _hornButton.normalColor = new Color(0.12f, 0.16f, 0.22f, 1f);
            _hornButton.pressedColor = ColAmber;
            _hornButton.keyboardShortcut = KeyCode.H;

            var hornBadge = CreatePanel("HornBadge", hornGo.transform, new Vector2(0f, 18f), new Vector2(46f, 46f), new Color(0.96f, 0.62f, 0.04f, 0.2f));
            hornBadge.GetComponent<Image>().sprite = _circleSprite;
            var hornIcon = CreateText("HornIcon", hornBadge.transform, Vector2.zero, new Vector2(46f, 46f), "▲", 22, FontStyle.Bold, ColAmber);
            hornIcon.alignment = TextAnchor.MiddleCenter;

            var hornLabel = CreateText("HornLabel", hornGo.transform, new Vector2(0f, -18f), new Vector2(125f, 34f), "HORN\nAIR BLAST", 11, FontStyle.Bold, Color.white);
            hornLabel.alignment = TextAnchor.MiddleCenter;

            // Alternative Steer Buttons Container (Hidden by default unless toggled)
            _buttonsSteerContainer = new GameObject("SteerButtonsModule");
            _buttonsSteerContainer.transform.SetParent(leftGroup, false);
            var btnSteerRect = _buttonsSteerContainer.AddComponent<RectTransform>();
            btnSteerRect.anchorMin = new Vector2(0f, 0f);
            btnSteerRect.anchorMax = new Vector2(0f, 0f);
            btnSteerRect.pivot = new Vector2(0.5f, 0.5f);
            btnSteerRect.anchoredPosition = new Vector2(210f, 210f);
            btnSteerRect.sizeDelta = new Vector2(360f, 140f);
            _buttonsSteerContainer.SetActive(!useSteeringWheel);

            _leftSteerBtn = CreateHoldButton("Btn_SteerLeft", _buttonsSteerContainer.transform, new Vector2(-90f, 0f), new Vector2(155f, 125f), "◀", 50, KeyCode.A);
            _rightSteerBtn = CreateHoldButton("Btn_SteerRight", _buttonsSteerContainer.transform, new Vector2(90f, 0f), new Vector2(155f, 125f), "▶", 50, KeyCode.D);
        }

        private void BuildRightControls(RectTransform parent)
        {
            // Anchored strictly to bottom-right with bottom-right pivot (1,0) - Size 380x500 px
            var rightGroup = CreateEmptyUI("RightControls", parent, new Vector2(1f, 0f), new Vector2(1f, 0f), new Vector2(-20f, 20f), new Vector2(380f, 500f), new Vector2(1f, 0f));

            // Turn Indicator Right - Size 86x72 px
            _turnRightBtn = CreateIconButton("Btn_TurnRight", rightGroup, new Vector2(-20f, 460f), new Vector2(86f, 72f), "▶", 38, ColAmber, new Vector2(1f, 0f), new Vector2(1f, 0f), new Vector2(1f, 0f));
            _turnRightIcon = _turnRightBtn.GetComponent<Image>();
            _turnRightBtn.onClick.AddListener(() => { if (busController != null) busController.ToggleTurnRight(); });

            // Dedicated Park Brake Button (Positioned at Y = 350f, well above the pedals) - Size 92x92 px
            _parkBrakeBtn = CreateIconButton("Btn_ParkBrake", rightGroup, new Vector2(-20f, 350f), new Vector2(92f, 92f), "P\nPARK BRAKE", 11, ColRed, new Vector2(1f, 0f), new Vector2(1f, 0f), new Vector2(1f, 0f));
            _parkBrakeIcon = _parkBrakeBtn.GetComponent<Image>();
            _parkBrakeBtn.onClick.AddListener(OnParkBrakeClicked);

            // Pedals Group: Brake (Left) and Gas (Right) - Size 320x330 px
            var pedalsGroup = CreateEmptyUI("PedalsGroup", rightGroup, new Vector2(1f, 0f), new Vector2(1f, 0f), new Vector2(0f, 0f), new Vector2(320f, 330f), new Vector2(1f, 0f));

            // Brake Pedal (Air Retarder - Red Accent) - Size 135x270 px
            _brakePedal = CreateHoldButton("Pedal_Brake", pedalsGroup, new Vector2(-165f, 0f), new Vector2(135f, 270f), string.Empty, 12, KeyCode.S, new Color(0.18f, 0.09f, 0.11f, 0.95f), new Vector2(1f, 0f), new Vector2(1f, 0f), new Vector2(1f, 0f));
            
            // Top badge on Brake Pedal
            var brakeBadge = CreatePanel("BrakeBadge", _brakePedal.transform, new Vector2(0f, 92f), new Vector2(44f, 44f), new Color(0.92f, 0.28f, 0.28f, 0.25f));
            brakeBadge.GetComponent<Image>().sprite = _circleSprite;
            var brakeBadgeIcon = CreateText("BrakeBadgeIcon", brakeBadge.transform, Vector2.zero, new Vector2(44f, 44f), "||", 22, FontStyle.Bold, ColRed);
            brakeBadgeIcon.alignment = TextAnchor.MiddleCenter;

            // 4 Horizontal Raised Metallic Grip Ribs on Brake Pedal (centered horizontally)
            float[] brakeRibY = new[] { 36f, 8f, -20f, -48f };
            for (int i = 0; i < brakeRibY.Length; i++)
            {
                var rib = CreatePanel($"Rib_{i}", _brakePedal.transform, new Vector2(0f, brakeRibY[i]), new Vector2(102f, 14f), Color.white);
                rib.GetComponent<Image>().sprite = _pedalRibSprite;
            }

            var brakeLabel = CreateText("BrakeLabel", _brakePedal.transform, new Vector2(0f, -100f), new Vector2(125f, 36f), "BRAKE\nAIR RETARDER", 12, FontStyle.Bold, ColRed);
            brakeLabel.alignment = TextAnchor.MiddleCenter;

            // Gas Pedal (Throttle - Emerald Green Accent) - Size 125x300 px
            _gasPedal = CreateHoldButton("Pedal_Gas", pedalsGroup, new Vector2(-10f, 0f), new Vector2(125f, 300f), string.Empty, 12, KeyCode.W, new Color(0.09f, 0.17f, 0.13f, 0.95f), new Vector2(1f, 0f), new Vector2(1f, 0f), new Vector2(1f, 0f));

            // Top badge on Gas Pedal
            var gasBadge = CreatePanel("GasBadge", _gasPedal.transform, new Vector2(0f, 106f), new Vector2(44f, 44f), new Color(0.20f, 0.82f, 0.45f, 0.25f));
            gasBadge.GetComponent<Image>().sprite = _circleSprite;
            var gasBadgeIcon = CreateText("GasBadgeIcon", gasBadge.transform, Vector2.zero, new Vector2(44f, 44f), "▲", 22, FontStyle.Bold, ColGreen);
            gasBadgeIcon.alignment = TextAnchor.MiddleCenter;

            // 5 Horizontal Raised Metallic Grip Ribs on Gas Pedal (centered horizontally)
            float[] gasRibY = new[] { 54f, 26f, -2f, -30f, -58f };
            for (int i = 0; i < gasRibY.Length; i++)
            {
                var rib = CreatePanel($"Rib_{i}", _gasPedal.transform, new Vector2(0f, gasRibY[i]), new Vector2(94f, 14f), Color.white);
                rib.GetComponent<Image>().sprite = _pedalRibSprite;
            }

            var gasLabel = CreateText("GasLabel", _gasPedal.transform, new Vector2(0f, -112f), new Vector2(115f, 36f), "GAS\nTHROTTLE", 12, FontStyle.Bold, ColGreen);
            gasLabel.alignment = TextAnchor.MiddleCenter;
        }

        private void BuildDashboardCluster(RectTransform parent)
        {
            // Anchored strictly to bottom-center with bottom-center pivot (0.5, 0) - Size 620x210 px
            var dashGroup = CreateEmptyUI("DashboardCluster", parent, new Vector2(0.5f, 0f), new Vector2(0.5f, 0f), new Vector2(0f, 16f), new Vector2(620f, 210f), new Vector2(0.5f, 0f));
            _dashGroup = dashGroup;

            // 1. Sleek Modern Dashboard Capsule (Speedometer + Fuel + Air Brake Telemetry) - Size 580x92 px
            var speedPanel = CreatePanel("SpeedometerPanel", dashGroup, new Vector2(0f, 8f), new Vector2(580f, 92f), ColPanel, new Vector2(0.5f, 0f), new Vector2(0.5f, 0f), new Vector2(0.5f, 0f));
            _speedPanelRect = speedPanel.GetComponent<RectTransform>();

            // Fuel telemetry on left flank of capsule
            var fuelSection = CreateEmptyUI("FuelSection", speedPanel.transform, new Vector2(0.5f, 0.5f), new Vector2(0.5f, 0.5f), new Vector2(-215f, 0f), new Vector2(125f, 66f), new Vector2(0.5f, 0.5f));
            var fuelLabel = CreateText("FuelLabel", fuelSection, Vector2.zero, new Vector2(125f, 62f), "DIESEL\n82%   142L", 12, FontStyle.Bold, new Color(0.80f, 0.88f, 0.98f));
            fuelLabel.alignment = TextAnchor.MiddleCenter;

            // Left Divider
            CreatePanel("DivLeft", speedPanel.transform, new Vector2(-145f, 0f), new Vector2(1f, 56f), new Color(1f, 1f, 1f, 0.15f));

            // Center Speedometer: Digital readout + KM/H + Speed Bar
            _speedValueText = CreateText("SpeedValue", speedPanel.transform, new Vector2(0f, 10f), new Vector2(150f, 58f), "000", 52, FontStyle.Bold, Color.white);
            _speedValueText.alignment = TextAnchor.MiddleCenter;

            var kmhLabel = CreateText("KmhLabel", speedPanel.transform, new Vector2(86f, 8f), new Vector2(58f, 26f), "KM/H", 14, FontStyle.Bold, ColAmber);
            kmhLabel.alignment = TextAnchor.MiddleLeft;

            // Speed Progress Bar
            var barBg = CreatePanel("SpeedBarBg", speedPanel.transform, new Vector2(0f, -30f), new Vector2(200f, 8f), new Color(1f, 1f, 1f, 0.15f));
            var barFillGo = new GameObject("SpeedBarFill");
            barFillGo.transform.SetParent(barBg.transform, false);
            var barFillRect = barFillGo.AddComponent<RectTransform>();
            barFillRect.anchorMin = new Vector2(0.5f, 0.5f);
            barFillRect.anchorMax = new Vector2(0.5f, 0.5f);
            barFillRect.pivot = new Vector2(0.5f, 0.5f);
            barFillRect.anchoredPosition = Vector2.zero;
            barFillRect.sizeDelta = new Vector2(200f, 8f);
            _speedBarFill = barFillGo.AddComponent<Image>();
            _speedBarFill.sprite = _whiteSprite;
            _speedBarFill.type = Image.Type.Filled;
            _speedBarFill.fillMethod = Image.FillMethod.Horizontal;
            _speedBarFill.fillOrigin = 0;
            _speedBarFill.fillAmount = 0f;
            _speedBarFill.color = ColAmber;

            // Right Divider
            CreatePanel("DivRight", speedPanel.transform, new Vector2(145f, 0f), new Vector2(1f, 56f), new Color(1f, 1f, 1f, 0.15f));

            // Air Brake telemetry on right flank of capsule
            var airSection = CreateEmptyUI("AirSection", speedPanel.transform, new Vector2(0.5f, 0.5f), new Vector2(0.5f, 0.5f), new Vector2(215f, 0f), new Vector2(125f, 66f), new Vector2(0.5f, 0.5f));
            var airLabel = CreateText("AirLabel", airSection, Vector2.zero, new Vector2(125f, 62f), "AIR BRAKE\n8.6 BAR   OPTIMAL", 12, FontStyle.Bold, ColGreen);
            airLabel.alignment = TextAnchor.MiddleCenter;

            // 2. Gear Selector Row [ P ] [ R ] [ N ] [ D ] (Placed directly above the capsule at Y = 110f) - Size 330x60 px
            var gearBar = CreatePanel("GearSelectorBar", dashGroup, new Vector2(0f, 110f), new Vector2(330f, 60f), ColPanel, new Vector2(0.5f, 0f), new Vector2(0.5f, 0f), new Vector2(0.5f, 0f));
            _gearButtonsGroup = gearBar.GetComponent<RectTransform>();

            string[] gearNames = { "P", "R", "N", "D" };
            TransmissionGear[] gears = { TransmissionGear.Park, TransmissionGear.Reverse, TransmissionGear.Neutral, TransmissionGear.Drive };
            _gearButtons = new Button[4];
            _gearLabels = new Text[4];

            for (int i = 0; i < 4; i++)
            {
                int index = i;
                float xPos = -111f + i * 74f;
                var gBtn = CreateIconButton($"Btn_Gear_{gearNames[i]}", gearBar.transform, new Vector2(xPos, 0f), new Vector2(64f, 48f), gearNames[i], 18, ColTextMuted);
                _gearButtons[i] = gBtn;
                _gearLabels[i] = gBtn.GetComponentInChildren<Text>();
                gBtn.onClick.AddListener(() => OnGearButtonClicked(gears[index]));
            }

            // 3. Notification Text (Safe shift warnings) - Positioned cleanly above gear selector
            _notificationText = CreateText("NotificationText", dashGroup, new Vector2(0f, 180f), new Vector2(500f, 28f), string.Empty, 14, FontStyle.Bold, ColAmber);
            _notificationText.alignment = TextAnchor.MiddleCenter;
        }

        private void BuildPauseModal(Transform parent)
        {
            _pauseModal = new GameObject("PauseModal");
            _pauseModal.transform.SetParent(parent, false);
            var modalRect = _pauseModal.AddComponent<RectTransform>();
            modalRect.anchorMin = Vector2.zero;
            modalRect.anchorMax = Vector2.one;
            modalRect.sizeDelta = Vector2.zero;

            // Dim backdrop
            var bgImg = _pauseModal.AddComponent<Image>();
            bgImg.sprite = _whiteSprite;
            bgImg.color = new Color(0f, 0f, 0f, 0.75f);

            // Dialog Window
            var dialog = CreatePanel("PauseDialog", modalRect, Vector2.zero, new Vector2(480f, 360f), ColBgDark, new Vector2(0.5f, 0.5f), new Vector2(0.5f, 0.5f), new Vector2(0.5f, 0.5f));

            var title = CreateText("PauseTitle", dialog.transform, new Vector2(0f, 120f), new Vector2(360f, 46f), "SIMULATION PAUSED", 22, FontStyle.Bold, ColAmber);
            title.alignment = TextAnchor.MiddleCenter;

            // Resume Button
            var resumeBtn = CreateIconButton("Btn_Resume", dialog.transform, new Vector2(0f, 48f), new Vector2(300f, 56f), "RESUME", 18, ColGreen);
            resumeBtn.onClick.AddListener(OnResumeClicked);

            // Reset Bus Button
            var resetBtn = CreateIconButton("Btn_Reset", dialog.transform, new Vector2(0f, -22f), new Vector2(300f, 56f), "RESET BUS TO TRACK (R)", 14, ColAmber);
            resetBtn.onClick.AddListener(() =>
            {
                if (busController != null) busController.ResetBus();
                OnResumeClicked();
            });

            // Control Style Toggle Button
            var steerToggleBtn = CreateIconButton("Btn_ToggleSteer", dialog.transform, new Vector2(0f, -92f), new Vector2(300f, 52f), "STEERING: WHEEL", 13, Color.white);
            steerToggleBtn.onClick.AddListener(() =>
            {
                useSteeringWheel = !useSteeringWheel;
                if (_wheelContainer != null) _wheelContainer.SetActive(useSteeringWheel);
                if (_buttonsSteerContainer != null) _buttonsSteerContainer.SetActive(!useSteeringWheel);
                steerToggleBtn.GetComponentInChildren<Text>().text = useSteeringWheel ? "STEERING: WHEEL" : "STEERING: ARROWS";
            });

            _pauseModal.SetActive(false);
        }

        // ==========================================
        // USER ACTIONS & DATA BINDING
        // ==========================================

        private void OnGearButtonClicked(TransmissionGear targetGear)
        {
            if (busController == null) return;

            if (!busController.TrySetGear(targetGear, out string failReason))
            {
                ShowNotification(failReason);
            }
            else
            {
                UpdateGearVisuals();
            }
        }

        private void ShowNotification(string msg)
        {
            if (_notificationText != null)
            {
                _notificationText.text = msg;
                _notificationTimer = 2.5f;
            }
        }

        private void UpdateGearVisuals()
        {
            if (busController == null || _gearButtons == null) return;

            TransmissionGear active = busController.gearMode;
            for (int i = 0; i < 4; i++)
            {
                TransmissionGear g = (TransmissionGear)i;
                bool isCur = (g == active);
                var img = _gearButtons[i].GetComponent<Image>();
                if (img != null)
                {
                    img.sprite = isCur ? _amberGlowSprite : _btnSprite;
                    img.color = isCur ? Color.white : new Color(0.12f, 0.16f, 0.22f, 0.95f);
                }
                if (_gearLabels[i] != null)
                {
                    _gearLabels[i].color = isCur ? Color.black : ColTextMuted;
                    _gearLabels[i].fontSize = isCur ? 20 : 18;
                }
            }
        }

        private void OnBeamClicked()
        {
            if (busController != null)
            {
                busController.ToggleHeadlights();
                UpdateSystemsVisuals();
            }
        }

        private void OnWiperClicked()
        {
            if (busController != null)
            {
                busController.ToggleWipers();
                UpdateSystemsVisuals();
            }
        }

        private void OnHazardClicked()
        {
            if (busController != null)
            {
                busController.ToggleHazards();
                UpdateSystemsVisuals();
            }
        }

        private void OnParkBrakeClicked()
        {
            if (busController != null)
            {
                if (busController.gearMode == TransmissionGear.Park)
                {
                    // Shifting out of Park requires pressing brake
                    if (busController.TrySetGear(TransmissionGear.Neutral, out string reason))
                    {
                        ShowNotification("Park Brake Disengaged (Neutral)");
                    }
                    else
                    {
                        ShowNotification(reason);
                    }
                }
                else
                {
                    // Engaging park brake shifts to Park
                    if (busController.TrySetGear(TransmissionGear.Park, out string reason))
                    {
                        ShowNotification("Park Brake Engaged");
                    }
                    else
                    {
                        ShowNotification(reason);
                    }
                }
                UpdateSystemsVisuals();
            }
        }

        private void OnCamCycleClicked()
        {
            if (cameraController != null)
            {
                cameraController.CycleCamera();
                UpdateCameraVisuals();
            }
        }

        private void UpdateCameraVisuals()
        {
            if (_camCycleLabel != null && cameraController != null)
            {
                _camCycleLabel.text = $"CAM {cameraController.CurrentPresetIndex + 1}/{cameraController.TotalPresets}";
            }

            if (_camCenterBtn != null && cameraController != null)
            {
                bool showCenter = cameraController.CurrentMode == BusCameraController.CameraMode.DriverCockpit && cameraController.IsCockpitLookOffset;
                if (_camCenterBtn.gameObject.activeSelf != showCenter)
                {
                    _camCenterBtn.gameObject.SetActive(showCenter);
                }
            }

            // In Cam 1 (Driver Cockpit), hide digital speedometer capsule so 3D gauges are completely unobscured
            if (cameraController != null)
            {
                bool isCockpit = cameraController.CurrentMode == BusCameraController.CameraMode.DriverCockpit;
                if (_speedPanelRect != null && _speedPanelRect.gameObject.activeSelf == isCockpit)
                {
                    _speedPanelRect.gameObject.SetActive(!isCockpit);
                }
                if (_gearButtonsGroup != null)
                {
                    Vector2 targetGearPos = isCockpit ? new Vector2(0f, 10f) : new Vector2(0f, 110f);
                    if (_gearButtonsGroup.anchoredPosition != targetGearPos)
                    {
                        _gearButtonsGroup.anchoredPosition = targetGearPos;
                    }
                }
            }
        }

        private void OnCamRearClicked()
        {
            if (cameraController != null)
            {
                cameraController.ToggleRearView();
                if (_camRearBtn != null)
                {
                    _camRearBtn.GetComponent<Image>().color = cameraController.IsRearViewActive ? ColAmber : new Color(0.12f, 0.16f, 0.22f, 0.95f);
                }
            }
        }

        private void OnPauseClicked()
        {
            Time.timeScale = 0f;
            if (_pauseModal != null) _pauseModal.SetActive(true);
        }

        private void OnResumeClicked()
        {
            Time.timeScale = 1f;
            if (_pauseModal != null) _pauseModal.SetActive(false);
        }

        private void UpdateSystemsVisuals()
        {
            if (busController == null) return;

            if (_beamBtn != null)
            {
                _beamBtn.GetComponent<Image>().color = busController.headlightsActive ? ColAmber : new Color(0.12f, 0.16f, 0.22f, 0.95f);
                if (_beamLabel != null) _beamLabel.color = busController.headlightsActive ? Color.black : ColAmber;
            }

            if (_wiperBtn != null)
            {
                _wiperBtn.GetComponent<Image>().color = busController.wipersActive ? ColAmber : new Color(0.12f, 0.16f, 0.22f, 0.95f);
                if (_wiperLabel != null) _wiperLabel.color = busController.wipersActive ? Color.black : ColTextMuted;
            }

            if (_hazardBtn != null)
            {
                _hazardBtn.GetComponent<Image>().color = busController.hazardsActive ? ColAmber : new Color(0.12f, 0.16f, 0.22f, 0.95f);
                if (_hazardLabel != null) _hazardLabel.color = busController.hazardsActive ? Color.black : ColAmber;
            }

            if (_parkBrakeBtn != null)
            {
                bool isPark = (busController.gearMode == TransmissionGear.Park || busController.isHandbrakeActive);
                _parkBrakeBtn.GetComponent<Image>().color = isPark ? ColRed : new Color(0.12f, 0.16f, 0.22f, 0.95f);
            }
        }

        private void UpdateBlinkersVisual()
        {
            if (busController == null) return;

            bool blink = busController.indicatorBlinkState;
            if (_turnLeftIcon != null)
            {
                bool leftActive = busController.turnLeftActive || busController.hazardsActive;
                _turnLeftIcon.color = (leftActive && blink) ? ColAmberGlow : new Color(0.12f, 0.16f, 0.22f, 0.95f);
            }

            if (_turnRightIcon != null)
            {
                bool rightActive = busController.turnRightActive || busController.hazardsActive;
                _turnRightIcon.color = (rightActive && blink) ? ColAmberGlow : new Color(0.12f, 0.16f, 0.22f, 0.95f);
            }
        }

        // ==========================================
        // UI BUILDER HELPERS
        // ==========================================

        private RectTransform CreateEmptyUI(string name, Transform parent, Vector2 anchorMin, Vector2 anchorMax, Vector2 pos, Vector2 size, Vector2? pivot = null)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            var rect = go.AddComponent<RectTransform>();
            rect.anchorMin = anchorMin;
            rect.anchorMax = anchorMax;
            rect.pivot = pivot ?? new Vector2(0.5f, 0.5f);
            rect.anchoredPosition = pos;
            rect.sizeDelta = size;
            return rect;
        }

        private GameObject CreatePanel(string name, Transform parent, Vector2 pos, Vector2 size, Color color, Vector2? aMin = null, Vector2? aMax = null, Vector2? pivot = null)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            var rect = go.AddComponent<RectTransform>();
            rect.anchorMin = aMin ?? new Vector2(0.5f, 0.5f); // DEFAULT CENTER (0.5, 0.5)
            rect.anchorMax = aMax ?? new Vector2(0.5f, 0.5f);
            rect.pivot = pivot ?? new Vector2(0.5f, 0.5f);
            rect.anchoredPosition = pos;
            rect.sizeDelta = size;

            var img = go.AddComponent<Image>();
            img.sprite = _panelSprite;
            img.type = Image.Type.Sliced;
            img.color = color;
            return go;
        }

        private Button CreateIconButton(string name, Transform parent, Vector2 pos, Vector2 size, string text, int fontSize, Color textCol, Vector2? aMin = null, Vector2? aMax = null, Vector2? pivot = null)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            var rect = go.AddComponent<RectTransform>();
            rect.anchorMin = aMin ?? new Vector2(0.5f, 0.5f);
            rect.anchorMax = aMax ?? new Vector2(0.5f, 0.5f);
            rect.pivot = pivot ?? new Vector2(0.5f, 0.5f);
            rect.anchoredPosition = pos;
            rect.sizeDelta = size;

            var img = go.AddComponent<Image>();
            img.sprite = _btnSprite;
            img.type = Image.Type.Sliced;
            img.color = new Color(0.12f, 0.16f, 0.22f, 0.95f);

            var btn = go.AddComponent<Button>();
            btn.targetGraphic = img;

            var txt = CreateText("Label", go.transform, Vector2.zero, size, text, fontSize, FontStyle.Bold, textCol);
            txt.alignment = TextAnchor.MiddleCenter;

            return btn;
        }

        private UIHoldButton CreateHoldButton(string name, Transform parent, Vector2 pos, Vector2 size, string text, int fontSize, KeyCode shortcut = KeyCode.None, Color? tint = null, Vector2? aMin = null, Vector2? aMax = null, Vector2? pivot = null)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            var rect = go.AddComponent<RectTransform>();
            rect.anchorMin = aMin ?? new Vector2(0.5f, 0.5f);
            rect.anchorMax = aMax ?? new Vector2(0.5f, 0.5f);
            rect.pivot = pivot ?? new Vector2(0.5f, 0.5f);
            rect.anchoredPosition = pos;
            rect.sizeDelta = size;

            var img = go.AddComponent<Image>();
            img.sprite = _btnSprite;
            img.type = Image.Type.Sliced;
            Color baseCol = tint ?? new Color(0.12f, 0.16f, 0.22f, 0.95f);
            img.color = baseCol;

            var hold = go.AddComponent<UIHoldButton>();
            hold.targetGraphic = img;
            hold.normalColor = baseCol;
            hold.pressedColor = (tint.HasValue) ? Color.Lerp(tint.Value, Color.white, 0.35f) : ColAmber;
            hold.keyboardShortcut = shortcut;

            if (!string.IsNullOrEmpty(text))
            {
                var txt = CreateText("Label", go.transform, Vector2.zero, size, text, fontSize, FontStyle.Bold, Color.white);
                txt.alignment = TextAnchor.MiddleCenter;
            }

            return hold;
        }

        private Text CreateText(string name, Transform parent, Vector2 pos, Vector2 size, string content, int fontSize, FontStyle style, Color color)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            var rect = go.AddComponent<RectTransform>();
            rect.anchoredPosition = pos;
            rect.sizeDelta = size;

            var txt = go.AddComponent<Text>();
            txt.font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            if (txt.font == null) txt.font = Resources.GetBuiltinResource<Font>("Arial.ttf");
            txt.text = content;
            txt.fontSize = fontSize;
            txt.fontStyle = style;
            txt.color = color;
            txt.raycastTarget = false;
            return txt;
        }
    }
}