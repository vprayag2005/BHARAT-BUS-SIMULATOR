using UnityEngine;
using UnityEngine.EventSystems;
using BharatBusSim.Vehicles;

namespace BharatBusSim.UI
{
    /// <summary>
    /// Transparent full-screen touch receiver for Camera Look & 360 Orbit modes.
    /// Placed behind HUD controls so dragging on open screen rotates the camera,
    /// while touches on controls (steering wheel, pedals, buttons) are consumed by those controls.
    /// Double-tap in Driver Cockpit mode resets head rotation straight forward.
    /// </summary>
    public class UICameraOrbitZone : MonoBehaviour, IBeginDragHandler, IDragHandler, IEndDragHandler, IPointerClickHandler
    {
        public BusCameraController cameraController;

        private void EnsureCameraController()
        {
            if (cameraController == null)
            {
                cameraController = FindFirstObjectByType<BusCameraController>();
            }
        }

        public void OnBeginDrag(PointerEventData eventData)
        {
            EnsureCameraController();
        }

        public void OnDrag(PointerEventData eventData)
        {
            EnsureCameraController();
            if (cameraController == null) return;

            if (cameraController.CurrentMode == BusCameraController.CameraMode.Orbit360)
            {
                cameraController.OnOrbitDrag(eventData.delta);
            }
            else if (cameraController.CurrentMode == BusCameraController.CameraMode.DriverCockpit)
            {
                cameraController.OnCockpitLookDrag(eventData.delta);
            }
        }

        public void OnEndDrag(PointerEventData eventData)
        {
        }

        public void OnPointerClick(PointerEventData eventData)
        {
            EnsureCameraController();
            if (eventData.clickCount >= 2 && cameraController != null)
            {
                if (cameraController.CurrentMode == BusCameraController.CameraMode.DriverCockpit)
                {
                    cameraController.ResetCockpitView();
                }
            }
        }
    }
}
