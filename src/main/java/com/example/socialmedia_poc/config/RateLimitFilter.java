package com.example.socialmedia_poc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter for sensitive endpoints.
 * Limits per IP address using a sliding-window counter.
 *
 * Endpoints protected:
 *  - /v1/users/login          → 10 requests / minute
 *  - /v1/users/register       → 5 requests / minute
 *  - /v1/users/google-login   → 10 requests / minute
 *  - /v1/users/session        → 20 requests / minute
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final long WINDOW_MS = 60_000; // 1 minute

    // path prefix → max requests per window
    private static final Map<String, Integer> RATE_LIMITS = Map.of(
        "/v1/users/login", 10,
        "/v1/users/register", 5,
        "/v1/users/google-login", 10,
        "/v1/users/session", 20
    );

    // key = "ip:path" → bucket
    private final ConcurrentHashMap<String, RateBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        Integer limit = null;
        String matchedPath = null;

        for (Map.Entry<String, Integer> entry : RATE_LIMITS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                limit = entry.getValue();
                matchedPath = entry.getKey();
                break;
            }
        }

        if (limit != null) {
            String ip = getClientIp(request);
            String key = ip + ":" + matchedPath;

            RateBucket bucket = buckets.compute(key, (k, existing) -> {
                long now = System.currentTimeMillis();
                if (existing == null || now - existing.windowStart > WINDOW_MS) {
                    return new RateBucket(now);
                }
                return existing;
            });

            int count = bucket.counter.incrementAndGet();
            if (count > limit) {
                log.warn("[RateLimit] {} exceeded limit ({}/{}) on {}", ip, count, limit, matchedPath);
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":\"error\",\"message\":\"Too many requests. Please try again later.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class RateBucket {
        final long windowStart;
        final AtomicInteger counter = new AtomicInteger(0);

        RateBucket(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
