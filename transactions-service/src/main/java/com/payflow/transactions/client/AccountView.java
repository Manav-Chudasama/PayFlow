package com.payflow.transactions.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Subset of the Accounts service's account response that we deserialize. */
public record AccountView(
        UUID id,
        String ownerName,
        BigDecimal balance,
        String currency,
        Instant createdAt
) {}
