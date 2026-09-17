package com.payflow.transactions.service;

import com.payflow.transactions.domain.Transaction;

/**
 * Raised when a transfer could not complete (e.g. the debit was rejected for
 * insufficient funds). Carries the persisted FAILED transaction so the caller
 * still gets the row with its final status, returned as a 4xx by the web layer.
 */
public class TransferFailedException extends RuntimeException {

    private final transient Transaction transaction;

    public TransferFailedException(Transaction transaction, String message) {
        super(message);
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }
}
