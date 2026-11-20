package com.payrecon.payment.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class RedisCheckController {

    private final StringRedisTemplate redis;

    public RedisCheckController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @GetMapping("/redis-check")
    public String check() {
        Boolean saved = redis.opsForValue()
                .setIfAbsent("demo:key", "hello", Duration.ofSeconds(60));
        return "saved = " + saved;
    }
}