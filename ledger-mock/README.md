# ledger-mock

The **ledger-mock** is a lightweight stub service that simulates a core banking ledger system. In a real fintech system, this would be an integration point with an actual core banking platform. For this capstone, it returns a **hardcoded balance** for any account, allowing the rest of the system to be built and tested without a real banking dependency.

---

## Responsibilities

- Expose a REST endpoint to return an account's current balance
- Always return a fixed balance of `10,000.00 NGN` for any account ID
- Act as the upstream dependency that `payments-api` calls synchronously to validate funds

---

## Tech Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 4.1.0 |
| Web | Spring MVC (Tomcat) |
| Java | 21 |

---

## API Endpoint

### GET `/api/v1/accounts/{accountId}/balance`

Returns the balance for any given account. Currently returns a fixed mock value.

**Request:**

```
GET /api/v1/accounts/user123/balance
```

**Response (200 OK):**

```json
{
  "accountId": "user123",
  "balance": 10000.00,
  "currency": "NGN"
}
```

> This endpoint always returns `10000.00 NGN` regardless of `accountId`. In a production system, it would query a real database or call a core banking API.

---

## How it fits in the architecture

```
payments-api
    │
    │  GET /api/v1/accounts/{accountId}/balance
    ▼
ledger-mock :8080
    │
    │  Returns: { balance: 10000.00, currency: "NGN" }
    ▼
payments-api validates balance ≥ requested amount
```

---

## Key Source File

### `AccountController.java`

```java
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    @GetMapping("/{accountId}/balance")
    public Map<String, Object> getBalance(@PathVariable String accountId) {
        return Map.of(
            "accountId", accountId,
            "balance", new BigDecimal("10000.00"),
            "currency", "NGN"
        );
    }
}
```

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-webmvc` | REST controller, embedded Tomcat |
| `spring-boot-starter-actuator` | Health endpoints |

---

## Building & Deploying

**macOS / Linux:**
```bash
./mvnw clean install -pl ledger-mock -DskipTests
bash build-and-load-images.sh
kubectl rollout restart deployment ledger-mock
kubectl logs deployment/ledger-mock -f
```

**Windows:**
```cmd
mvnw.cmd clean install -pl ledger-mock -DskipTests
bash build-and-load-images.sh
kubectl rollout restart deployment ledger-mock
kubectl logs deployment/ledger-mock -f
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

- Connect to a real database (PostgreSQL, Oracle) via JPA
- Implement proper debit/credit transactions
- Support balance reservation and release (hold/unhold)
- Return different balances per account
- Integrate with core banking APIs (ISO 20022, etc.)
