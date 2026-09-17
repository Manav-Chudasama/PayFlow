package com.payflow.transactions.service;

import com.payflow.transactions.client.AccountView;
import com.payflow.transactions.client.AccountsClient;
import com.payflow.transactions.domain.Transaction;
import com.payflow.transactions.domain.TransactionStatus;
import com.payflow.transactions.event.TransactionEvent;
import com.payflow.transactions.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * The two-hop transfer: a synchronous debit/credit against Accounts over REST,
 * followed by an asynchronous publish to Kafka once (and only once) the money has
 * actually moved. Deliberately not wrapped in a single JPA transaction — each
 * status change is committed on its own so a FAILED transfer is still recorded
 * even though the request ultimately throws.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final String TOPIC = "transactions";

    private final TransactionRepository repository;
    private final AccountsClient accountsClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DevFaultService devFaultService;

    public TransactionService(TransactionRepository repository,
                              AccountsClient accountsClient,
                              KafkaTemplate<String, Object> kafkaTemplate,
                              DevFaultService devFaultService) {
        this.repository = repository;
        this.accountsClient = accountsClient;
        this.kafkaTemplate = kafkaTemplate;
        this.devFaultService = devFaultService;
    }

    public Transaction transfer(UUID fromAccount, UUID toAccount, BigDecimal amount,
                                String description, String merchant, String idempotencyKey) {
        // DEV: One-shot simulated failure. Fires BEFORE the idempotency pre-check so
        // that no DB row is written on this attempt. A retry with the same key will
        // find nothing in the pre-check and process the transfer normally — exactly once.
        if (devFaultService.shouldFail()) {
            throw new SimulatedBackendException(
                    "DEV: Simulated backend failure (one-shot). Retry with the same Idempotency-Key.");
        }

        // Idempotency: a retried request with a known key returns the original result
        // rather than moving money a second time.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Transaction> existing = repository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotency-Key {} already seen; returning existing transaction {}",
                        idempotencyKey, existing.get().getId());
                return existing.get();
            }
        }

        if (fromAccount.equals(toAccount)) {
            throw new InvalidTransferException("fromAccount and toAccount must be different");
        }

        Transaction txn = openPending(fromAccount, toAccount, amount, description, merchant, idempotencyKey);

        // Hop 1 (synchronous): debit the sender. If this fails there is nothing to
        // undo and, crucially, no event is published.
        AccountView debited;
        try {
            debited = accountsClient.debit(fromAccount, amount);
        } catch (RestClientException e) {
            return markFailed(txn, "debit failed: " + e.getMessage());
        }

        // Hop 2 (synchronous): credit the receiver. If this fails after the debit
        // succeeded, compensate by refunding the sender so money is never lost.
        try {
            accountsClient.credit(toAccount, amount);
        } catch (RestClientException e) {
            compensate(fromAccount, amount);
            return markFailed(txn, "credit failed: " + e.getMessage());
        }

        txn.setCurrency(debited.currency());
        txn.setStatus(TransactionStatus.COMPLETED);
        txn = repository.save(txn);

        publish(txn);
        return txn;
    }

    private Transaction openPending(UUID fromAccount, UUID toAccount, BigDecimal amount,
                                    String description, String merchant, String idempotencyKey) {
        Transaction txn = new Transaction();
        txn.setFromAccount(fromAccount);
        txn.setToAccount(toAccount);
        txn.setAmount(amount);
        txn.setDescription(description);
        txn.setMerchant(merchant);
        txn.setStatus(TransactionStatus.PENDING);
        txn.setIdempotencyKey(idempotencyKey);
        try {
            return repository.save(txn);
        } catch (DataIntegrityViolationException e) {
            // Lost a race on the unique idempotency key; return the winner's row.
            return repository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> e);
        }
    }

    private Transaction markFailed(Transaction txn, String reason) {
        log.warn("Transaction {} failed: {}", txn.getId(), reason);
        txn.setStatus(TransactionStatus.FAILED);
        txn.setFailureReason(reason);
        Transaction saved = repository.save(txn);
        throw new TransferFailedException(saved, reason);
    }

    private void compensate(UUID fromAccount, BigDecimal amount) {
        try {
            accountsClient.credit(fromAccount, amount);
        } catch (RestClientException e) {
            // Best effort. In a real system this would go to a reconciliation queue.
            log.error("Compensating refund to {} failed: {}", fromAccount, e.getMessage());
        }
    }

    private void publish(Transaction txn) {
        TransactionEvent event = new TransactionEvent(
                txn.getId(), txn.getFromAccount(), txn.getToAccount(),
                txn.getAmount(), txn.getCurrency(),
                txn.getDescription(), txn.getMerchant(), Instant.now());
        kafkaTemplate.send(TOPIC, txn.getId().toString(), event);
        log.info("Published transaction.completed for {}", txn.getId());
    }

    public Transaction get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }
}
