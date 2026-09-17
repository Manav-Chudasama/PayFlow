package com.payflow.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payflow.accounts.domain.Account;
import com.payflow.accounts.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private Account accountWithBalance(UUID id, String balance) {
        Account account = new Account();
        account.setId(id);
        account.setOwnerName("Test");
        account.setBalance(new BigDecimal(balance));
        account.setCurrency("INR");
        return account;
    }

    @Test
    void debitThrowsWhenBalanceIsInsufficient() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.of(accountWithBalance(id, "100.00")));

        assertThatThrownBy(() -> accountService.debit(id, new BigDecimal("500.00")))
                .isInstanceOf(InsufficientFundsException.class);

        // A rejected debit must not persist a changed balance.
        verify(accountRepository, never()).save(any());
    }

    @Test
    void debitReducesBalanceWhenSufficient() {
        UUID id = UUID.randomUUID();
        Account account = accountWithBalance(id, "100.00");
        when(accountRepository.findById(id)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account result = accountService.debit(id, new BigDecimal("30.00"));

        assertThat(result.getBalance()).isEqualByComparingTo("70.00");
    }

    @Test
    void creditAddsToBalance() {
        UUID id = UUID.randomUUID();
        Account account = accountWithBalance(id, "100.00");
        when(accountRepository.findById(id)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account result = accountService.credit(id, new BigDecimal("25.50"));

        assertThat(result.getBalance()).isEqualByComparingTo("125.50");
    }

    @Test
    void getThrowsWhenAccountMissing() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.get(id))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
