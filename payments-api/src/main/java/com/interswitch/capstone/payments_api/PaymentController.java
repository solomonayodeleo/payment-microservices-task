package com.interswitch.capstone.payments_api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;

import io.nats.client.Connection;
import io.nats.client.Nats;
import java.nio.charset.StandardCharsets;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final RestClient restClient;
    private Connection natsConnection;

    public PaymentController() {
        this.restClient = RestClient.create("http://ledger-mock:8080");
        
        // Connect to our NATS post office!
        try {
            this.natsConnection = Nats.connect("nats://nats:4222");
        } catch (Exception e) {
            System.err.println("Failed to connect to NATS: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> request) {
        String accountId = (String) request.get("accountId");
        BigDecimal amount = new BigDecimal(request.get("amount").toString());

        // 1. Check Ledger
        Map<String, Object> ledgerResponse = restClient.get()
                .uri("/api/v1/accounts/{accountId}/balance", accountId)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        // 2. Validate Funds
        BigDecimal balance = new BigDecimal(ledgerResponse.get("balance").toString());
        if (balance.compareTo(amount) < 0) {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "reason", "Insufficient Funds"));
        }

        String paymentReference = UUID.randomUUID().toString();

        // 3. PUBLISH TO NATS! (Asynchronous Event)
        if (natsConnection != null) {
            String eventPayload = String.format("{\"paymentReference\":\"%s\", \"accountId\":\"%s\", \"amount\":%s}", 
                                                paymentReference, accountId, amount);
            natsConnection.publish("payments.completed", eventPayload.getBytes(StandardCharsets.UTF_8));
            System.out.println("Published Event: " + eventPayload);
        }

        // 4. Return immediately to the user
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "paymentReference", paymentReference,
                "amountDeducted", amount
        ));
    }
}
