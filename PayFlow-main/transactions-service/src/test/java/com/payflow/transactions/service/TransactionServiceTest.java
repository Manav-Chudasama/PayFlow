package com.payflow.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payflow.transactions.client.AccountView;
import com.payflow.transactions.client.AccountsClient;
import com.payflow.transactions.domain.Transaction;
import com.payflow.transactions.domain.TransactionStatus;
import com.payflow.transactions.event.TransactionEvent;
import com.payflow.transactions.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository repository;
    @Mock
    private AccountsClient accountsClient;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private DevFaultService devFaultService;

    @InjectMocks
    private TransactionService service;

    @Test
    void rejectsTransferToSameAccount() {
        UUID account = UUID.randomUUID();

        assertThatThrownBy(() ->
                service.transfer(account, account, new BigDecimal("100"), null, null, null))
                .isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void completedTransferMovesMoneyAndPublishesEvent() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100");

        // Simulate JPA assigning the @GeneratedValue id on first save.
        when(repository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
        when(accountsClient.debit(from, amount))
                .thenReturn(new AccountView(from, "Alice", new BigDecimal("900"), "INR", Instant.now()));
        when(accountsClient.credit(to, amount))
                .thenReturn(new AccountView(to, "Bob", new BigDecimal("100"), "INR", Instant.now()));

        Transaction result = service.transfer(from, to, amount, "Food delivery", "Swiggy", null);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.getMerchant()).isEqualTo("Swiggy");
        // The completed transfer publishes exactly one event to the transactions topic.
        verify(kafkaTemplate).send(eq("transactions"), anyString(), any(TransactionEvent.class));
    }
}
