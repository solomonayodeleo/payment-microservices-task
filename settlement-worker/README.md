# settlement-worker

The **settlement-worker** is a background microservice that listens for payment events published to NATS and processes them asynchronously. It embodies the **event-driven** part of the architecture — it is completely decoupled from the payments-api and only communicates through the message broker.

When a payment is authorised, the `payments-api` immediately returns a success response to the client and fires an event to NATS. The `settlement-worker` picks up that event and handles the actual settlement work — such as debiting the account, calling an external bank, or writing to an audit log — without affecting the end user's experience.

---

## Responsibilities

- Connect to NATS on startup and subscribe to the `payments.completed` subject
- Process each incoming settlement event
- Simulate a 2-second settlement operation (represents a real bank call or database write)
- Log the result

---

## Tech Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 4.1.0 |
| Web | Spring MVC (Tomcat) — for health/actuator endpoints |
| Messaging | NATS via `jnats` 2.17.6 |
| Java | 21 |

---

## NATS Event — `payments.completed`

The worker subscribes to this subject. Each message has the following JSON payload:

```json
{
  "paymentReference": "961b55dd-13ec-4257-b1df-d599fb71a390",
  "accountId": "user123",
  "amount": 5000
}
```

---

## How it works

On application startup, `SettlementListener.java` (annotated with `@PostConstruct`) runs automatically:

1. Opens a NATS connection to `nats://nats:4222`
2. Creates a `Dispatcher` with a message-handling lambda
3. Subscribes the dispatcher to `payments.completed`
4. Waits for incoming messages indefinitely

For each message received:
- Logs the raw JSON payload
- Sleeps for 2 seconds (simulating real settlement work)
- Logs a success confirmation

---

## Key Source File

### `SettlementListener.java`

```java
@Service
public class SettlementListener {

    @PostConstruct
    public void init() {
        this.natsConnection = Nats.connect("nats://nats:4222");

        Dispatcher dispatcher = natsConnection.createDispatcher((msg) -> {
            String payload = new String(msg.getData(), StandardCharsets.UTF_8);
            System.out.println("✅ Received: " + payload);
            Thread.sleep(2000); // Simulate work
            System.out.println("✅ Settlement Processed Successfully!");
        });

        dispatcher.subscribe("payments.completed");
    }
}
```

---

## Message Flow

```
payments-api
    │
    │  PUBLISH → NATS subject: "payments.completed"
    ▼
   NATS :4222
    │
    │  SUBSCRIBE ← settlement-worker (always listening)
    ▼
settlement-worker
    │
    │  Processes event (2s simulated work)
    │  Logs success
```

---

## Sample Log Output

```
🚀 Settlement Worker is listening to NATS on 'payments.completed'...

✅ [SETTLEMENT WORKER] Received Message on 'payments.completed':
   {"paymentReference":"961b55dd-13ec-4257-b1df-d599fb71a390", "accountId":"user123", "amount":5000}

✅ [SETTLEMENT WORKER] Settlement Processed Successfully!
```

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-webmvc` | Embedded Tomcat (keeps the process alive) |
| `spring-boot-starter-actuator` | Health and info endpoints |
| `io.nats:jnats:2.17.6` | NATS Java client for subscribing to events |

---

## Building & Deploying

**macOS / Linux:**
```bash
./mvnw clean install -pl settlement-worker -DskipTests
bash build-and-load-images.sh
kubectl rollout restart deployment settlement-worker

# Watch live settlement processing
kubectl logs deployment/settlement-worker -f
```

**Windows:**
```cmd
mvnw.cmd clean install -pl settlement-worker -DskipTests
bash build-and-load-images.sh
kubectl rollout restart deployment settlement-worker

# Watch live settlement processing
kubectl logs deployment/settlement-worker -f
```

---

## Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Future Improvements

In a real implementation, this service would:

- Persist each settlement record to a database
- Implement error handling and dead-letter queues (DLQ)
- Implement the **Saga Pattern** with compensation: if settlement fails, publish a `payments.reversed` event to trigger a refund
- Add retry logic with exponential backoff
- Emit metrics (settlement throughput, failure rate) to Prometheus
