package com.specskart.shared;

import com.specskart.auth.User;
import com.specskart.auth.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** The JWT only carries the caller's email (see JwtAuthFilter); several POS actions need to
 *  attribute a stock change/sale/device claim to a real user id, not just an email string. */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public UUID idOf(Authentication auth) {
        return userOf(auth).map(User::getId).orElse(null);
    }

    public String nameOf(Authentication auth) {
        return userOf(auth).map(User::getFullName).orElse(auth == null ? "" : String.valueOf(auth.getPrincipal()));
    }

    private java.util.Optional<User> userOf(Authentication auth) {
        if (auth == null) return java.util.Optional.empty();
        return users.findByEmailIgnoreCase(String.valueOf(auth.getPrincipal()));
    }
}
