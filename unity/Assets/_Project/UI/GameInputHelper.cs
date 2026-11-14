using System;
using UnityEngine;
using UnityEngine.InputSystem;

namespace BharatBusSim.UI
{
    /// <summary>
    /// Unified input helper bridging the new Unity Input System (com.unity.inputsystem)
    /// and legacy UnityEngine.Input with safe fallbacks.
    /// Prevents InvalidOperationException when activeInputHandler is configured,
    /// while preserving full keyboard controls for development/desktop testing
    /// and touch controls on mobile.
    /// </summary>
    public static class GameInputHelper
    {
        public static bool IsKeyPressed(Key newKey, KeyCode legacyKey)
        {
#if ENABLE_INPUT_SYSTEM
            var kb = Keyboard.current;
            if (kb != null)
            {
                try
                {
                    return kb[newKey].isPressed;
                }
                catch { }
            }
#endif

#if ENABLE_LEGACY_INPUT_MANAGER
            if (legacyKey != KeyCode.None)
            {
                try
                {
                    return UnityEngine.Input.GetKey(legacyKey);
                }
                catch { }
            }
#endif

            return false;
        }

        public static bool WasKeyPressedThisFrame(Key newKey, KeyCode legacyKey)
        {
#if ENABLE_INPUT_SYSTEM
            var kb = Keyboard.current;
            if (kb != null)
            {
                try
                {
                    return kb[newKey].wasPressedThisFrame;
                }
                catch { }
            }
#endif

#if ENABLE_LEGACY_INPUT_MANAGER
            if (legacyKey != KeyCode.None)
            {
                try
                {
                    return UnityEngine.Input.GetKeyDown(legacyKey);
                }
                catch { }
            }
#endif

            return false;
        }

        public static bool WasKeyReleasedThisFrame(Key newKey, KeyCode legacyKey)
        {
#if ENABLE_INPUT_SYSTEM
            var kb = Keyboard.current;
            if (kb != null)
            {
                try
                {
                    return kb[newKey].wasReleasedThisFrame;
                }
                catch { }
            }
#endif

#if ENABLE_LEGACY_INPUT_MANAGER
            if (legacyKey != KeyCode.None)
            {
                try
                {
                    return UnityEngine.Input.GetKeyUp(legacyKey);
                }
                catch { }
            }
#endif

            return false;
        }

        public static Key ToInputSystemKey(KeyCode legacy)
        {
            switch (legacy)
            {
                case KeyCode.W: return Key.W;
                case KeyCode.S: return Key.S;
                case KeyCode.A: return Key.A;
                case KeyCode.D: return Key.D;
                case KeyCode.UpArrow: return Key.UpArrow;
                case KeyCode.DownArrow: return Key.DownArrow;
                case KeyCode.LeftArrow: return Key.LeftArrow;
                case KeyCode.RightArrow: return Key.RightArrow;
                case KeyCode.Space: return Key.Space;
                case KeyCode.P: return Key.P;
                case KeyCode.R: return Key.R;
                case KeyCode.N: return Key.N;
                case KeyCode.L: return Key.L;
                case KeyCode.J: return Key.J;
                case KeyCode.H: return Key.H;
                case KeyCode.Escape: return Key.Escape;
                default: return Key.None;
            }
        }
    }
}
