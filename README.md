# Payment Processing Microservices — Capstone Project

> A production-style, event-driven payment processing system built with Spring Boot 4, deployed on a local Kubernetes cluster using Kind. Designed as a capstone to demonstrate microservices architecture, asynchronous messaging, and cloud-native deployment patterns.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Payment Flow](#payment-flow)
- [API Reference](#api-reference)
- [Services](#services)
- [Kubernetes Manifests](#kubernetes-manifests)
- [Development Workflow](#development-workflow)

---

## Project Overview

This project simulates a real-world **fintech payment processing system** composed of four independent microservices that communicate over HTTP and asynchronous messaging (NATS). It is fully containerised with Docker and orchestrated on a local Kubernetes cluster via [Kind](https://kind.sigs.k8s.io/).

**Core capabilities demonstrated:**

- API Gateway pattern with route-based proxying
- Synchronous inter-service HTTP calls (payments → ledger)
- Asynchronous event-driven processing (payments → NATS → settlement worker)
- State management with Redis (deployed as a StatefulSet with 3 replicas)
- Kubernetes-native deployment with NGINX Ingress

---

## Architecture

```
                         ┌──────────────────────────────────────────────────┐
                         │              Kubernetes Cluster (Kind)            │
                         │                                                    │
  Client (curl/browser)  │  ┌──────────────┐       ┌───────────────┐        │
  ─────────────────────► │  │     NGINX    │       │    Gateway    │        │
  Host: payments.local   │  │   Ingress    ├──────►│    :8080      │        │
  http://localhost/...   │  └──────────────┘       └───────┬───────┘        │
                         │                                  │                 │
                         │                 route: /api/v1/payments            │
                         │                                  │                 │
                         │                         ┌────────▼────────┐       │
                         │                         │  payments-api   │       │
                         │                         │    :8080        │       │
                         │                         └──┬───────────┬──┘       │
                         │                            │           │           │
                         │              HTTP GET       │           │ NATS      │
                         │              /balance       │           │ publish   │
                         │                        ┌───▼──────┐ ┌──▼──────┐  │
                         │                        │  ledger  │ │  NATS   │  │
                         │                        │  -mock   │ │  :4222  │  │
                         │                        └──────────┘ └────┬────┘  │
                         │                                          │        │
                         │                                  NATS subscribe   │
                         │                                          │        │
                         │                         ┌───────────────▼──────┐ │
                         │                         │   settlement-worker  │ │
                         │                         │  (async processing)  │ │
                         │                         └──────────────────────┘ │
                         │                                                    │
                         │   ┌────────────────────────────────────────────┐  │
                         │   │      Redis StatefulSet (3 replicas)        │  │
                         │   └────────────────────────────────────────────┘  │
                         └──────────────────────────────────────────────────┘
```

### Request Flow — Step by Step

1. **Client** sends `POST /api/v1/payments` with `Host: payments.local`
2. **NGINX Ingress** receives the request and routes it to the **Gateway** service
3. **Gateway** (Spring Cloud Gateway MVC) matches the `Path` predicate and proxies to **payments-api**
4. **payments-api** calls **ledger-mock** over HTTP to verify the account balance
5. If sufficient funds exist → a `paymentReference` UUID is generated, `200 SUCCESS` is returned **immediately**
6. **payments-api** publishes a `payments.completed` event to **NATS** asynchronously (fire-and-forget)
7. **settlement-worker** receives the NATS message and processes the settlement in the background

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 4.1.0 |
| API Gateway | Spring Cloud Gateway (MVC/Servlet) | 5.0.2 (BOM 2025.1.2) |
| Build Tool | Maven | Wrapper (`mvnw`) |
| Message Broker | NATS | Latest |
| NATS Java Client | jnats | 2.17.6 |
| State Store | Redis | 6.2-alpine |
| Containerisation | Docker (eclipse-temurin JRE) | 21-jre |
| Orchestration | Kubernetes via Kind | Latest |
| Ingress Controller | NGINX Ingress | Latest |

---

## Project Structure

```
microservice-assignment/
├── gateway/                     # API Gateway — route-based proxy
│   ├── src/main/resources/application.yml
│   ├── Dockerfile
│   └── pom.xml
│
├── payments-api/                # Core payment service — authorises & publishes events
│   ├── src/main/java/.../
│   │   ├── PaymentsApiApplication.java
│   │   └── PaymentController.java
│   ├── Dockerfile
│   └── pom.xml
│
├── ledger-mock/                 # Mock banking ledger — returns account balances
│   ├── src/main/java/.../
│   │   ├── LedgerMockApplication.java
│   │   └── AccountController.java
│   ├── Dockerfile
│   └── pom.xml
│
├── settlement-worker/           # Async worker — consumes NATS events, settles payments
│   ├── src/main/java/.../
│   │   ├── SettlementWorkerApplication.java
│   │   └── SettlementListener.java
│   ├── Dockerfile
│   └── pom.xml
│
├── k8s/
│   ├── deployments.yaml         # All service Deployments + ClusterIP Services
│   ├── ingress.yaml             # NGINX Ingress routing rules
│   ├── nats.yaml                # NATS Deployment + Service
│   ├── redis.yaml               # Redis StatefulSet + Headless Service
│   ├── tls.crt                  # Self-signed TLS certificate
│   └── tls.key                  # TLS private key
│
├── build-and-load-images.sh     # Build all Docker images & load into Kind
├── setup-cluster.sh             # Bootstrap Kind cluster + NGINX Ingress
├── pom.xml                      # Root Maven multi-module POM
└── Plan.md                      # Original project design plan
```

---

## Prerequisites

| Tool | Minimum Version | Notes |
|---|---|---|
| Java JDK | 21+ | See install instructions below |
| Docker Desktop | Latest | [docker.com](https://www.docker.com/products/docker-desktop/) |
| Kind | Latest | [kind.sigs.k8s.io/docs/user/quick-start](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) |
| kubectl | Latest | [kubernetes.io/docs/tasks/tools](https://kubernetes.io/docs/tasks/tools/) |
| jq | Any | Optional — pretty-prints JSON in terminal |

### Installing Java 21

Install the **Temurin JDK 21** distribution from [Adoptium](https://adoptium.net/temurin/releases/?version=21). It is free, open-source, and works on all platforms.

**macOS**
```bash
brew install --cask temurin@21
```

**Windows**  
Download and run the `.msi` installer from [adoptium.net](https://adoptium.net/temurin/releases/?version=21). The installer sets `JAVA_HOME` and adds Java to your `PATH` automatically.

**Linux (Ubuntu/Debian)**
```bash
sudo apt-get install -y wget apt-transport-https
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo apt-key add -
echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -sc) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt-get update && sudo apt-get install -y temurin-21-jdk
```

Verify your installation:
```bash
java -version
# Expected: openjdk version "21.x.x" ...
```

> The Maven Wrapper (`mvnw` / `mvnw.cmd`) included in this project will automatically use whichever Java 21 is on your `PATH`. No manual `JAVA_HOME` configuration needed as long as Java 21 is installed correctly.

---

## Getting Started

### 1. Clone the repository

```bash
git clone <repository-url>
cd microservice-assignment
```

### 2. Create the Kubernetes cluster

Creates a Kind cluster named `ds-lab` with port 80 exposed on localhost, then installs the NGINX Ingress Controller:

```bash
bash setup-cluster.sh
```

### 3. Deploy infrastructure

```bash
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/nats.yaml

# Wait until all Redis pods are Running
kubectl get pods -w
```

### 4. Build and load all service images

**macOS / Linux:**
```bash
./mvnw clean install -DskipTests
bash build-and-load-images.sh
```

**Windows (Command Prompt or PowerShell):**
```cmd
mvnw.cmd clean install -DskipTests
bash build-and-load-images.sh
```

> If `bash` is not available on Windows, use [Git Bash](https://git-scm.com/downloads) or [WSL](https://learn.microsoft.com/en-us/windows/wsl/install) to run the shell script.

### 5. Deploy services and Ingress

```bash
kubectl apply -f k8s/deployments.yaml
kubectl apply -f k8s/ingress.yaml
```

### 6. Verify all pods are Running

```bash
kubectl get pods
```

```
NAME                                 READY   STATUS    RESTARTS   AGE
gateway-xxxxx                        1/1     Running   0          30s
ledger-mock-xxxxx                    1/1     Running   0          30s
nats-xxxxx                           1/1     Running   0          2m
payments-api-xxxxx                   1/1     Running   0          30s
redis-0                              1/1     Running   0          2m
redis-1                              1/1     Running   0          2m
redis-2                              1/1     Running   0          2m
settlement-worker-xxxxx              1/1     Running   0          30s
```

---

## Payment Flow

### Successful payment

```bash
curl -s \
  -H "Host: payments.local" \
  -H "Content-Type: application/json" \
  -d '{"accountId": "user123", "amount": 5000}' \
  http://localhost/api/v1/payments | jq .
```

```json
{
  "status": "SUCCESS",
  "paymentReference": "961b55dd-13ec-4257-b1df-d599fb71a390",
  "amountDeducted": 5000
}
```

Then confirm async settlement happened:

```bash
kubectl logs deployment/settlement-worker --tail=5
```

```
✅ [SETTLEMENT WORKER] Received Message on 'payments.completed': {...}
✅ [SETTLEMENT WORKER] Settlement Processed Successfully!
```

### Insufficient funds

```bash
curl -s \
  -H "Host: payments.local" \
  -H "Content-Type: application/json" \
  -d '{"accountId": "user123", "amount": 999999}' \
  http://localhost/api/v1/payments | jq .
```

```json
{
  "status": "FAILED",
  "reason": "Insufficient Funds"
}
```

---

## API Reference

### POST /api/v1/payments

The only externally exposed endpoint. Routed via Gateway → payments-api.

**Request**

```json
{
  "accountId": "string",
  "amount": number
}
```

**Success (HTTP 200)**

```json
{
  "status": "SUCCESS",
  "paymentReference": "uuid",
  "amountDeducted": number
}
```

**Failure (HTTP 400)**

```json
{
  "status": "FAILED",
  "reason": "Insufficient Funds"
}
```

---

## Services

| Service | Port | README |
|---|---|---|
| gateway | 8080 | [gateway/README.md](./gateway/README.md) |
| payments-api | 8080 | [payments-api/README.md](./payments-api/README.md) |
| ledger-mock | 8080 | [ledger-mock/README.md](./ledger-mock/README.md) |
| settlement-worker | 8080 | [settlement-worker/README.md](./settlement-worker/README.md) |

---

## Kubernetes Manifests

| File | What it deploys |
|---|---|
| `k8s/deployments.yaml` | All 4 microservice Deployments + ClusterIP Services |
| `k8s/ingress.yaml` | NGINX Ingress — routes `payments.local` → gateway |
| `k8s/nats.yaml` | NATS message broker Deployment + Service (port 4222) |
| `k8s/redis.yaml` | Redis StatefulSet (3 replicas) + Headless Service |

---

## Development Workflow

### Rebuild and redeploy a single service

**macOS / Linux:**
```bash
# 1. Build only the module you changed
./mvnw clean install -pl payments-api -DskipTests

# 2. Rebuild images and load into Kind
bash build-and-load-images.sh

# 3. Rolling restart
kubectl rollout restart deployment payments-api

# 4. Follow logs
kubectl logs deployment/payments-api -f
```

**Windows:**
```cmd
# 1. Build
mvnw.cmd clean install -pl payments-api -DskipTests

# 2. Rebuild images (run in Git Bash or WSL)
bash build-and-load-images.sh

# 3 & 4. Restart and follow logs
kubectl rollout restart deployment payments-api
kubectl logs deployment/payments-api -f
```


### Useful kubectl commands

```bash
# Status of all pods
kubectl get pods

# Describe a pod for detailed event info
kubectl describe pod <pod-name>

# Stream logs
kubectl logs deployment/gateway -f
kubectl logs deployment/payments-api -f
kubectl logs deployment/ledger-mock -f
kubectl logs deployment/settlement-worker -f

# List services and their cluster IPs
kubectl get services
```

### Tear down the cluster

```bash
kind delete cluster --name ds-lab
```
