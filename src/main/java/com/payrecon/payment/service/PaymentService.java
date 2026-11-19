package com.payrecon.payment.service;

import com.payrecon.payment.dto.CreatePaymentRequest;
import com.payrecon.payment.dto.PaymentResponse;
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

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    // Do all of this as one unit
    // ” If anything fails halfway, the database rolls back to how it was
    @Transactional
    public PaymentResponse create(CreatePaymentRequest req) {
        Payment payment = new Payment(req.amount(), req.currency(),
                req.payerAccount(), req.payeeAccount(), req.reference());

        Payment saved = paymentRepository.save(payment);
        return PaymentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id){
        return paymentRepository.findById(id)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment not found: "+id));
    }
}


