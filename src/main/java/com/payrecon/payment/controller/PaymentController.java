package com.payrecon.payment.controller;

import com.payrecon.payment.dto.CreatePaymentRequest;
import com.payrecon.payment.dto.PaymentResponse;
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

//    Postman JSON → Controller → Service → Repository → Hibernate INSERT → Postgres
    //                                                                         │
//    Postman  ←  201 + receipt JSON  ←  PaymentResponse.from(saved)  ←────────┘
    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request){
        PaymentResponse created = paymentService.create(request);
        return ResponseEntity.created(URI.create("api/v1/payments"+created.paymentId())).body(created);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id){
        return paymentService.get(id);
    }

}
