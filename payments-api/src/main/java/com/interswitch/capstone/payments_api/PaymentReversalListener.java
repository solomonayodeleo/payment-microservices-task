package com.interswitch.capstone.payments_api;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class PaymentReversalListener {

    private Connection natsConnection;
    private final RestClient restClient;

    public PaymentReversalListener() {
        this.restClient = RestClient.create("http://ledger-mock:8080");
    }

    @PostConstruct
    public void init() {
        try {
            // 1. Connect to NATS
            this.natsConnection = Nats.connect("nats://nats:4222");

            // 2. Create dispatcher for reversing payments
            Dispatcher dispatcher = natsConnection.createDispatcher((msg) -> {
                String payload = new String(msg.getData(), StandardCharsets.UTF_8);
                System.out.println("\n⚠️ [PAYMENTS API] Received Compensation Event on '" + msg.getSubject() + "': " + payload);
                
                try {
                    // Quick and dirty manual parsing for simplicity
                    String accountId = payload.split("\"accountId\":\"")[1].split("\"")[0];
                    String amountStr = payload.split("\"amount\":")[1].split("}")[0].trim();
                    
                    System.out.println("🔄 [PAYMENTS API] Refunding " + amountStr + " to account " + accountId + "...");
                    
                    // Call the ledger-mock to refund the funds
                    Map<String, Object> response = restClient.post()
                            .uri("/api/v1/accounts/{accountId}/refund", accountId)
                            .body(Map.of("amount", amountStr))
                            .retrieve()
                            .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
                            
                    System.out.println("✅ [PAYMENTS API] Compensation successful: " + response);
                    
                } catch (Exception ex) {
                    System.err.println("❌ [PAYMENTS API] Failed to process compensation: " + ex.getMessage());
                }
            });

            // 3. Subscribe to the reversal event
            dispatcher.subscribe("payments.reversed");

            System.out.println("🚀 Payments API is listening to NATS on 'payments.reversed' for Saga compensations...");

        } catch (Exception e) {
            System.err.println("❌ Failed to connect to NATS in PaymentReversalListener: " + e.getMessage());
        }
    }
}
