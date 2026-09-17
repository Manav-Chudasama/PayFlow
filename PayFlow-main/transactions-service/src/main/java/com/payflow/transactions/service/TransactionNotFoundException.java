package com.payflow.transactions.service;

import java.util.UUID;

/** Thrown when a transaction id does not exist; mapped to 404 by the web layer. */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(UUID id) {
        super("Transaction not found: " + id);
    }
}
