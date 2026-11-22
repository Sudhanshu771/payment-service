package com.payrecon.payment.event;

import com.payrecon.payment.dto.PaymentCreatedEvent;
import com.payrecon.payment.model.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    public static final String TOPIC = "payment.created";
    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, PaymentCreatedEvent> kafka;

    public PaymentEventPublisher(KafkaTemplate<String, PaymentCreatedEvent> kafka) {
        this.kafka = kafka;
    }

    public void publishCreated(Payment p) {
        PaymentCreatedEvent event = new PaymentCreatedEvent(
                p.getId(), p.getAmount(), p.getCurrency(),
                p.getPayerAccount(), p.getPayeeAccount(), p.getReference(), p.getCreatedAt());

        String key = p.getId().toString();

        try {
            kafka.send(TOPIC, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka send FAILED for payment {}", key, ex);
                        } else {
                            log.info("Sent payment {} to {} (offset {})", key, TOPIC,
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (RuntimeException ex) {
            log.error("Kafka send FAILED for payment {}", key, ex);
        }
    }
}