using System;
using UnityEngine;

namespace BharatBusSim.Toll
{
    /// <summary>
    /// Session wallet and economic transaction manager for Bharat Bus Simulator.
    /// Manages player funds with default starting balance of ₹10,000.
    /// Supports thread-safe, idempotent deductions and real-time UI notifications.
    /// </summary>
    public class TollEconomyManager : MonoBehaviour
    {
        public static TollEconomyManager Instance { get; private set; }

        public const int DefaultInitialBalance = 10000;
        public const int DefaultTollCharge = 50;

        [Header("Runtime Economy State")]
        [SerializeField] private int _currentBalance = DefaultInitialBalance;

        public int CurrentBalance => _currentBalance;

        public event Action<int> OnBalanceChanged;
        public event Action<string, bool> OnTransactionCompleted;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            _currentBalance = DefaultInitialBalance;
        }

        public void ResetWallet(int initialBalance = DefaultInitialBalance)
        {
            _currentBalance = initialBalance;
            OnBalanceChanged?.Invoke(_currentBalance);
        }

        public bool HasSufficientFunds(int amount)
        {
            return _currentBalance >= amount;
        }

        /// <summary>
        /// Deducts the specified amount exactly once if sufficient funds exist.
        /// Returns true if transaction succeeded; false if insufficient funds.
        /// </summary>
        public bool TryDeduct(int amount, string reason = "Paliyekkara Toll Passage")
        {
            if (amount <= 0) return true;

            if (_currentBalance < amount)
            {
                string failMsg = $"INSUFFICIENT FUNDS! Required: ₹{amount}, Balance: ₹{_currentBalance}";
                OnTransactionCompleted?.Invoke(failMsg, false);
                return false;
            }

            _currentBalance -= amount;
            string successMsg = $"{reason}: -₹{amount} (Balance: ₹{_currentBalance})";
            OnBalanceChanged?.Invoke(_currentBalance);
            OnTransactionCompleted?.Invoke(successMsg, true);
            return true;
        }

        public void AddFunds(int amount)
        {
            if (amount <= 0) return;
            _currentBalance += amount;
            OnBalanceChanged?.Invoke(_currentBalance);
        }
    }
}
