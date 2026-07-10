# payments-api

The **payments-api** is the core of the payment processing system. It is responsible for:

1. Receiving payment requests from the Gateway
2. Verifying account balance against the Ledger
3. Generating a unique payment reference
4. Publishing an asynchronous event to NATS
5. Returning an immediate response to the client

This service follows the **fire-and-forget** pattern — it responds to the client without waiting for settlement to complete. The settlement is handled asynchronously by the `settlement-worker`.

---

## Responsibilities

| Step | Action |
|---|---|
| 1 | Receive `POST /api/v1/payments` with `accountId` and `amount` |
| 2 | Call `ledger-mock` via HTTP to fetch current account balance |
| 3 | Validate that balance ≥ amount (reject with 400 if not) |
| 4 | Generate a UUID as the `paymentReference` |
| 5 | Publish `payments.completed` event to NATS |
| 6 | Return `200 SUCCESS` immediately |

---

## Tech Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 4.1.0 |
| Web | Spring MVC (Tomcat) |
| HTTP Client | Spring `RestClient` |
| Messaging | NATS via `jnats` 2.17.6 |
| State Store | Spring Data Redis |
| Java | 21 |

---

## API Endpoint

### POST `/api/v1/payments`

**Request Body:**

```json
{
  "accountId": "user123",
  "amount": 5000
}
```

**Success Response (200 OK):**

```json
{
  "status": "SUCCESS",
  "paymentReference": "961b55dd-13ec-4257-b1df-d599fb71a390",
  "amountDeducted": 5000
}
```

**Failure Response (400 Bad Request):**

```json
{
  "status": "FAILED",
  "reason": "Insufficient Funds"
}
```

---

## NATS Event

On a successful payment, the service publishes to the NATS subject `payments.completed`:

```json
{
  "paymentReference": "961b55dd-13ec-4257-b1df-d599fb71a390",
  "accountId": "user123",
  "amount": 5000
}
```

The `settlement-worker` subscribes to this subject to process the settlement asynchronously.

---

## Key Source Files

### `PaymentController.java`

The main REST controller at `/api/v1/payments`. On startup it:

- Creates a `RestClient` pointed at `http://ledger-mock:8080`
- Opens a persistent NATS connection to `nats://nats:4222`

On each `POST`:

1. Fetches balance from ledger via `GET /api/v1/accounts/{accountId}/balance`
2. Compares balance to requested amount
3. Publishes to NATS subject `payments.completed`
4. Returns the response

---

## Inter-Service Communication

```
payments-api
    │
    ├── HTTP GET → ledger-mock:8080/api/v1/accounts/{accountId}/balance
    │
    └── NATS PUBLISH → nats:4222 subject: payments.completed
```

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-webmvc` | REST controller, embedded Tomcat |
| `spring-boot-starter-actuator` | Health and info endpoints |
| `spring-boot-starter-data-redis` | Redis connection for state management |
| `io.nats:jnats:2.17.6` | NATS Java client for publishing events |

---

## Building & Deploying

**macOS / Linux:**
```bash
# Build
./mvnw clean install -pl payments-api -DskipTests

# Load image into Kind and restart
bash build-and-load-images.sh
kubectl rollout restart deployment payments-api

# Follow logs
kubectl logs deployment/payments-api -f
```

**Windows:**
```cmd
# Build
mvnw.cmd clean install -pl payments-api -DskipTests

# Load image into Kind and restart
bash build-and-load-images.sh
kubectl rollout restart deployment payments-api

# Follow logs
kubectl logs deployment/payments-api -f
```

---

## Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```
