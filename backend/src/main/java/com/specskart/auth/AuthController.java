package com.specskart.auth;

import com.specskart.config.AppProperties;
import com.specskart.pos.DeviceSessionService;
import com.specskart.shared.ApiException;
import com.specskart.whatsapp.WhatsAppProvider;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final DeviceSessionService deviceSessions;
    private final WhatsAppProvider whatsapp;
    private final AppProperties props;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt, DeviceSessionService deviceSessions,
                          WhatsAppProvider whatsapp, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.deviceSessions = deviceSessions;
        this.whatsapp = whatsapp;
        this.props = props;
    }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid email or password."));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid email or password.");
        }
        // Only the POS app sends a deviceId; the web admin login is unaffected.
        if (req.deviceId() != null && !req.deviceId().isBlank()) {
            deviceSessions.claim(user.getId(), req.deviceId(), req.deviceName(), req.platform(), req.appVersion());
        }
        return new AuthDtos.LoginResponse(jwt.issue(user), user.getEmail(), user.getFullName(), user.getRole().name());
    }

    /** No email/SMS infra here — a forgotten password gets an admin notified over WhatsApp
     *  (the same staff numbers every other alert in this app uses) to reset it manually via
     *  AdminUserController's password field. Always returns success, whether or not the email
     *  matches an account, so this can't be used to check who has a login. */
    @PostMapping("/forgot-password")
    public void forgotPassword(@RequestBody AuthDtos.ForgotPasswordRequest req) {
        users.findByEmailIgnoreCase(req.email()).filter(User::isActive).ifPresent(user -> {
            String msg = "🔑 Password reset requested for " + user.getEmail()
                    + " (" + user.getFullName() + "). Reset it from the admin app (Staff → " + user.getFullName() + ").";
            for (String to : props.whatsapp().staffNumbers()) {
                try { whatsapp.sendText(to.trim(), msg); } catch (Exception e) { log.warn("forgot-password alert failed: {}", e.getMessage()); }
            }
        });
    }
}
