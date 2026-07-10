package com.interswitch.capstone.payments_api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import org.springframework.core.ParameterizedTypeReference;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    // Spring Boot 3's new modern HTTP Client
    private final RestClient restClient;

    public PaymentController() {
        // We configure it to talk to the internal Kubernetes DNS name of the Ledger Mock
        this.restClient = RestClient.create("http://ledger-mock:8080");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> request) {
        String accountId = (String) request.get("accountId");
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        
        // 1. Make a Synchronous Network Call to the Ledger Mock
        Map<String, Object> ledgerResponse = restClient.get()
                .uri("/api/v1/accounts/{accountId}/balance", accountId)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});


        // 2. Validate the balance
        BigDecimal balance = new BigDecimal(ledgerResponse.get("balance").toString());
        if (balance.compareTo(amount) < 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "reason", "Insufficient Funds"
            ));
        }

        // 3. Success! Return a Payment Reference
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "paymentReference", UUID.randomUUID().toString(),
                "amountDeducted", amount
        ));
    }
}