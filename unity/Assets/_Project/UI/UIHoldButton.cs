using System;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace BharatBusSim.UI
{
    /// <summary>
    /// Touch button supporting press-and-hold interactions for pedals, steering buttons, and horn.
    /// Implements pointer events with independent pointer tracking for reliable mobile multi-touch.
    /// </summary>
    public class UIHoldButton : MonoBehaviour, IPointerDownHandler, IPointerUpHandler
    {
        [Tooltip("Optional keyboard key that activates this button for desktop testing")]
        public KeyCode keyboardShortcut = KeyCode.None;

        [Tooltip("Visual target to tint on press")]
        public Graphic targetGraphic;

        [Tooltip("Normal color tint")]
        public Color normalColor = Color.white;

        [Tooltip("Pressed color tint")]
        public Color pressedColor = new Color(0.96f, 0.62f, 0.04f, 1f); // Amber

        public bool IsPressed { get; private set; }
        public float HoldDuration { get; private set; }

        public event Action OnPressed;
        public event Action OnReleased;

        private int _activePointerId = -1;

        private void Awake()
        {
            if (targetGraphic == null)
            {
                targetGraphic = GetComponent<Graphic>();
            }
            UpdateVisual();
        }

        private void Update()
        {
            // Keyboard shortcut support with safe dual InputSystem / legacy fallback
            if (keyboardShortcut != KeyCode.None)
            {
                var inputKey = GameInputHelper.ToInputSystemKey(keyboardShortcut);
                bool kbPressed = GameInputHelper.IsKeyPressed(inputKey, keyboardShortcut);
                if (kbPressed && !IsPressed && _activePointerId == -1)
                {
                    SetPressedState(true);
                }
                else if (!kbPressed && IsPressed && _activePointerId == -1)
                {
                    SetPressedState(false);
                }
            }

            if (IsPressed)
            {
                HoldDuration += Time.unscaledDeltaTime;
            }
        }

        public void OnPointerDown(PointerEventData eventData)
        {
            if (_activePointerId == -1)
            {
                _activePointerId = eventData.pointerId;
                SetPressedState(true);
            }
        }

        public void OnPointerUp(PointerEventData eventData)
        {
            if (eventData.pointerId == _activePointerId)
            {
                _activePointerId = -1;
                SetPressedState(false);
            }
        }

        private void SetPressedState(bool pressed)
        {
            if (IsPressed == pressed) return;

            IsPressed = pressed;
            if (pressed)
            {
                HoldDuration = 0f;
                OnPressed?.Invoke();
            }
            else
            {
                OnReleased?.Invoke();
            }

            UpdateVisual();
        }

        private void UpdateVisual()
        {
            if (targetGraphic != null)
            {
                targetGraphic.color = IsPressed ? pressedColor : normalColor;
            }
        }

        private void OnDisable()
        {
            _activePointerId = -1;
            SetPressedState(false);
        }
    }
}
