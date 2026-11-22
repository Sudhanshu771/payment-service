package com.payrecon.payment.repository;

import com.payrecon.payment.model.Payment;
import com.payrecon.payment.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // Derived query: "where status = ?" + paging/sorting from the Pageable
    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}
