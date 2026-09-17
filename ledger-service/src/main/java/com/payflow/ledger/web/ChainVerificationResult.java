package com.payflow.ledger.web;

import java.util.UUID;

/**
 * Outcome of walking the ledger's hash chain. {@code valid == false} means the
 * stored data no longer matches its hashes — i.e. something was altered, removed,
 * or reordered after it was written.
 */
public record ChainVerificationResult(
        boolean valid,
        long entriesChecked,
        String message,
        UUID firstInvalidEntryId,
        Long firstInvalidChainIndex
) {
    public static ChainVerificationResult valid(long checked) {
        return new ChainVerificationResult(true, checked,
                "Chain intact: all " + checked + " entries verified against their hashes.",
                null, null);
    }

    public static ChainVerificationResult broken(long checked, String reason,
                                                 UUID entryId, Long chainIndex) {
        return new ChainVerificationResult(false, checked, reason, entryId, chainIndex);
    }
}
