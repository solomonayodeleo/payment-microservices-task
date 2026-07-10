# Gateway

The API Gateway is the **single entry point** for all external traffic into the payment system. Built with **Spring Cloud Gateway (MVC/Servlet edition)**, it receives requests from the NGINX Ingress Controller and proxies them to the appropriate downstream microservice based on path-matching rules.

---

## Responsibilities

- Accept all inbound HTTP traffic forwarded by the NGINX Ingress Controller
- Match request paths against configured route predicates
- Proxy matched requests transparently to the correct upstream service
- Act as a decoupling layer — clients never communicate directly with backend services

---

## Tech Stack

| Component | Technology |
|---|---|
| Framework | Spring Boot 4.1.0 |
| Gateway library | Spring Cloud Gateway Server MVC 5.0.2 |
| Web stack | Servlet (Spring MVC / Tomcat) |
| Spring Cloud BOM | 2025.1.2 |
| Java | 21 |

> **Why MVC, not WebFlux?**  
> Spring Cloud Gateway MVC is the servlet-based gateway — it runs on Tomcat instead of Netty. This is the correct choice for Spring Boot 4.x, which no longer includes `NettyWebServerFactoryCustomizer` in the auto-configuration for non-reactive stacks.

---

## Configuration

### `application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: gateway
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: payments-route
              uri: http://payments-api:8080
              predicates:
                - Path=/api/v1/payments,/api/v1/payments/**
```

**Key points:**

- The property prefix is `spring.cloud.gateway.server.webmvc.routes` — this is specific to the MVC edition and is different from the reactive gateway (`spring.cloud.gateway.routes`).
- The `uri` uses the Kubernetes service DNS name `payments-api`, which resolves within the cluster.
- Two path patterns are matched: the exact path `/api/v1/payments` and any sub-paths `/api/v1/payments/**`.

---

## How it fits in the architecture

```
NGINX Ingress (port 80)
       │
       │  Host: payments.local
       ▼
  Gateway :8080
       │
       │  Path: /api/v1/payments
       ▼
  payments-api :8080
```

---

## Running locally (outside Kubernetes)

This service is designed to run inside Kubernetes where the upstream DNS name `payments-api` resolves. To run standalone, update `application.yml` with the actual host:

```yaml
uri: http://localhost:8081   # replace with your local payments-api port
```

Then build and run:

```bash
./mvnw spring-boot:run -pl gateway
```

---

## Building & Deploying

**macOS / Linux:**
```bash
# Build the JAR
./mvnw clean install -pl gateway -DskipTests

# Build Docker image and load into Kind
bash build-and-load-images.sh

# Restart the deployment
kubectl rollout restart deployment gateway

# Watch logs
kubectl logs deployment/gateway -f
```

**Windows:**
```cmd
# Build the JAR
mvnw.cmd clean install -pl gateway -DskipTests

# Build Docker image and load into Kind
bash build-and-load-images.sh

# Restart the deployment
kubectl rollout restart deployment gateway

# Watch logs
kubectl logs deployment/gateway -f
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

## Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | Servlet-based web container (Tomcat) |
| `spring-cloud-starter-gateway-server-webmvc` | MVC-based API Gateway routing |
| `spring-boot-starter-test` | Testing support |
