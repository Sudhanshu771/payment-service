package com.payrecon.payment.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class IdempotencyStore {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String PREFIX = "idem:";               // keys look like idem:abc-123
    private static final Duration TTL = Duration.ofHours(24);   // forget keys after 24 hours

    private final StringRedisTemplate redis;

    public IdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // SET NX: returns true only for the FIRST request with this key
    public boolean claim(String key) {
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(PREFIX + key, IN_PROGRESS, TTL));
    }

    // Payment saved: replace "IN_PROGRESS" with the real payment id
    public void complete(String key, UUID paymentId) {
        redis.opsForValue().set(PREFIX + key, paymentId.toString(), TTL);
    }

    // Something failed: remove the key so the client can try again
    public void release(String key) {
        redis.delete(PREFIX + key);
    }

    // What's stored for this key: null, "IN_PROGRESS", or a payment id
    public String lookup(String key) {
        return redis.opsForValue().get(PREFIX + key);
    }
}