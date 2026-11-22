package com.payrecon.payment.controller;

import com.payrecon.payment.dto.CreatePaymentRequest;
import com.payrecon.payment.dto.CreatePaymentResult;
import com.payrecon.payment.dto.PageResponse;
import com.payrecon.payment.dto.PaymentResponse;
import com.payrecon.payment.model.PaymentStatus;
import com.payrecon.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Postman (key + JSON) → Controller → Service → Redis check → Postgres
    //   new key  → 201 Created + receipt
    //   same key → 200 OK + the SAME receipt (no new payment)
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        CreatePaymentResult result = paymentService.create(idempotencyKey, request);

        if (result.replayed()) {
            return ResponseEntity.ok()                               // 200: retry
                    .header("Idempotent-Replayed", "true")
                    .body(result.payment());
        }
        return ResponseEntity
                .created(URI.create("/api/v1/payments/" + result.payment().paymentId()))  // 201: new
                .header("Idempotent-Replayed", "false")
                .body(result.payment());
    }

    // NEW: GET /api/v1/payments?page=0&size=20&status=PENDING  (status is optional)
    @GetMapping
    public PageResponse<PaymentResponse> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return paymentService.list(status, page, size);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id){
        return paymentService.get(id);
    }
}