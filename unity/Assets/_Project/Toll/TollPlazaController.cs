using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using BharatBusSim.Vehicles;

namespace BharatBusSim.Toll
{
    public enum TollPassageState
    {
        Approaching,
        AwaitingPayment,
        Paid,
        Passing,
        Complete
    }

    public class TollPlazaController : MonoBehaviour
    {
        [Header("Toll Configuration")]
        public int tollFee = 50;
        public string plazaName = "Paliyekkara Toll Plaza (NH 544)";

        [Header("Barrier & Signal Elements")]
        public Transform barrierArm;
        public Transform[] barrierArms;
        public Collider barrierPhysicalCollider;
        public Collider[] barrierPhysicalColliders;
        public Renderer trafficLightRenderer;
        public Renderer[] trafficLightRenderers;
        public Material redLightMat;
        public Material greenLightMat;

        [Header("Runtime State")]
        public TollPassageState currentState = TollPassageState.Approaching;
        public bool hasPaidForCurrentPassage = false;

        private Coroutine _barrierAnimCoroutine;

        private void Start()
        {
            SetSignalLight(false); // Start Red
            if (barrierArm != null)
            {
                barrierArm.localRotation = Quaternion.Euler(0f, 0f, 0f); // Closed
            }
            if (barrierArms != null)
            {
                foreach (var arm in barrierArms)
                {
                    if (arm != null) arm.localRotation = Quaternion.Euler(0f, 0f, 0f);
                }
            }
            if (barrierPhysicalCollider != null)
            {
                barrierPhysicalCollider.enabled = true;
            }
            if (barrierPhysicalColliders != null)
            {
                foreach (var col in barrierPhysicalColliders)
                {
                    if (col != null) col.enabled = true;
                }
            }
        }

        public void HandleApproachingEntry(Collider other)
        {
            if (!IsPlayerBus(other)) return;

            if (currentState == TollPassageState.Approaching)
            {
                currentState = TollPassageState.AwaitingPayment;
                AttemptTollPayment();
            }
        }

        public void HandleApproachingExit(Collider other)
        {
            if (!IsPlayerBus(other)) return;

            // If player reversed back out before passing through
            if (currentState == TollPassageState.AwaitingPayment || (currentState == TollPassageState.Paid && !hasPassedBarrier))
            {
                // Reset state cleanly without charging again
                currentState = TollPassageState.Approaching;
                hasPaidForCurrentPassage = false;
                CloseBarrier();
            }
        }

        public void HandleBarrierCrossingEntry(Collider other)
        {
            if (!IsPlayerBus(other)) return;

            if (currentState == TollPassageState.Paid)
            {
                currentState = TollPassageState.Passing;
                hasPassedBarrier = true;
            }
        }

        public void HandleLaneExit(Collider other)
        {
            if (!IsPlayerBus(other)) return;

            if (currentState == TollPassageState.Passing || currentState == TollPassageState.Paid)
            {
                currentState = TollPassageState.Complete;
                CloseBarrier();
                StartCoroutine(ResetTollForNextPassageAfterDelay(5f));
            }
        }

        private bool hasPassedBarrier = false;

        public bool AttemptTollPayment()
        {
            if (hasPaidForCurrentPassage) return true;

            var manager = TollEconomyManager.Instance;
            if (manager == null)
            {
                manager = FindFirstObjectByType<TollEconomyManager>();
            }

            if (manager == null)
            {
                Debug.LogWarning("[TollPlazaController] TollEconomyManager not found in scene!");
                return false;
            }

            if (manager.HasSufficientFunds(tollFee))
            {
                bool success = manager.TryDeduct(tollFee, $"{plazaName} Fee");
                if (success)
                {
                    hasPaidForCurrentPassage = true;
                    currentState = TollPassageState.Paid;
                    OpenBarrier();
                    return true;
                }
            }
            else
            {
                currentState = TollPassageState.AwaitingPayment;
                // Barrier remains closed, red light stays active
                SetSignalLight(false);
                if (barrierPhysicalCollider != null) barrierPhysicalCollider.enabled = true;
            }

            return false;
        }

        public void OpenBarrier()
        {
            SetSignalLight(true); // Green light
            if (barrierPhysicalCollider != null) barrierPhysicalCollider.enabled = false;
            if (barrierPhysicalColliders != null)
            {
                foreach (var col in barrierPhysicalColliders)
                {
                    if (col != null) col.enabled = false;
                }
            }

            if (_barrierAnimCoroutine != null) StopCoroutine(_barrierAnimCoroutine);
            _barrierAnimCoroutine = StartCoroutine(AnimateBarrier(80f, 1.2f));
        }

        public void CloseBarrier()
        {
            SetSignalLight(false); // Red light
            if (_barrierAnimCoroutine != null) StopCoroutine(_barrierAnimCoroutine);
            _barrierAnimCoroutine = StartCoroutine(AnimateBarrier(0f, 1.2f, () =>
            {
                if (barrierPhysicalCollider != null) barrierPhysicalCollider.enabled = true;
                if (barrierPhysicalColliders != null)
                {
                    foreach (var col in barrierPhysicalColliders)
                    {
                        if (col != null) col.enabled = true;
                    }
                }
            }));
        }

        private IEnumerator AnimateBarrier(float targetAngleZ, float duration, Action onComplete = null)
        {
            var armsToAnimate = new List<Transform>();
            if (barrierArm != null) armsToAnimate.Add(barrierArm);
            if (barrierArms != null)
            {
                foreach (var a in barrierArms)
                {
                    if (a != null && !armsToAnimate.Contains(a)) armsToAnimate.Add(a);
                }
            }

            if (armsToAnimate.Count == 0) yield break;

            var startRots = new List<Quaternion>();
            foreach (var arm in armsToAnimate)
            {
                startRots.Add(arm.localRotation);
            }

            Quaternion targetRot = Quaternion.Euler(0f, 0f, targetAngleZ);
            float elapsed = 0f;

            while (elapsed < duration)
            {
                elapsed += Time.deltaTime;
                float t = Mathf.SmoothStep(0f, 1f, elapsed / duration);
                for (int i = 0; i < armsToAnimate.Count; i++)
                {
                    armsToAnimate[i].localRotation = Quaternion.Slerp(startRots[i], targetRot, t);
                }
                yield return null;
            }

            for (int i = 0; i < armsToAnimate.Count; i++)
            {
                armsToAnimate[i].localRotation = targetRot;
            }
            onComplete?.Invoke();
        }

        private void SetSignalLight(bool isGreen)
        {
            Material targetMat = isGreen ? greenLightMat : redLightMat;
            if (trafficLightRenderer != null && targetMat != null)
            {
                trafficLightRenderer.sharedMaterial = targetMat;
            }
            if (trafficLightRenderers != null && targetMat != null)
            {
                foreach (var r in trafficLightRenderers)
                {
                    if (r != null) r.sharedMaterial = targetMat;
                }
            }
        }

        private IEnumerator ResetTollForNextPassageAfterDelay(float delaySeconds)
        {
            yield return new WaitForSeconds(delaySeconds);
            hasPaidForCurrentPassage = false;
            hasPassedBarrier = false;
            currentState = TollPassageState.Approaching;
        }

        private bool IsPlayerBus(Collider col)
        {
            return col.GetComponentInParent<BusController>() != null || col.CompareTag("Player");
        }
    }
}
