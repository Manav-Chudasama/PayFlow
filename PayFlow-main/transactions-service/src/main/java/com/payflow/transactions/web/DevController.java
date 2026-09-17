package com.payflow.transactions.web;

import com.payflow.transactions.service.DevFaultService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEV-only controller. Exposes a single endpoint to arm a one-shot backend
 * failure, enabling safe-retry / idempotency demonstrations from the frontend.
 *
 * <p>How the demo works:
 * <ol>
 *   <li>Frontend calls POST /dev/simulate-error → flag armed.</li>
 *   <li>Frontend calls POST /transactions → DevFaultService fires → 503 returned, NO DB row written.</li>
 *   <li>Frontend retries POST /transactions with the SAME Idempotency-Key → flag already reset,
 *       idempotency pre-check finds nothing → transfer executes exactly once.</li>
 * </ol>
 */
@RestController
@RequestMapping("/dev")
public class DevController {

    private final DevFaultService devFaultService;

    public DevController(DevFaultService devFaultService) {
        this.devFaultService = devFaultService;
    }

    @PostMapping("/simulate-error")
    public ResponseEntity<Map<String, String>> simulateError() {
        devFaultService.armError();
        return ResponseEntity.ok(Map.of(
                "status", "armed",
                "message", "Next POST /transactions will fail with HTTP 503 (one-shot)."
        ));
    }
}
