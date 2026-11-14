using UnityEngine;

namespace BharatBusSim.Toll
{
    /// <summary>
    /// Helper component attached to toll triggers to forward trigger events to the master controller.
    /// </summary>
    public class TollZoneTrigger : MonoBehaviour
    {
        public enum TriggerZoneType { ApproachZone, BarrierZone, ExitZone }
        public TriggerZoneType zoneType;
        public TollPlazaController controller;

        private void OnTriggerEnter(Collider other)
        {
            if (controller == null) return;
            switch (zoneType)
            {
                case TriggerZoneType.ApproachZone:
                    controller.HandleApproachingEntry(other);
                    break;
                case TriggerZoneType.BarrierZone:
                    controller.HandleBarrierCrossingEntry(other);
                    break;
                case TriggerZoneType.ExitZone:
                    controller.HandleLaneExit(other);
                    break;
            }
        }

        private void OnTriggerExit(Collider other)
        {
            if (controller == null) return;
            if (zoneType == TriggerZoneType.ApproachZone)
            {
                controller.HandleApproachingExit(other);
            }
        }
    }
}
