package com.payrecon.payment.service;

import com.payrecon.payment.dto.CreatePaymentRequest;
import com.payrecon.payment.dto.CreatePaymentResult;
import com.payrecon.payment.dto.PageResponse;
import com.payrecon.payment.dto.PaymentResponse;
import com.payrecon.payment.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.payrecon.payment.event.PaymentEventPublisher;
import com.payrecon.payment.idempotency.IdempotencyStore;
import com.payrecon.payment.idempotency.RequestHasher;
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
    private final IdempotencyStore idempotencyStore;
    private final PaymentEventPublisher eventPublisher;


    public PaymentService(PaymentRepository paymentRepository,
                          IdempotencyStore idempotencyStore,
                          PaymentEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.idempotencyStore = idempotencyStore;
        this.eventPublisher = eventPublisher;
    }

    public CreatePaymentResult create(String idempotencyKey, CreatePaymentRequest req) {

        String requestHash = RequestHasher.hash(req);                    // NEW: fingerprint of the slip

        // 1. Seen this key before? → check it in replay()
        if (!idempotencyStore.claim(idempotencyKey, requestHash)) {      // NEW: pass the hash
            return replay(idempotencyKey, requestHash);                  // NEW: pass the hash
        }

        // 2. New key → create the payment
        try {
            Payment payment = new Payment(req.amount(), req.currency(),
                    req.payerAccount(), req.payeeAccount(), req.reference());
            Payment saved = paymentRepository.save(payment);

            // 3. Remember: this key + this hash → this payment
            idempotencyStore.complete(idempotencyKey, requestHash, saved.getId());
            eventPublisher.publishCreated(saved);                     // NEW: ring the bell
            return new CreatePaymentResult(PaymentResponse.from(saved), false);

        } catch (RuntimeException e) {
            idempotencyStore.release(idempotencyKey);
            throw e;
        }
    }

    private CreatePaymentResult replay(String idempotencyKey, String requestHash) {
        IdempotencyStore.Entry stored = idempotencyStore.lookup(idempotencyKey);   // NEW: an Entry, not text

        // Key vanished (expired) between our two Redis calls → ask to retry
        if (stored == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A request with this Idempotency-Key is still being processed. Retry shortly.");
        }

        // NEW: same key, DIFFERENT payment details → client mistake → 409
        if (!stored.requestHash().equals(requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This Idempotency-Key was already used for a different payment. Use a new key.");
        }

        // Same details, but the first request hasn't finished yet → 409
        if (stored.inProgress()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A request with this Idempotency-Key is still being processed. Retry shortly.");
        }

        // Same details, finished → return the SAME payment (200)
        Payment existing = paymentRepository.findById(UUID.fromString(stored.state()))
                .orElseThrow(() -> new IllegalStateException("Stored payment not found: " + stored.state()));
        return new CreatePaymentResult(PaymentResponse.from(existing), true);
    }

    // NEW: newest payments first, one page at a time, optionally only one status
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(PaymentStatus status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);          // never return 1,000,000 rows at once
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Payment> result = (status == null)
                ? paymentRepository.findAll(pageable)
                : paymentRepository.findByStatus(status, pageable);

        return PageResponse.from(result, PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id){
        return paymentRepository.findById(id)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment not found: "+id));
    }
}