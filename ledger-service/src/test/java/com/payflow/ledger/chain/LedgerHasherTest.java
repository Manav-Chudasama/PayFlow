package com.payflow.ledger.chain;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.ledger.domain.LedgerEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerHasherTest {

    private LedgerEntry entry() {
        LedgerEntry e = new LedgerEntry();
        e.setTransactionId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        e.setFromAccount(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        e.setToAccount(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        e.setAmount(new BigDecimal("250.00"));
        e.setCurrency("INR");
        e.setMerchant("Swiggy");
        e.setDescription("Food delivery");
        e.setCategory("food");
        e.setRecordedAt(Instant.parse("2026-06-15T10:15:30Z"));
        e.setChainIndex(0L);
        e.setPreviousHash(LedgerHasher.GENESIS_HASH);
        return e;
    }

    @Test
    void hashIsDeterministicAndFullLength() {
        assertThat(LedgerHasher.hash(entry()))
                .isEqualTo(LedgerHasher.hash(entry()))
                .hasSize(64);
    }

    @Test
    void changingTheAmountChangesTheHash() {
        String original = LedgerHasher.hash(entry());
        LedgerEntry tampered = entry();
        tampered.setAmount(new BigDecimal("999999.00"));
        assertThat(LedgerHasher.hash(tampered)).isNotEqualTo(original);
    }

    @Test
    void changingTheCategoryOrMerchantChangesTheHash() {
        String original = LedgerHasher.hash(entry());
        LedgerEntry tampered = entry();
        tampered.setCategory("salary");
        tampered.setMerchant("TOTALLY LEGIT");
        assertThat(LedgerHasher.hash(tampered)).isNotEqualTo(original);
    }

    @Test
    void relinkingToADifferentPredecessorChangesTheHash() {
        String original = LedgerHasher.hash(entry());
        LedgerEntry moved = entry();
        moved.setPreviousHash("a".repeat(64));
        assertThat(LedgerHasher.hash(moved)).isNotEqualTo(original);
    }

    @Test
    void reorderingChangesTheHash() {
        String original = LedgerHasher.hash(entry());
        LedgerEntry moved = entry();
        moved.setChainIndex(7L);
        assertThat(LedgerHasher.hash(moved)).isNotEqualTo(original);
    }

    @Test
    void amountScaleIsCanonicalizedSoADbRoundTripDoesNotBreakTheChain() {
        // 250.00 (as stored by Postgres numeric) and 250.0 must hash identically.
        LedgerEntry a = entry();
        a.setAmount(new BigDecimal("250.00"));
        LedgerEntry b = entry();
        b.setAmount(new BigDecimal("250.0"));
        assertThat(LedgerHasher.hash(a)).isEqualTo(LedgerHasher.hash(b));
    }
}
