package com.specskart.auth;

import com.specskart.pos.DeviceSessionService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final DeviceSessionService deviceSessions;

    public JwtAuthFilter(JwtService jwtService, DeviceSessionService deviceSessions) {
        this.jwtService = jwtService;
        this.deviceSessions = deviceSessions;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                String role = claims.get("role", String.class);
                var auth = new UsernamePasswordAuthenticationToken(
                        claims.get("email", String.class), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
                heartbeat(claims.getSubject(), request);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    /** POS app requests carry X-Device-Id / X-App-Version; the web admin's don't. Never fails the request. */
    private void heartbeat(String userId, HttpServletRequest request) {
        String deviceId = request.getHeader("X-Device-Id");
        if (deviceId == null || deviceId.isBlank() || userId == null) return;
        try {
            deviceSessions.heartbeat(UUID.fromString(userId), deviceId, request.getHeader("X-App-Version"));
        } catch (Exception e) {
            log.debug("device heartbeat skipped: {}", e.getMessage());
        }
    }
}
