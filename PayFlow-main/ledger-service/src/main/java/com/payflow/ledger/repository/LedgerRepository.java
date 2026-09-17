package com.payflow.ledger.repository;

import com.payflow.ledger.domain.LedgerEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findAllByOrderByRecordedAtDesc();

    /** Entries where the account is either the sender or the receiver, newest first. */
    List<LedgerEntry> findByFromAccountOrToAccountOrderByRecordedAtDesc(UUID fromAccount, UUID toAccount);

    boolean existsByTransactionId(UUID transactionId);

    /** Tail of the hash chain — the entry a new one links back to. */
    Optional<LedgerEntry> findTopByOrderByChainIndexDesc();

    /** The whole chain in write order, for verification. */
    List<LedgerEntry> findAllByOrderByChainIndexAsc();
}
