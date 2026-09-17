package com.payflow.accounts.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Payload for creating an account. {@code initialBalance} and {@code currency}
 * are optional (default 0 and INR respectively).
 */
public record CreateAccountRequest(
        @NotBlank String ownerName,
        @PositiveOrZero BigDecimal initialBalance,
        String currency
) {}
