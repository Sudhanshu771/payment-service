package com.payrecon.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate) {

        // Still failing after retries → send to "<topic>-dlt" (the problem tray)
        DeadLetterPublishingRecoverer sendToDlt = new DeadLetterPublishingRecoverer(kafkaTemplate);

        // Wait 2 seconds, retry 2 more times (3 tries in total)
        FixedBackOff retryRule = new FixedBackOff(2000L, 2);

        return new DefaultErrorHandler(sendToDlt, retryRule);
    }
}