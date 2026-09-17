package com.payflow.transactions.domain;

/** Lifecycle of a transfer. Only COMPLETED transfers publish an event to Kafka. */
public enum TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED
}
