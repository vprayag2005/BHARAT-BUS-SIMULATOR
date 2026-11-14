using UnityEngine;

namespace BharatBusSim.Vehicles
{
    /// <summary>
    /// Synchronizes physical turn signal and hazard light emissive materials and dynamic lights
    /// with BusController blinker state.
    /// Controls front corner indicators, side amber marker indicators, and rear amber tail indicators.
    /// </summary>
    public class BusIndicatorVisuals : MonoBehaviour
    {
        public BusController busController;

        [Header("Dynamic Signal Lights")]
        public Light[] leftLights;
        public Light[] rightLights;

        [Header("Lamp Renderers")]
        public Renderer[] leftIndicators;
        public Renderer[] rightIndicators;
        public Renderer[] combinedIndicators;

        [Header("Materials")]
        public Material indicatorOffMaterial;
        public Material indicatorOnMaterial;

        private bool _wasLeftOn = false;
        private bool _wasRightOn = false;

        private void Start()
        {
            if (busController == null)
            {
                busController = GetComponent<BusController>();
            }

            SetLightGroup(leftLights, false);
            SetLightGroup(rightLights, false);
            SetIndicatorGroup(leftIndicators, false);
            SetIndicatorGroup(rightIndicators, false);
            SetIndicatorGroup(combinedIndicators, false);
        }

        private void Update()
        {
            if (busController == null) return;

            bool blink = busController.indicatorBlinkState;
            bool hazards = busController.hazardsActive;

            bool leftShouldBeOn = blink && (hazards || busController.turnLeftActive);
            bool rightShouldBeOn = blink && (hazards || busController.turnRightActive);
            bool combinedShouldBeOn = blink && (hazards || busController.turnLeftActive || busController.turnRightActive);

            if (leftShouldBeOn != _wasLeftOn)
            {
                SetLightGroup(leftLights, leftShouldBeOn);
                SetIndicatorGroup(leftIndicators, leftShouldBeOn);
                _wasLeftOn = leftShouldBeOn;
            }

            if (rightShouldBeOn != _wasRightOn)
            {
                SetLightGroup(rightLights, rightShouldBeOn);
                SetIndicatorGroup(rightIndicators, rightShouldBeOn);
                _wasRightOn = rightShouldBeOn;
            }

            SetIndicatorGroup(combinedIndicators, combinedShouldBeOn);
        }

        private void SetLightGroup(Light[] lights, bool on)
        {
            if (lights == null) return;
            for (int i = 0; i < lights.Length; i++)
            {
                if (lights[i] != null) lights[i].enabled = on;
            }
        }

        private void SetIndicatorGroup(Renderer[] renderers, bool on)
        {
            if (renderers == null) return;
            Material matToApply = on ? indicatorOnMaterial : indicatorOffMaterial;
            if (matToApply == null) return;

            for (int i = 0; i < renderers.Length; i++)
            {
                var r = renderers[i];
                if (r != null)
                {
                    r.sharedMaterial = matToApply;
                }
            }
        }
    }
}
