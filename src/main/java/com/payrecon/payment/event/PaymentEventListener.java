package com.payrecon.payment.event;

import com.payrecon.payment.dto.PaymentCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// TEMPORARY: for learning. The real consumer will live in reconciliation-service (step 5)
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    @KafkaListener(topics = PaymentEventPublisher.TOPIC, groupId = "payment-demo")
    public void onPaymentCreated(PaymentCreatedEvent event) {
        log.info("RECEIVED payment {} amount {} {} ref {}",
                event.paymentId(), event.amount(), event.currency(), event.reference());

        // TEMPORARY: fake a failure so we can watch retries
        if (event.reference().startsWith("FAIL")) {
            throw new IllegalStateException("Simulated failure for " + event.reference());
        }
    }
}