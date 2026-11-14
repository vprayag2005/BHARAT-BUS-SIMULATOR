using UnityEngine;

namespace BharatBusSim.Vehicles
{
    /// <summary>
    /// Synchronizes a visual wheel transform with its corresponding WheelCollider's physics pose.
    /// In Stage 3B (True 6-Wheel Physics with 3 Axles):
    /// - Each visual wheel is driven by its own physical WheelCollider.
    /// - Front visual wheels follow front WheelCollider steering, rotation and suspension.
    /// - Middle and rear visual wheels have independent suspension compression and rolling rotation.
    /// - Rear and middle visual wheels do not receive steering.
    /// - Left and right wheel rim dish orientations are preserved facing outwards.
    /// </summary>
    public class BusWheel : MonoBehaviour
    {
        [Tooltip("The physics WheelCollider driving this wheel")]
        public WheelCollider wheelCollider;

        [Tooltip("The visual transform that rotates and steers with the wheel")]
        public Transform visualTransform;

        [Tooltip("Whether this visual wheel turns with steering")]
        public bool receivesSteering = true;

        [Tooltip("Whether this wheel is on the left side of the bus")]
        public bool isLeftSide = false;

        [Tooltip("Whether an explicit resting local position in vehicle space is enforced")]
        public bool useExplicitRestPosition = false;

        [Tooltip("Explicit resting local position in vehicle space (optional override)")]
        public Vector3 explicitRestPosition;

        private Transform _vehicleRoot;
        private Vector3 _localRestPosition;
        private float _nominalColliderLocalY;
        private bool _initialized;

        private void Start()
        {
            Initialize();
        }

        public void Initialize()
        {
            if (_initialized) return;

            var rb = GetComponentInParent<Rigidbody>();
            if (rb != null)
            {
                _vehicleRoot = rb.transform;
            }
            else if (transform.parent != null)
            {
                _vehicleRoot = transform.parent;
            }

            if (useExplicitRestPosition)
            {
                _localRestPosition = explicitRestPosition;
            }
            else if (_vehicleRoot != null && visualTransform != null)
            {
                _localRestPosition = _vehicleRoot.InverseTransformPoint(visualTransform.position);
            }

            if (wheelCollider != null && _vehicleRoot != null)
            {
                float mountY = _vehicleRoot.InverseTransformPoint(wheelCollider.transform.position).y;
                float restTravel = wheelCollider.suspensionDistance * wheelCollider.suspensionSpring.targetPosition;
                _nominalColliderLocalY = mountY - restTravel;
            }

            _initialized = true;
        }

        private void LateUpdate()
        {
            UpdateWheelPose();
        }

        public void UpdateWheelPose()
        {
            if (wheelCollider == null || visualTransform == null) return;
            if (!_initialized) Initialize();

            // 1. Get WheelCollider world pose
            wheelCollider.GetWorldPose(out Vector3 colWorldPos, out Quaternion colWorldRot);

            if (useExplicitRestPosition && _vehicleRoot != null)
            {
                // Enforce exact lateral/longitudinal seating with independent vertical suspension travel
                Vector3 colLocalPos = _vehicleRoot.InverseTransformPoint(colWorldPos);
                float deltaY = colLocalPos.y - _nominalColliderLocalY;
                Vector3 targetLocalPos = new Vector3(_localRestPosition.x, _localRestPosition.y + deltaY, _localRestPosition.z);
                visualTransform.position = _vehicleRoot.TransformPoint(targetLocalPos);
            }
            else
            {
                // Direct co-centered physical tracking
                visualTransform.position = colWorldPos;
            }

            // 2. Compute Rotation:
            // Base orientation: Left wheels face outward (-X) with 180° yaw; Right wheels face outward (+X) with 0° yaw
            Quaternion baseYaw = isLeftSide ? Quaternion.Euler(0f, 180f, 0f) : Quaternion.identity;

            visualTransform.rotation = colWorldRot * baseYaw;
        }
    }
}
