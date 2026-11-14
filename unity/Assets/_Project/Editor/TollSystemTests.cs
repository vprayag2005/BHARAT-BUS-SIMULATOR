using System;
using UnityEngine;
using UnityEditor;
using BharatBusSim.Toll;
using BharatBusSim.Vehicles;

namespace BharatBusSim.Editor
{
    public static class TollSystemTests
    {
        [MenuItem("BharatBusSim/Tests/Run Toll System Automated Tests")]
        public static bool RunAllTollTests()
        {
            Debug.Log("==================================================================");
            Debug.Log("[TollSystemTests] STARTING TOLL ECONOMY & BARRIER AUTOMATED TESTS");
            Debug.Log("==================================================================");

            bool allPassed = true;

            // Setup test GameObject
            var testGo = new GameObject("Toll_Test_Harness");
            var economy = testGo.AddComponent<TollEconomyManager>();
            var awakeEco = typeof(TollEconomyManager).GetMethod("Awake", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);
            if (awakeEco != null) awakeEco.Invoke(economy, null);

            var controller = testGo.AddComponent<TollPlazaController>();
            var startCtrl = typeof(TollPlazaController).GetMethod("Start", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);
            if (startCtrl != null) startCtrl.Invoke(controller, null);

            // Mock barrier arm & collider
            var armGo = new GameObject("Mock_Barrier_Arm");
            armGo.transform.SetParent(testGo.transform, false);
            var armCol = armGo.AddComponent<BoxCollider>();
            controller.barrierArm = armGo.transform;
            controller.barrierPhysicalCollider = armCol;

            try
            {
                // Test 1: Initial Wallet Balance is ₹10,000
                economy.ResetWallet(10000);
                if (economy.CurrentBalance != 10000)
                {
                    Debug.LogError($"[TollSystemTests] Test 1 FAILED: Expected balance 10000, got {economy.CurrentBalance}");
                    allPassed = false;
                }
                else
                {
                    Debug.Log("[TollSystemTests] Test 1 PASSED: Initial balance is correctly ₹10,000.");
                }

                // Test 2: Successful Payment of ₹50 exactly once
                bool pay1 = controller.AttemptTollPayment();
                if (!pay1 || economy.CurrentBalance != 9950 || !controller.hasPaidForCurrentPassage || controller.currentState != TollPassageState.Paid)
                {
                    Debug.LogError($"[TollSystemTests] Test 2 FAILED: Payment failed! Balance={economy.CurrentBalance}, State={controller.currentState}");
                    allPassed = false;
                }
                else
                {
                    Debug.Log($"[TollSystemTests] Test 2 PASSED: ₹50 deducted successfully. Balance: ₹{economy.CurrentBalance}, State: {controller.currentState}");
                }

                // Test 3: Idempotency (Repeated call in same passage does not deduct again)
                bool payRepeat = controller.AttemptTollPayment();
                if (economy.CurrentBalance != 9950)
                {
                    Debug.LogError($"[TollSystemTests] Test 3 FAILED: Idempotency violated! Balance changed to {economy.CurrentBalance}");
                    allPassed = false;
                }
                else
                {
                    Debug.Log("[TollSystemTests] Test 3 PASSED: Idempotency verified. No repeated deductions while in lane.");
                }

                // Test 4: Insufficient Funds Prevention
                economy.ResetWallet(30); // Less than ₹50
                controller.hasPaidForCurrentPassage = false;
                controller.currentState = TollPassageState.Approaching;

                bool payFail = controller.AttemptTollPayment();
                if (payFail || economy.CurrentBalance != 30 || controller.hasPaidForCurrentPassage || controller.currentState == TollPassageState.Paid)
                {
                    Debug.LogError($"[TollSystemTests] Test 4 FAILED: Insufficient funds allowed or altered balance! Balance={economy.CurrentBalance}");
                    allPassed = false;
                }
                else
                {
                    Debug.Log($"[TollSystemTests] Test 4 PASSED: Insufficient funds safely prevented transaction. Balance remained ₹30, barrier locked.");
                }

                // Test 5: Reverse Movement Reset
                economy.ResetWallet(10000);
                controller.currentState = TollPassageState.AwaitingPayment;
                // Mock vehicle collider
                var busGo = new GameObject("Mock_Player_Bus");
                busGo.AddComponent<BusController>();
                var busCol = busGo.AddComponent<BoxCollider>();

                // Trigger approaching exit (player reversed away from toll)
                controller.HandleApproachingExit(busCol);
                if (controller.currentState != TollPassageState.Approaching || controller.hasPaidForCurrentPassage)
                {
                    Debug.LogError($"[TollSystemTests] Test 5 FAILED: Reverse exit did not reset state cleanly! State={controller.currentState}");
                    allPassed = false;
                }
                else
                {
                    Debug.Log("[TollSystemTests] Test 5 PASSED: Reversing out of toll lane safely resets state to Approaching.");
                }

                UnityEngine.Object.DestroyImmediate(busGo);

                if (allPassed)
                {
                    Debug.Log("==================================================================");
                    Debug.Log("[TollSystemTests] ALL 5 TOLL SYSTEM AUTOMATED TESTS PASSED!");
                    Debug.Log("==================================================================");
                }
            }
            finally
            {
                UnityEngine.Object.DestroyImmediate(testGo);
            }

            return allPassed;
        }
    }
}
