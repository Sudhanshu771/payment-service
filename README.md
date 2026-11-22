# Payment Service

The payment API of the **Payment Processing & Reconciliation Platform**, built with **Spring Boot 4**, **PostgreSQL**, **Redis** and **Kafka**.

**Platform:** payment-service · [reconciliation-service](https://github.com/Sudhanshu771/reconciliation-service) · [payrecon-dashboard](https://github.com/Sudhanshu771/payrecon-dashboard)

It handles the hard parts of real payment systems: **duplicate requests** (idempotency), **reliable events** for other services, and **secure access** with roles.

---

## Platform overview

```
React dashboard (5173)
   │  JWT
   ▼
payment-service (8080) ──▶ PostgreSQL "payments"
   │   │
   │   └──▶ Redis (idempotency keys)
   │
   └── Kafka "payment.created" ──▶ reconciliation-service (8081) ──▶ PostgreSQL "reconciliation"
                                         ▲                               + OpenAI (Spring AI)
                              bank statement CSV
```

## Features

- **Create, list and fetch payments**: layered design (controller → service → repository), paging and status filter
- **Idempotency with Redis**: an `Idempotency-Key` header makes retries safe
  - new key → **201 Created**
  - same key, same body → **200 OK** with the *same* payment (`Idempotent-Replayed: true`)
  - same key, different body → **409 Conflict** (detected with a SHA-256 hash of the request)
  - Redis down → **503** (fail closed: never risk a duplicate payment)
- **Kafka events**: every new payment is published to `payment.created` (key = payment id, JSON value)
- **Login + roles (JWT)**: `POST /api/v1/auth/login` issues a signed token; roles decide what each user may do
- **Validation and consistent errors**: Bean Validation and `ProblemDetail` (RFC 9457) for every error; internal details are logged, never returned

## Tech stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security / OAuth2 Resource Server) |
| Database | PostgreSQL 18 |
| Cache / idempotency | Redis (Spring Data Redis, Lettuce) |
| Messaging | Apache Kafka 4 (KRaft), Spring for Apache Kafka |
| Auth | JWT (HS256) via Spring Security, BCrypt passwords |
| Build | Maven |

---

## API

All `/api/v1` endpoints except login need `Authorization: Bearer <token>`.

### Log in
`POST /api/v1/auth/login`
```json
{ "username": "operator", "password": "..." }
```
**200 OK**
```json
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "username": "operator", "roles": ["OPERATOR"], "expiresAt": "2026-11-22T16:30:00Z" }
```
Wrong username or password → **401** `"Invalid username or password"`

### Create a payment (OPERATOR, ADMIN)
`POST /api/v1/payments` with header `Idempotency-Key: <unique per payment>`
```json
{
  "amount": 2500.00,
  "currency": "INR",
  "payerAccount": "HDFC0001234-50100234567812",
  "payeeAccount": "ICIC0004321-002301556789",
  "reference": "INV-90017"
}
```
**201 Created**, with `Location: /api/v1/payments/{id}`:
```json
{
  "paymentId": "e039d1f1-53e5-4878-bc2c-7866ee3883ae",
  "status": "PENDING",
  "amount": 2500.00,
  "currency": "INR",
  "payerAccount": "HDFC0001234-50100234567812",
  "payeeAccount": "ICIC0004321-002301556789",
  "reference": "INV-90017",
  "createdAt": "2026-11-19T06:25:08.246601Z"
}
```

### List payments (OPERATOR, AUDITOR, ADMIN)
`GET /api/v1/payments?page=0&size=20&status=PENDING` (status is optional; newest first; max size 100)
```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 13, "totalPages": 1 }
```

### Get one payment
`GET /api/v1/payments/{id}` → **200 OK**, or **404 Not Found**

### Error format
```json
{
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/payments",
  "errors": { "amount": "must be greater than 0", "currency": "must be INR" }
}
```

| Status | When |
|---|---|
| 400 | Invalid fields, malformed JSON, missing `Idempotency-Key`, bad id/parameter |
| 401 | No token, an invalid/expired token, or a failed login |
| 403 | Logged in, but the role isn't allowed (e.g. an AUDITOR creating a payment) |
| 404 | Payment or URL not found |
| 405 / 415 | Wrong HTTP method / wrong body type |
| 409 | Idempotency-Key reused for a different payment, or still in progress |
| 503 | Redis unavailable (try again shortly) |
| 500 | Unexpected error (details are logged, never returned) |

### Roles
| Role | Can |
|---|---|
| OPERATOR | create and view payments |
| AUDITOR | view payments; upload statements and reconcile (in reconciliation-service) |
| ADMIN | everything |

---

## Running locally

**Prerequisites:** Java 17+, PostgreSQL on `localhost:5432` (database `payments`), Redis on `6379`, Kafka on `9092`.
(On Windows, Redis and Kafka run in WSL: `sudo service redis-server start` and `cd ~/kafka && bin/kafka-server-start.sh config/server.properties`.)

```powershell
.\mvnw.cmd spring-boot:run
```
The API runs at `http://localhost:8080`; the `payments` table is created on startup.

> `mvnw` uses `JAVA_HOME`, which must point to **JDK 17+** (e.g. `$env:JAVA_HOME="C:\Program Files\Java\jdk-17"`). Running from IntelliJ uses the project SDK instead.

### Configuration (environment variables)
| Variable | Purpose | Local default |
|---|---|---|
| `DB_PASSWORD` | PostgreSQL password | `postgres` |
| `JWT_SECRET` | signs tokens; **must match reconciliation-service** (min 32 chars) | a local-dev-only value |
| `DEMO_PASSWORD` | password of the demo users `operator`, `auditor`, `admin` | a local-dev-only value (see `application.properties`) |

> ⚠️ The defaults are for local development only. Set real values before deploying anywhere.

---

## Design decisions

- **`BigDecimal` for money**, never `double`, to avoid rounding errors (`0.1 + 0.2 ≠ 0.3`).
- **UUID primary keys**: ids appear in URLs and events, so they shouldn't be guessable.
- **Enum stored as text** (`PENDING`), so reordering the enum can't corrupt data.
- **Constructor injection** everywhere: dependencies are `final`, explicit and easy to test.
- **Timestamps in UTC** (`Instant`); clients convert to local time.
- **Idempotency key claimed atomically** with Redis `SET NX EX`; the stored value is `hash|state`, so a retry can be replayed and a different body detected. On failure the key is released so the client can retry.
- **Payments are append-only**: no update or delete API. Corrections would be new entries (reversals), keeping a full audit trail.
- **Publishing never fails the request**: the payment is already saved, so a Kafka error is logged (with a 2 s `max.block.ms`). This "dual-write" gap is a known limitation (see below).
- **Stateless security**: JWTs instead of sessions, so CSRF protection isn't needed; CORS allows only the dashboard's origin.

## Known limitations / next steps

- **Dual write**: if Kafka is down after the DB save, the event is lost → fix with a transactional **outbox** table.
- **Demo users live in memory** → move them to a `users` table or an identity provider (e.g. Keycloak).
- **Status stays `PENDING`**: nothing moves payments to `COMPLETED`/`FAILED` yet.
- `PaymentEventListener` is a learning leftover (the real consumer is reconciliation-service).
- More automated tests; Docker Compose and deployment.

## Roadmap

- [x] **Step 1:** Core payment API: Postgres, validation, error handling
- [x] **Step 2:** Idempotency with Redis (201 / replayed 200 / 409 / 503)
- [x] **Step 3:** Publish `payment.created` events to Kafka
- [x] **Step 4:** Consumer retries with backoff and a dead-letter topic (DLT)
- [x] **Step 5:** Reconciliation service ([reconciliation-service](https://github.com/Sudhanshu771/reconciliation-service))
- [x] **Step 6:** React dashboard + JWT login with roles ([payrecon-dashboard](https://github.com/Sudhanshu771/payrecon-dashboard))
- [x] **Step 7:** AI explanations of reconciliation breaks with Spring AI (in reconciliation-service)
- [ ] **Step 8:** Docker Compose and deployment
