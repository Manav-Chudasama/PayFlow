package com.payflow.transactions.service;

/**
 * Thrown by {@link DevFaultService} when a one-shot simulated backend failure
 * is armed via the DEV endpoint. Maps to HTTP 503 in the global exception handler.
 */
public class SimulatedBackendException extends RuntimeException {

    public SimulatedBackendException(String message) {
        super(message);
    }
}
