package com.payflow.ledger.web;

import java.util.List;

/**
 * Wrapper around a list of ledger entries. Caching a concrete type (rather than a
 * raw {@code List<...>}) lets the Redis value serializer bind to a known class,
 * avoiding polymorphic type headers.
 */
public record LedgerPage(List<LedgerEntryResponse> entries) {
}
