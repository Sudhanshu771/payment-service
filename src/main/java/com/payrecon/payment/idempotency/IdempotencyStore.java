package com.payrecon.payment.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class IdempotencyStore {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String PREFIX = "idem:";
    private static final String SEPARATOR = "|";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public IdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // What lookup() gives back: the saved hash + the state (IN_PROGRESS or a payment id)
    public record Entry(String requestHash, String state) {
        public boolean inProgress() {
            return IN_PROGRESS.equals(state);
        }
    }

    // SET NX: store "hash|IN_PROGRESS" only if the key is new
    public boolean claim(String key, String requestHash) {
        return Boolean.TRUE.equals(redis.opsForValue()
                .setIfAbsent(PREFIX + key, requestHash + SEPARATOR + IN_PROGRESS, TTL));
    }

    // Payment saved: store "hash|paymentId"
    public void complete(String key, String requestHash, UUID paymentId) {
        redis.opsForValue().set(PREFIX + key, requestHash + SEPARATOR + paymentId, TTL);
    }

    // Something failed: delete the key so the client can retry
    public void release(String key) {
        redis.delete(PREFIX + key);
    }

    // Read "hash|state" and split it into its two parts
    public Entry lookup(String key) {
        String value = redis.opsForValue().get(PREFIX + key);
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\|", 2);    // split at the first "|"
        if (parts.length < 2) {
            return new Entry("", value);           // old 3a-style value without a hash
        }
        return new Entry(parts[0], parts[1]);
    }
}