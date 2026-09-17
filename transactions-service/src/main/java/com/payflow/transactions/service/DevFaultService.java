package com.payflow.transactions.service;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

/**
 * DEV utility: holds a one-shot flag that makes the next transaction attempt
 * fail with a simulated 503, then automatically resets itself. Used to
 * demonstrate that retrying with the same Idempotency-Key is safe after a
 * transient error — the retry finds no existing row and processes normally.
 */
@Service
public class DevFaultService {

    private final AtomicBoolean shouldFailOnce = new AtomicBoolean(false);

    /** Arms the one-shot error flag. Next call to {@link #shouldFail()} returns true. */
    public void armError() {
        shouldFailOnce.set(true);
    }

    /**
     * Returns {@code true} if the error flag was armed, and atomically resets it to
     * {@code false} so subsequent calls are not affected (guaranteed one-shot).
     */
    public boolean shouldFail() {
        return shouldFailOnce.getAndSet(false);
    }
}
