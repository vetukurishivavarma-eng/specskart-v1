package com.specskart.admin;

import com.specskart.audit.AuditLogService;
import com.specskart.auth.Role;
import com.specskart.auth.User;
import com.specskart.auth.UserRepository;
import com.specskart.shared.ApiException;
import com.specskart.shared.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Staff directory — the GET is used by the CRM "Assign employee" control (readable by ADMIN
 *  and AGENT); creating a login or changing one is ADMIN-only, same as the Specskart POS app's
 *  staff-management screen and everywhere else that touches an account. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    /** storeId scopes this login to one shop -- null (or omitted) leaves it unscoped, seeing
     *  every shop; that's how every ADMIN account, and any non-shop role, stays as-is. */
    public record CreateUser(String email, String fullName, String password, String role, UUID storeId) {}
    /** password is optional — set it to reset a staff member's login (e.g. "forgot password"),
     *  omit it to just change name/role/active. storeId omitted (null) leaves the current
     *  assignment unchanged; there's no way to clear it back to unscoped from this endpoint
     *  today (a mis-assigned account gets deleted and re-created, which is rare enough). */
    public record UpdateUser(String fullName, String role, Boolean active, String password, UUID storeId) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final CurrentUser currentUser;
    private final AuditLogService audit;

    public AdminUserController(UserRepository users, PasswordEncoder encoder, CurrentUser currentUser, AuditLogService audit) {
        this.users = users;
        this.encoder = encoder;
        this.currentUser = currentUser;
        this.audit = audit;
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
    public Map<String, Object> create(@RequestBody CreateUser req, Authentication auth) {
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
        u.setStoreId(req.storeId());
        users.save(u);
        audit.record("USER", u.getId().toString(), "CREATE", currentUser.idOf(auth), currentUser.nameOf(auth),
                u.getStoreId(), u.getEmail() + " (" + u.getRole() + ")");
        return view(u);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        return view(users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "No such user.")));
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @RequestBody UpdateUser req, Authentication auth) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "No such user."));
        if (req.fullName() != null) u.setFullName(req.fullName().trim());
        if (req.role() != null) u.setRole("ADMIN".equalsIgnoreCase(req.role()) ? Role.ADMIN : Role.AGENT);
        if (req.active() != null) u.setActive(req.active());
        if (req.password() != null && !req.password().isBlank()) u.setPasswordHash(encoder.encode(req.password()));
        if (req.storeId() != null) u.setStoreId(req.storeId());
        users.save(u);
        audit.record("USER", id.toString(), "UPDATE", currentUser.idOf(auth), currentUser.nameOf(auth), u.getStoreId(), u.getEmail());
        return view(u);
    }

    private static Map<String, Object> view(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getFullName());
        m.put("email", u.getEmail());
        m.put("role", u.getRole().name());
        m.put("active", u.isActive());
        m.put("storeId", u.getStoreId());
        return m;
    }
}
