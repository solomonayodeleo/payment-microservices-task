package com.interswitch.capstone.settlement_worker;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

@Service
public class SettlementListener {

    private Connection natsConnection;
    private final String natsUrl;
    
    public SettlementListener(@Value("${NATS_URL:nats://nats:4222}") String natsUrl) {
        this.natsUrl = natsUrl;
    }

    @PostConstruct
    public void init() {
        try {
            // 1. Connect to NATS
            this.natsConnection = Nats.connect(natsUrl);

            // 2. Create a dispatcher to listen for messages
            Dispatcher dispatcher = natsConnection.createDispatcher((msg) -> {
                String payload = new String(msg.getData(), StandardCharsets.UTF_8);
                System.out.println("\n✅ [SETTLEMENT WORKER] Received Message on '" + msg.getSubject() + "': " + payload);
                
                // Simulate a slow database save or external bank transfer (2 seconds)
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Simulate Failure Condition for Saga Pattern
                if (payload.contains("\"accountId\":\"user-fail\"")) {
                    System.err.println("❌ [SETTLEMENT WORKER] Settlement FAILED for payload: " + payload);
                    System.out.println("🔄 [SETTLEMENT WORKER] Triggering Compensating Transaction (payments.reversed)...");
                    natsConnection.publish("payments.reversed", payload.getBytes(StandardCharsets.UTF_8));
                } else {
                    System.out.println("✅ [SETTLEMENT WORKER] Settlement Processed Successfully!\n");
                }
            });

            // 3. Subscribe to the exact subject the Payments API is broadcasting
            dispatcher.subscribe("payments.completed");

            System.out.println("🚀 Settlement Worker is listening to NATS on 'payments.completed'...");

        } catch (Exception e) {
            System.err.println("❌ Failed to connect to NATS: " + e.getMessage());
        }
    }
}
