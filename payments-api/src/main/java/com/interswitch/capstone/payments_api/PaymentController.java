package com.interswitch.capstone.payments_api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import io.nats.client.Connection;
import io.nats.client.Nats;
import java.nio.charset.StandardCharsets;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final CircuitBreaker circuitBreaker;
    private Connection natsConnection;

    public PaymentController(StringRedisTemplate redisTemplate, @Value("${NATS_URL:nats://nats:4222}") String natsUrl) {
        this.restClient = RestClient.create("http://ledger-mock:8080");
        this.redisTemplate = redisTemplate;
        
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slidingWindowSize(10)
            .permittedNumberOfCallsInHalfOpenState(3)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .build();
        this.circuitBreaker = CircuitBreaker.of("ledger", config);
        
        // Connect to our NATS post office!
        try {
            this.natsConnection = Nats.connect(natsUrl);
        } catch (Exception e) {
            System.err.println("Failed to connect to NATS: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> processPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> request) {

        // 0. Idempotency Check
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            String redisKey = "payment:idempotency:" + idempotencyKey;
            Boolean isAbsent = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSED", Duration.ofHours(24));
            
            if (Boolean.FALSE.equals(isAbsent)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("status", "FAILED", "reason", "Duplicate Request. A payment with this Idempotency-Key was already processed."));
            }
        }

        String accountId = (String) request.get("accountId");
        BigDecimal amount = new BigDecimal(request.get("amount").toString());

        // 1. Check Ledger (Wrapped in Circuit Breaker)
        Map<String, Object> ledgerResponse;
        
        try {
            ledgerResponse = circuitBreaker.executeSupplier(() -> restClient.get()
                    .uri("/api/v1/accounts/{accountId}/balance", accountId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            // Circuit Breaker opened or ledger is down
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "FAILED", "reason", "Ledger Service Unavailable", "details", e.getMessage()));
        }

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
