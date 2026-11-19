package com.payrecon.payment.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "payments")
public class Payment {
    @Id // Primary Key
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // A UUID is a 128-bit random value

    @Column(nullable = false,precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String payerAccount;

    @Column(nullable = false)
    private String payeeAccount;

    @Column(nullable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    protected Payment() {}

    public Payment(BigDecimal amount, String currency, String payerAccount,
                   String payeeAccount, String reference) {
        this.amount = amount;
        this.currency = currency;
        this.payerAccount = payerAccount;
        this.payeeAccount = payeeAccount;
        this.reference = reference;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getPayerAccount() { return payerAccount; }
    public String getPayeeAccount() { return payeeAccount; }
    public String getReference() { return reference; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
