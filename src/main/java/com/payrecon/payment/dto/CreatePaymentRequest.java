package com.payrecon.payment.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreatePaymentRequest(

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "amount can have at most 2 decimal places")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "INR", message = "only INR is supported")
        String currency,

        @NotBlank(message = "payerAccount is required")
        @Size(max = 34, message = "payerAccount must be at most 34 characters")
        String payerAccount,

        @NotBlank(message = "payeeAccount is required")
        @Size(max = 34, message = "payeeAccount must be at most 34 characters")
        String payeeAccount,

        @NotBlank(message = "reference is required")
        @Size(max = 64, message = "reference must be at most 64 characters")
        String reference) { }