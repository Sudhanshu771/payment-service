package com.payrecon.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCreatedEvent(
        UUID paymentId,
        BigDecimal amount,
        String currency,
        String payerAccount,
        String payeeAccount,
        String reference,
        Instant createdAt) { }