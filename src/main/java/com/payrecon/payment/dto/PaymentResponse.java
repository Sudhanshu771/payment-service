package com.payrecon.payment.dto;

import com.payrecon.payment.model.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(UUID paymentId,
                              String status,
                              BigDecimal amount,
                              String currency,
                              String payerAccount,
                              String payeeAccount,
                              String reference,
                              Instant createdAt) {
    // from(Payment p) is a small converter: it copies the ledger page onto a receipt.
    // Keeping that conversion in one place means you change only one line when the receipt changes.
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getStatus().name(), p.getAmount(),
                p.getCurrency(), p.getPayerAccount(), p.getPayeeAccount(),
                p.getReference(), p.getCreatedAt());
    }
}
