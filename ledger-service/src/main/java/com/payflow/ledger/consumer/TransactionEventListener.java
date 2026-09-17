package com.payflow.ledger.consumer;

import com.payflow.ledger.categorizer.TransactionCategorizer;
import com.payflow.ledger.domain.LedgerEntry;
import com.payflow.ledger.event.TransactionEvent;
import com.payflow.ledger.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The ledger is a pure consumer: it never writes via REST, it only listens on the
 * {@code transactions} topic and appends an audit record. Because the group id is
 * stable, events published while this service is down wait on the topic and are
 * recorded when it comes back up.
 */
@Component
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final LedgerService ledgerService;
    private final TransactionCategorizer categorizer;

    public TransactionEventListener(LedgerService ledgerService,
                                    TransactionCategorizer categorizer) {
        this.ledgerService = ledgerService;
        this.categorizer = categorizer;
    }

    @KafkaListener(topics = "transactions", groupId = "ledger-service")
    public void onTransaction(TransactionEvent event) {
        // Idempotent consume: a redelivered event for a transaction we've already
        // recorded is skipped rather than duplicated.
        if (ledgerService.exists(event.transactionId())) {
            log.info("Ledger already has transaction {}; skipping", event.transactionId());
            return;
        }
        String category = categorizer.categorize(event);   // AI enrichment (Phase 4)
        LedgerEntry entry = toEntry(event, category);
        ledgerService.record(entry);   // saves + evicts the affected read caches
        log.info("Recorded ledger entry for transaction {} (category={})",
                event.transactionId(), category);
    }

    private LedgerEntry toEntry(TransactionEvent event, String category) {
        LedgerEntry entry = new LedgerEntry();
        entry.setTransactionId(event.transactionId());
        entry.setFromAccount(event.fromAccount());
        entry.setToAccount(event.toAccount());
        entry.setAmount(event.amount());
        entry.setCurrency(event.currency());
        entry.setDescription(event.description());
        entry.setMerchant(event.merchant());
        entry.setCategory(category);
        return entry;
    }
}
