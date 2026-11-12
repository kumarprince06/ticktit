package com.ticktit.config;

import com.ticktit.common.exception.ApiException;
import com.ticktit.common.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-IP limit on every auth action (sign-up, login, OTP, reset...): 20 per minute per endpoint.
 * Stops one machine from trying many emails or flooding inboxes. Per-account limits live in AuthService.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)   // right after RequestIdFilter, before security and controllers
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_PER_MINUTE = 20;

    private final RateLimiter rateLimiter;
    private final HandlerExceptionResolver resolver;

    public AuthRateLimitFilter(RateLimiter rateLimiter,
                               @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.rateLimiter = rateLimiter;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // ponytail: getRemoteAddr is the direct client; behind a load balancer, enable forwarded headers from it only
        String key = "rl:auth:" + request.getRequestURI() + ":" + request.getRemoteAddr();
        try {
            rateLimiter.check(key, MAX_PER_MINUTE, Duration.ofMinutes(1));
        } catch (ApiException e) {
            response.setHeader("Retry-After", "60");
            resolver.resolveException(request, response, null, e);   // same JSON error format as everywhere else
            return;
        }
        chain.doFilter(request, response);
    }
}
