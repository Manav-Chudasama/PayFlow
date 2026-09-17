package com.payflow.accounts.service;

import java.util.UUID;

/** Thrown when an account id does not exist; translated to a 404 by the web layer. */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID accountId) {
        super("Account not found: " + accountId);
    }
}
