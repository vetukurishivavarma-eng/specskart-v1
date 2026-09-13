package com.specskart.auth;

import com.specskart.pos.DeviceSessionService;
import com.specskart.shared.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final DeviceSessionService deviceSessions;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt, DeviceSessionService deviceSessions) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.deviceSessions = deviceSessions;
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
}
