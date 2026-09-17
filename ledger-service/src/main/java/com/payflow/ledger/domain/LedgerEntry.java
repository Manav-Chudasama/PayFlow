package com.payflow.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An immutable audit record of a completed transfer. The ledger is
 * <strong>append-only</strong> — there are deliberately no update or delete
 * paths; that immutability is the whole point of a ledger.
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Source transaction id. Unique so a redelivered event can't be recorded twice. */
    @Column(nullable = false, unique = true)
    private UUID transactionId;

    @Column(nullable = false)
    private UUID fromAccount;

    @Column(nullable = false)
    private UUID toAccount;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    /** Context carried from the transfer; the AI categorizer classifies on these. */
    private String description;

    private String merchant;

    /** Enrichment label; populated by the AI categorizer in Phase 4. */
    private String category;

    @Column(nullable = false, updatable = false)
    private Instant recordedAt;

    // --- tamper-evidence: hash chain ---------------------------------------

    /**
     * Position in the hash chain, starting at 0. Unique, so a concurrent insert
     * can't silently fork the chain — and a gap reveals a deleted entry.
     */
    @Column(unique = true)
    private Long chainIndex;

    /** {@code entryHash} of the preceding entry (all zeros for the genesis entry). */
    @Column(length = 64)
    private String previousHash;

    /**
     * SHA-256 over this entry's content plus {@link #previousHash}. Changing any
     * field changes this hash, which breaks every link after it.
     */
    @Column(length = 64)
    private String entryHash;

    @PrePersist
    void onCreate() {
        if (recordedAt == null) {
            // Microsecond precision matches what Postgres stores, so the value used
            // to compute the hash survives a DB round-trip unchanged.
            recordedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }
}
