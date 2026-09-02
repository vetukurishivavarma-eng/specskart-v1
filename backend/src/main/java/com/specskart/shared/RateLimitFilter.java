package com.specskart.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight in-memory fixed-window rate limiter for unauthenticated endpoints.
 * Per client IP + path-prefix bucket. Not distributed — a Redis token bucket is the Phase 2 upgrade.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private static final int LIMIT = 60; // requests per IP per prefix per minute

    private record Bucket(AtomicLong windowStart, AtomicLong count) {}

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return !(p.startsWith("/api/webhooks/") || p.startsWith("/api/frame-finder/") || p.startsWith("/api/sim/"));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String key = clientIp(request) + "|" + prefix(request.getRequestURI());
        long now = System.currentTimeMillis();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(new AtomicLong(now), new AtomicLong(0)));

        synchronized (b) {
            if (now - b.windowStart().get() > WINDOW_MS) {
                b.windowStart().set(now);
                b.count().set(0);
            }
            if (b.count().incrementAndGet() > LIMIT) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please slow down.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String prefix(String uri) {
        String[] parts = uri.split("/");
        return parts.length > 2 ? "/" + parts[1] + "/" + parts[2] : uri;
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
