package com.payflow.ledger.web;

import com.payflow.ledger.domain.LedgerEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read-only view of an immutable ledger entry. */
public record LedgerEntryResponse(
        UUID id,
        UUID transactionId,
        UUID fromAccount,
        UUID toAccount,
        BigDecimal amount,
        String currency,
        String description,
        String merchant,
        String category,
        Instant recordedAt,
        Long chainIndex,
        String previousHash,
        String entryHash
) {
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransactionId(),
                entry.getFromAccount(),
                entry.getToAccount(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getDescription(),
                entry.getMerchant(),
                entry.getCategory(),
                entry.getRecordedAt(),
                entry.getChainIndex(),
                entry.getPreviousHash(),
                entry.getEntryHash());
    }
}
