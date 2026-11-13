package com.ticktit.common.ratelimit;

import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Against the local Redis. Each test uses a fresh key. */
@SpringBootTest
class RateLimiterTest {

    @Autowired RateLimiter rateLimiter;
    @Autowired StringRedisTemplate redis;

    @Test
    void allowsUpToMaxThenRefuses() {
        String key = "rl:test:" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            rateLimiter.check(key, 3, Duration.ofMinutes(1));
        }
        ApiException ex = assertThrows(ApiException.class, () -> rateLimiter.check(key, 3, Duration.ofMinutes(1)));
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getErrorCode());
        redis.delete(key);
    }

    @Test
    void keyExpiresWithTheWindow() {
        String key = "rl:test:" + UUID.randomUUID();
        rateLimiter.check(key, 3, Duration.ofMinutes(1));

        Long ttl = redis.getExpire(key);
        assertTrue(ttl != null && ttl > 0 && ttl <= 60, "key must expire, ttl was " + ttl);
        redis.delete(key);
    }
}
