package com.payflow.transactions.web;

import com.payflow.transactions.domain.Transaction;
import com.payflow.transactions.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Outward shape of a transaction, including its final status and any failure reason. */
public record TransactionResponse(
        UUID id,
        UUID fromAccount,
        UUID toAccount,
        BigDecimal amount,
        String currency,
        String description,
        String merchant,
        TransactionStatus status,
        String failureReason,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction txn) {
        return new TransactionResponse(
                txn.getId(),
                txn.getFromAccount(),
                txn.getToAccount(),
                txn.getAmount(),
                txn.getCurrency(),
                txn.getDescription(),
                txn.getMerchant(),
                txn.getStatus(),
                txn.getFailureReason(),
                txn.getCreatedAt());
    }
}
