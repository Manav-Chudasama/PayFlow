package com.payflow.transactions.service;

/** A request that is semantically invalid (e.g. transferring to the same account). Maps to 400. */
public class InvalidTransferException extends RuntimeException {

    public InvalidTransferException(String message) {
        super(message);
    }
}
