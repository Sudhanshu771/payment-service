package com.payrecon.payment.service;

import com.payrecon.payment.dto.CreatePaymentRequest;
import com.payrecon.payment.dto.CreatePaymentResult;
import com.payrecon.payment.dto.PaymentResponse;
import com.payrecon.payment.idempotency.IdempotencyStore;
import com.payrecon.payment.model.Payment;
import com.payrecon.payment.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final IdempotencyStore idempotencyStore;          // NEW: the token register (Redis)

    public PaymentService(PaymentRepository paymentRepository,
                          IdempotencyStore idempotencyStore) { // NEW: Spring injects both
        this.paymentRepository = paymentRepository;
        this.idempotencyStore = idempotencyStore;
    }

    // No @Transactional here: save() commits on its own,
    // so we write to Redis only AFTER the payment is safely in Postgres
    public CreatePaymentResult create(String idempotencyKey, CreatePaymentRequest req) {

        // 1. Seen this key before? → it's a retry → return the old result
        if (!idempotencyStore.claim(idempotencyKey)) {
            return replay(idempotencyKey);
        }

        // 2. New key → create the payment
        try {
            Payment payment = new Payment(req.amount(), req.currency(),
                    req.payerAccount(), req.payeeAccount(), req.reference());
            Payment saved = paymentRepository.save(payment);

            // 3. Remember which payment this key created
            idempotencyStore.complete(idempotencyKey, saved.getId());
            return new CreatePaymentResult(PaymentResponse.from(saved), false);

        } catch (RuntimeException e) {
            // Saving failed → free the key so the client can retry
            idempotencyStore.release(idempotencyKey);
            throw e;
        }
    }

    private CreatePaymentResult replay(String idempotencyKey) {
        String stored = idempotencyStore.lookup(idempotencyKey);

        // First request is still running → ask the client to retry shortly
        if (stored == null || IdempotencyStore.IN_PROGRESS.equals(stored)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A request with this Idempotency-Key is still being processed. Retry shortly.");
        }

        // Finished earlier → return the SAME payment, don't create a new one
        Payment existing = paymentRepository.findById(UUID.fromString(stored))
                .orElseThrow(() -> new IllegalStateException("Stored payment not found: " + stored));
        return new CreatePaymentResult(PaymentResponse.from(existing), true);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id){
        return paymentRepository.findById(id)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment not found: "+id));
    }
}