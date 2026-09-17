package com.payflow.transactions.client;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over the {@link RestClient} pointed at the Accounts service. A 4xx
 * from Accounts (e.g. insufficient funds -> 422) surfaces as a
 * {@link org.springframework.web.client.RestClientResponseException}, which the
 * transfer flow catches to mark the transaction FAILED.
 */
@Component
public class AccountsClient {

    private final RestClient restClient;

    public AccountsClient(RestClient accountsClient) {
        this.restClient = accountsClient;
    }

    public AccountView debit(UUID accountId, BigDecimal amount) {
        return call("/accounts/{id}/debit", accountId, amount);
    }

    public AccountView credit(UUID accountId, BigDecimal amount) {
        return call("/accounts/{id}/credit", accountId, amount);
    }

    private AccountView call(String path, UUID accountId, BigDecimal amount) {
        return restClient.post()
                .uri(path, accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AmountBody(amount))
                .retrieve()
                .body(AccountView.class);
    }

    private record AmountBody(BigDecimal amount) {}
}
