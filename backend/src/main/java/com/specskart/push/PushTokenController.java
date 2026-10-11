package com.specskart.push;

import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The POS app registers its phone for order notifications after login (ADMIN + AGENT). */
@RestController
@RequestMapping("/api/admin/push-token")
public class PushTokenController {

    private final PushTokenRepository tokens;

    public PushTokenController(PushTokenRepository tokens) {
        this.tokens = tokens;
    }

    public record Register(String token) {}

    @PostMapping
    @Transactional
    public Map<String, Object> register(@RequestBody Register body, Authentication auth) {
        String token = body.token() == null ? "" : body.token().trim();
        if (token.isEmpty() || token.length() > 4096) return Map.of("registered", false);
        // A phone passed to another staff member keeps its token; it follows whoever signed in last.
        PushToken t = tokens.findByToken(token).orElseGet(PushToken::new);
        t.setToken(token);
        t.setUserEmail(auth.getName());
        tokens.save(t);
        return Map.of("registered", true);
    }
}
