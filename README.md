#Payment Service

A REST API for creating and tracking payments, built with **Spring Boot 4** and **PostgreSQL**.

This is the first service of a larger **Payment Processing & Reconciliation Platform** I'm building step by step. The goal is a backend that handles the hard parts of real payment systems: duplicate requests, reliable event delivery, and matching payments against bank statements.

> **Status:** 🚧 In progress. Step 1 (core payment API) is complete. See the [roadmap](#roadmap).

---

## Features (implemented)

- **Create and fetch payments**: `POST` and `GET` endpoints with a layered design (controller → service → repository)
- **PostgreSQL persistence** with Spring Data JPA / Hibernate
- **Input validation**: positive amounts with at most 2 decimals, required fields, INR only
- **Consistent error responses** using Spring's `ProblemDetail` (RFC 9457), with per-field validation messages and no internal details leaked

## Tech stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1 (Spring Web MVC, Spring Data JPA, Bean Validation) |
| Database | PostgreSQL 18 |
| Build | Maven |
| API testing | Postman |

## Architecture (current)

```
Client ──HTTP/JSON──▶ PaymentController ──▶ PaymentService ──▶ PaymentRepository ──▶ PostgreSQL
                      (HTTP in/out)         (business logic)    (data access)
```

- **DTOs** (`CreatePaymentRequest`, `PaymentResponse`) are kept separate from the **entity** (`Payment`), so clients can't set internal fields such as `id` or `status`.
- **`GlobalExceptionHandler`** (`@RestControllerAdvice`) turns every error into the same JSON shape.

---

## API

### Create a payment

`POST /api/v1/payments`

```json
{
  "amount": 2500.00,
  "currency": "INR",
  "payerAccount": "HDFC0001234-50100234567812",
  "payeeAccount": "ICIC0004321-002301556789",
  "reference": "INV-90017"
}
```

**201 Created**, with a `Location: /api/v1/payments/{id}` header:

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

### Get a payment

`GET /api/v1/payments/{id}` → **200 OK**, or **404 Not Found**

### Error format

Every error uses the same structure. A validation failure returns **400 Bad Request**:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/payments",
  "code": "VALIDATION_FAILED",
  "errors": {
    "amount": "must be greater than 0",
    "currency": "must be INR"
  }
}
```

| Status | When |
|---|---|
| 400 | Invalid fields, malformed JSON, or an invalid ID in the URL |
| 404 | Payment not found |
| 500 | Unexpected server error (details are logged, never returned) |

---

## Running locally

**Prerequisites:** Java 17+, PostgreSQL running on `localhost:5432`

1. Create the database:
   ```sql
   CREATE DATABASE payments;
   ```
2. Set the database password as an environment variable (it is never stored in the code):
   ```powershell
   $env:DB_PASSWORD="<your-postgres-password>"
   ```
3. Run:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   On macOS/Linux, use `export DB_PASSWORD=...` and `./mvnw spring-boot:run`.

The `payments` table is created automatically on startup. The API runs at `http://localhost:8080`.

---

## Design decisions

- **`BigDecimal` for money**, never `double`, to avoid floating-point rounding errors (`0.1 + 0.2 ≠ 0.3`).
- **UUID primary keys**: payment IDs appear in URLs and events, so they shouldn't be guessable or reveal business volume.
- **Enum stored as text** (`PENDING`), so reordering the enum can't corrupt existing data.
- **Constructor injection** everywhere, so dependencies are `final`, explicit and easy to unit-test.
- **Timestamps in UTC** (`Instant`); clients convert to local time.

---

## Roadmap

- [x] **Step 1:** Core payment API: Postgres, validation, error handling
- [ ] **Step 2:** Idempotency with Redis: safe retries with an `Idempotency-Key` header (201 / replayed 200 / 409 Conflict)
- [ ] **Step 3:** Publish `payment.created` events to Kafka (transactional outbox)
- [ ] **Step 4:** Consumer retries with backoff and a dead-letter topic (DLT), with idempotent replay
- [ ] **Step 5:** Reconciliation service: match bank statement CSVs to payments (MATCHED / UNMATCHED / MISMATCHED)
- [ ] Tests, Docker/WSL setup docs, correlation IDs across services
