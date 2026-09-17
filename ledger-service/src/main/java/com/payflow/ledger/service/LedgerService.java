package com.payflow.ledger.service;

import com.payflow.ledger.chain.LedgerHasher;
import com.payflow.ledger.config.CacheConfig;
import com.payflow.ledger.domain.LedgerEntry;
import com.payflow.ledger.repository.LedgerRepository;
import com.payflow.ledger.web.ChainVerificationResult;
import com.payflow.ledger.web.LedgerEntryResponse;
import com.payflow.ledger.web.LedgerPage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

/**
 * Read/write access to the ledger with Redis caching. Reads are cached; a write
 * (a new entry from the Kafka consumer) evicts the "all" list and both the
 * sender's and receiver's per-account lists, so a subsequent read always reflects
 * the freshly recorded transaction.
 */
@Service
public class LedgerService {

    private final LedgerRepository ledgerRepository;

    public LedgerService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Cacheable(cacheNames = CacheConfig.LEDGER_ALL)
    public LedgerPage getAll() {
        return page(ledgerRepository.findAllByOrderByRecordedAtDesc());
    }

    @Cacheable(cacheNames = CacheConfig.LEDGER_BY_ACCOUNT, key = "#accountId")
    public LedgerPage getByAccount(UUID accountId) {
        return page(ledgerRepository.findByFromAccountOrToAccountOrderByRecordedAtDesc(accountId, accountId));
    }

    public boolean exists(UUID transactionId) {
        return ledgerRepository.existsByTransactionId(transactionId);
    }

    /**
     * Link the entry into the hash chain and persist it, then invalidate the caches
     * it affects: the full list, plus the per-account lists for both accounts.
     *
     * <p>Chaining is done here (not in a JPA callback) so the exact values that get
     * stored are the ones that get hashed.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.LEDGER_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.LEDGER_BY_ACCOUNT, key = "#entry.fromAccount"),
            @CacheEvict(cacheNames = CacheConfig.LEDGER_BY_ACCOUNT, key = "#entry.toAccount")
    })
    public LedgerEntry record(LedgerEntry entry) {
        LedgerEntry previous = ledgerRepository.findTopByOrderByChainIndexDesc().orElse(null);
        entry.setChainIndex(previous == null ? 0L : previous.getChainIndex() + 1);
        entry.setPreviousHash(previous == null ? LedgerHasher.GENESIS_HASH : previous.getEntryHash());
        if (entry.getRecordedAt() == null) {
            entry.setRecordedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        }
        entry.setEntryHash(LedgerHasher.hash(entry));
        return ledgerRepository.save(entry);
    }

    /**
     * Walk the chain from genesis and re-derive every hash. Detects three kinds of
     * tampering: an edited field (recomputed hash differs), a deleted entry (gap in
     * chainIndex, and the next entry's previousHash no longer matches), and a
     * reordered entry (chainIndex is part of the hash).
     */
    public ChainVerificationResult verifyChain() {
        List<LedgerEntry> chain = ledgerRepository.findAllByOrderByChainIndexAsc();
        String expectedPrevious = LedgerHasher.GENESIS_HASH;
        long expectedIndex = 0;
        long checked = 0;

        for (LedgerEntry entry : chain) {
            if (entry.getChainIndex() == null || entry.getEntryHash() == null) {
                return ChainVerificationResult.broken(checked,
                        "Entry is not part of the hash chain (missing chainIndex/entryHash).",
                        entry.getId(), entry.getChainIndex());
            }
            if (entry.getChainIndex() != expectedIndex) {
                return ChainVerificationResult.broken(checked,
                        "Chain break: expected chainIndex " + expectedIndex + " but found "
                                + entry.getChainIndex() + " — an entry was deleted or reordered.",
                        entry.getId(), entry.getChainIndex());
            }
            if (!expectedPrevious.equals(entry.getPreviousHash())) {
                return ChainVerificationResult.broken(checked,
                        "Chain break at index " + entry.getChainIndex()
                                + ": previousHash does not match the preceding entry's hash.",
                        entry.getId(), entry.getChainIndex());
            }
            String recomputed = LedgerHasher.hash(entry);
            if (!recomputed.equals(entry.getEntryHash())) {
                return ChainVerificationResult.broken(checked,
                        "Tampering detected at index " + entry.getChainIndex()
                                + ": stored hash does not match the entry's content.",
                        entry.getId(), entry.getChainIndex());
            }
            expectedPrevious = entry.getEntryHash();
            expectedIndex++;
            checked++;
        }
        return ChainVerificationResult.valid(checked);
    }

    private LedgerPage page(List<LedgerEntry> entries) {
        return new LedgerPage(entries.stream().map(LedgerEntryResponse::from).toList());
    }
}
