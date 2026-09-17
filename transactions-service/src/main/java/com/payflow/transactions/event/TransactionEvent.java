package com.payflow.transactions.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The message published to the {@code transactions} Kafka topic once a transfer
 * completes. This same shape is duplicated in the Ledger service (Phase 3) — for
 * a one-week build, copying one record is simpler and more honest than a shared
 * module.
 */
public record TransactionEvent(
        UUID transactionId,
        UUID fromAccount,
        UUID toAccount,
        BigDecimal amount,
        String currency,
        String description,
        String merchant,
        Instant timestamp
) {}
