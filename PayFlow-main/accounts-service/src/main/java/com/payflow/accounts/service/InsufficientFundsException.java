package com.payflow.accounts.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thrown when a debit would drive an account below zero. The controller layer
 * translates this into a 4xx (not a 500) — a debit for more than the balance is
 * a client error, not a server fault. This is the Phase 1 error-handling story.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, BigDecimal balance, BigDecimal amount) {
        super("Account %s has insufficient funds: balance=%s, requested debit=%s"
                .formatted(accountId, balance, amount));
    }
}
