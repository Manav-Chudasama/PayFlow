package com.payflow.transactions.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body for {@code POST /transactions}. {@code description} and {@code merchant} are
 * optional context that the Ledger's AI categorizer uses to classify the transfer.
 */
public record TransferRequest(
        @NotNull UUID fromAccount,
        @NotNull UUID toAccount,
        @NotNull @Positive BigDecimal amount,
        String description,
        String merchant
) {}
