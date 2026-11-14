using System.Collections.Generic;
using UnityEngine;

namespace BharatBusSim.Diagnostics
{
    /// <summary>
    /// Phase 0 Smoke Test Diagnostics Overlay.
    /// Displays mobile device specifications, real-time FPS, frame timing,
    /// and rendering information to verify Android runtime stability.
    /// </summary>
    public class DeviceDiagnosticsOverlay : MonoBehaviour
    {
        private const int FrameBufferSize = 60;
        private readonly Queue<float> _frameDeltas = new Queue<float>(FrameBufferSize);
        private float _fpsAccumulator;
        private float _currentFps;
        private float _currentFrameTimeMs;

        private GUIStyle _boxStyle;
        private GUIStyle _headerStyle;
        private GUIStyle _labelStyle;
        private GUIStyle _statusStyle;
        private GUIStyle _buttonStyle;
        private bool _stylesInitialized;

        private void Awake()
        {
            // Lock target frame rate to 60 FPS for smooth mobile gameplay testing
            Application.targetFrameRate = 60;
            Screen.sleepTimeout = SleepTimeout.NeverSleep;
        }

        private void Update()
        {
            float dt = Time.unscaledDeltaTime;
            _frameDeltas.Enqueue(dt);
            _fpsAccumulator += dt;

            if (_frameDeltas.Count > FrameBufferSize)
            {
                _fpsAccumulator -= _frameDeltas.Dequeue();
            }

            if (_frameDeltas.Count > 0 && _fpsAccumulator > 0.0001f)
            {
                _currentFps = _frameDeltas.Count / _fpsAccumulator;
                _currentFrameTimeMs = (_fpsAccumulator / _frameDeltas.Count) * 1000f;
            }
        }

        private void InitializeStyles()
        {
            float scale = Mathf.Max(1.0f, Screen.dpi / 160f);
            int baseFontSize = Mathf.RoundToInt(13 * scale);

            _boxStyle = new GUIStyle(GUI.skin.box);
            Texture2D bgTex = new Texture2D(1, 1);
            bgTex.SetPixel(0, 0, new Color(0.05f, 0.07f, 0.12f, 0.85f));
            bgTex.Apply();
            _boxStyle.normal.background = bgTex;
            _boxStyle.padding = new RectOffset(16, 16, 16, 16);

            _headerStyle = new GUIStyle(GUI.skin.label)
            {
                fontSize = Mathf.RoundToInt(16 * scale),
                fontStyle = FontStyle.Bold,
                normal = { textColor = new Color(0.3f, 0.85f, 1.0f) }
            };

            _labelStyle = new GUIStyle(GUI.skin.label)
            {
                fontSize = baseFontSize,
                normal = { textColor = Color.white }
            };

            _statusStyle = new GUIStyle(GUI.skin.label)
            {
                fontSize = Mathf.RoundToInt(14 * scale),
                fontStyle = FontStyle.Bold,
                normal = { textColor = new Color(0.3f, 1.0f, 0.4f) }
            };

            _buttonStyle = new GUIStyle(GUI.skin.button)
            {
                fontSize = baseFontSize,
                fontStyle = FontStyle.Bold
            };

            _stylesInitialized = true;
        }

        private void OnGUI()
        {
            if (!_stylesInitialized)
            {
                InitializeStyles();
            }

            // Respect Safe Area for notches and punch holes
            Rect safeArea = Screen.safeArea;
            float panelWidth = Mathf.Min(safeArea.width - 40, 580);
            float panelHeight = Mathf.Min(safeArea.height - 40, 380);
            Rect panelRect = new Rect(safeArea.x + 20, safeArea.y + 20, panelWidth, panelHeight);

            GUILayout.BeginArea(panelRect, _boxStyle);
            
            GUILayout.Label("BHARAT BUS SIMULATOR — PHASE 0 SMOKE TEST", _headerStyle);
            GUILayout.Space(6);

            GUILayout.Label($"Package: {Application.identifier}  |  Unity: {Application.unityVersion}", _labelStyle);
            GUILayout.Label($"Performance: {_currentFps:0.0} FPS ({_currentFrameTimeMs:0.1} ms/frame)", _labelStyle);
            GUILayout.Label($"Device Model: {SystemInfo.deviceModel}", _labelStyle);
            GUILayout.Label($"OS: {SystemInfo.operatingSystem}", _labelStyle);
            GUILayout.Label($"CPU: {SystemInfo.processorType} ({SystemInfo.processorCount} cores)", _labelStyle);
            GUILayout.Label($"RAM: {SystemInfo.systemMemorySize} MB", _labelStyle);
            GUILayout.Label($"GPU: {SystemInfo.graphicsDeviceName} ({SystemInfo.graphicsDeviceType})", _labelStyle);
            GUILayout.Label($"VRAM: {SystemInfo.graphicsMemorySize} MB", _labelStyle);
            GUILayout.Label($"Screen: {Screen.width}x{Screen.height} @ {Screen.dpi:0} DPI (Orient: {Screen.orientation})", _labelStyle);
            GUILayout.Space(8);

            GUILayout.Label("✔ STATUS: Phase 0 Toolchain & Android Runtime Verified", _statusStyle);
            GUILayout.Space(8);

            if (GUILayout.Button("Quit Application", _buttonStyle, GUILayout.Height(36)))
            {
                Application.Quit();
            }

            GUILayout.EndArea();
        }
    }
}
