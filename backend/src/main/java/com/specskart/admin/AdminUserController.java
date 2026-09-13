package com.specskart.admin;

import com.specskart.auth.Role;
import com.specskart.auth.User;
import com.specskart.auth.UserRepository;
import com.specskart.shared.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Staff directory — the GET is used by the CRM "Assign employee" control (readable by ADMIN
 *  and AGENT); creating a login or changing one is ADMIN-only, same as the Specskart POS app's
 *  staff-management screen and everywhere else that touches an account. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    public record CreateUser(String email, String fullName, String password, String role) {}
    public record UpdateUser(String fullName, String role, Boolean active) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminUserController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return users.findAll().stream()
                .filter(u -> includeInactive || u.isActive())
                .map(AdminUserController::view)
                .toList();
    }

    /** ADMIN-only (see SecurityConfig) — everyone else on the shop floor gets an AGENT
     *  account, which already covers every /api/admin/pos/** and lens-* endpoint. */
    @PostMapping
    public Map<String, Object> create(@RequestBody CreateUser req) {
        if (req.email() == null || req.email().isBlank() || req.password() == null || req.password().isBlank()) {
            throw ApiException.badRequest("MISSING_FIELDS", "Email and password are required.");
        }
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("EMAIL_TAKEN", "That email already has an account.");
        }
        User u = new User();
        u.setEmail(req.email().trim());
        u.setFullName(req.fullName() == null ? "" : req.fullName().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole("ADMIN".equalsIgnoreCase(req.role()) ? Role.ADMIN : Role.AGENT);
        return view(users.save(u));
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @RequestBody UpdateUser req) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "No such user."));
        if (req.fullName() != null) u.setFullName(req.fullName().trim());
        if (req.role() != null) u.setRole("ADMIN".equalsIgnoreCase(req.role()) ? Role.ADMIN : Role.AGENT);
        if (req.active() != null) u.setActive(req.active());
        return view(users.save(u));
    }

    private static Map<String, Object> view(User u) {
        return Map.of("id", u.getId(), "name", u.getFullName(),
                "email", u.getEmail(), "role", u.getRole().name(), "active", u.isActive());
    }
}
