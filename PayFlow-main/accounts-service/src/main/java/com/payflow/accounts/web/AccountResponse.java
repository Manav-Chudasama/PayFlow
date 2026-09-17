package com.payflow.accounts.web;

import com.payflow.accounts.domain.Account;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Outward shape of an account — keeps the JPA entity out of the HTTP layer. */
public record AccountResponse(
        UUID id,
        String ownerName,
        BigDecimal balance,
        String currency,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerName(),
                account.getBalance(),
                account.getCurrency(),
                account.getCreatedAt());
    }
}
