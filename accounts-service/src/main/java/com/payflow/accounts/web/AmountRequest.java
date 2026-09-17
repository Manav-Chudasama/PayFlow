package com.payflow.accounts.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Body for the credit/debit endpoints: {@code { "amount": 100.00 }}. */
public record AmountRequest(
        @NotNull @Positive BigDecimal amount
) {}
