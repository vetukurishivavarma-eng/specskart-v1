package com.specskart.admin;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class MeController {

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        String role = auth.getAuthorities().stream().findFirst().map(Object::toString).orElse("ROLE_AGENT");
        return Map.of("email", auth.getPrincipal(), "role", role.replace("ROLE_", ""));
    }
}
