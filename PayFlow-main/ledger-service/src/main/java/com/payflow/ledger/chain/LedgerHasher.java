package com.payflow.ledger.chain;

import com.payflow.ledger.domain.LedgerEntry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the SHA-256 hash that links a ledger entry to the one before it.
 *
 * <p>The hash covers every business field <em>and</em> the previous entry's hash,
 * so altering any value — or reordering/removing an entry — changes this entry's
 * hash and breaks every link after it. That is what makes the ledger
 * tamper-<em>evident</em>: tampering is still possible in the database, but it can
 * no longer go unnoticed.
 */
public final class LedgerHasher {

    /** previousHash of the very first entry. */
    public static final String GENESIS_HASH = "0".repeat(64);

    private LedgerHasher() {
    }

    public static String hash(LedgerEntry entry) {
        String canonical = String.join("|",
                nullSafe(entry.getPreviousHash()),
                String.valueOf(entry.getChainIndex()),
                String.valueOf(entry.getTransactionId()),
                String.valueOf(entry.getFromAccount()),
                String.valueOf(entry.getToAccount()),
                canonicalAmount(entry.getAmount()),
                nullSafe(entry.getCurrency()),
                nullSafe(entry.getDescription()),
                nullSafe(entry.getMerchant()),
                nullSafe(entry.getCategory()),
                String.valueOf(entry.getRecordedAt()));
        return sha256Hex(canonical);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Normalizes scale so the value hashed on write matches the value read back
     * from Postgres ({@code 250.00} and {@code 250.0} both canonicalize to "250").
     */
    private static String canonicalAmount(BigDecimal amount) {
        return amount == null ? "" : amount.stripTrailingZeros().toPlainString();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
