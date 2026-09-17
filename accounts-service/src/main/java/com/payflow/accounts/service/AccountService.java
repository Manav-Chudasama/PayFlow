package com.payflow.accounts.service;

import com.payflow.accounts.config.CacheConfig;
import com.payflow.accounts.domain.Account;
import com.payflow.accounts.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account create(String ownerName, BigDecimal initialBalance, String currency) {
        Account account = new Account();
        account.setOwnerName(ownerName);
        account.setBalance(initialBalance != null ? initialBalance : BigDecimal.ZERO);
        if (currency != null && !currency.isBlank()) {
            account.setCurrency(currency);
        }
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    /**
     * Cached read used by the GET endpoint. On a hit the account is served from
     * Redis without touching Postgres; every write evicts the key (see credit/debit),
     * so a balance change is never served stale. The write path deliberately uses the
     * uncached {@link #get} so it always mutates a fresh, managed entity.
     */
    @Cacheable(cacheNames = CacheConfig.ACCOUNTS, key = "#id")
    public Account getCached(UUID id) {
        return get(id);
    }

    /** Add {@code amount} to the account balance. */
    @CacheEvict(cacheNames = CacheConfig.ACCOUNTS, key = "#id")
    @Transactional
    public Account credit(UUID id, BigDecimal amount) {
        Account account = get(id);
        account.setBalance(account.getBalance().add(amount));
        return accountRepository.save(account);
    }

    /**
     * Subtract {@code amount} from the balance, rejecting the debit if it would go
     * negative. Throws {@link InsufficientFundsException} rather than persisting a
     * bad state — the funds check is authoritative here.
     */
    @CacheEvict(cacheNames = CacheConfig.ACCOUNTS, key = "#id")
    @Transactional
    public Account debit(UUID id, BigDecimal amount) {
        Account account = get(id);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(id, account.getBalance(), amount);
        }
        account.setBalance(account.getBalance().subtract(amount));
        return accountRepository.save(account);
    }
}
