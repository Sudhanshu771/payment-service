package com.payrecon.payment.dto;

public record CreatePaymentResult(PaymentResponse payment, boolean replayed) { }