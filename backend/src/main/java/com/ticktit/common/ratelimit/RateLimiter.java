package com.ticktit.common.ratelimit;

import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Fixed-window counter in Redis: at most {@code max} calls per {@code window} for one key.
 * Shared by all app instances, so limits hold even with several servers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    // INCR + set the expiry on the first hit, in one atomic step (a crash between the two would leave a key forever)
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return n
            """, Long.class);

    private final StringRedisTemplate redis;

    /** Throws 429 TOO_MANY_REQUESTS when the key went over {@code max} in the current window. */
    public void check(String key, int max, Duration window) {
        Long count;
        try {
            count = redis.execute(INCREMENT, List.of(key), String.valueOf(window.toMillis()));
        } catch (RuntimeException e) {
            // ponytail: fails open so a Redis outage doesn't take login down; per-user OTP limits in Postgres still apply
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            return;
        }
        if (count != null && count > max) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }
}
