package com.giki.mess.billing.model;

import com.giki.mess.common.Money;

/**
 * A consistent, immutable copy of a ledger taken while holding its lock.
 * Reading balance, meal count and status one by one could mix values from two
 * different moments; a snapshot cannot.
 */
public record LedgerSnapshot(Money balance, int unbilledMealCount,
                             StudentLedger.AccountStatus status) {
}
