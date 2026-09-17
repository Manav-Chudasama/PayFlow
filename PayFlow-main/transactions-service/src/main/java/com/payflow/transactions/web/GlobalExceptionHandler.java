package com.payflow.transactions.web;

import com.payflow.transactions.service.InvalidTransferException;
import com.payflow.transactions.service.SimulatedBackendException;
import com.payflow.transactions.service.TransactionNotFoundException;
import com.payflow.transactions.service.TransferFailedException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps failures to deliberate 4xx responses. A failed transfer still returns the
 * transaction (with status FAILED) so the caller sees the final state, not a 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransferFailedException.class)
    public ResponseEntity<TransactionResponse> handleTransferFailed(TransferFailedException ex) {
        // 422: request was well-formed but the transfer could not be carried out.
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(TransactionResponse.from(ex.getTransaction()));
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(TransactionNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SimulatedBackendException.class)
    public ResponseEntity<Map<String, Object>> handleSimulatedError(SimulatedBackendException ex) {
        // DEV: one-shot simulated 503 — no DB row was written, retry with same key is safe.
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(InvalidTransferException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(InvalidTransferException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
