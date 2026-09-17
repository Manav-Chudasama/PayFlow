package com.payflow.ledger.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-side copy of the event published by the Transactions service to the
 * {@code transactions} topic. Deliberately duplicated (not shared via a module)
 * — for a one-week build, copying one record keeps the services independent and
 * is honest about the trade-off.
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
