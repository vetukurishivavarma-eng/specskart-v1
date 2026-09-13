package com.specskart.shared;

import com.specskart.auth.Role;
import com.specskart.auth.User;
import com.specskart.auth.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;
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

    /** Null means unscoped (every ADMIN account) -- sees/touches every shop. */
    public UUID storeIdOf(Authentication auth) {
        return userOf(auth).map(User::getStoreId).orElse(null);
    }

    public boolean isAdmin(Authentication auth) {
        return userOf(auth).map(u -> u.getRole() == Role.ADMIN).orElse(false);
    }

    /** Throws 403 if a shop-scoped login is asking about a shop other than its own. An
     *  ADMIN (or an unscoped login) always passes. Call this at the top of any POS endpoint
     *  that takes a storeId, before touching that shop's data. */
    public void assertStoreAccess(Authentication auth, UUID storeId) {
        User u = userOf(auth).orElse(null);
        if (u == null) throw ApiException.forbidden("STORE_SCOPED", "Not signed in.");
        if (u.getRole() == Role.ADMIN || u.getStoreId() == null) return;
        if (!Objects.equals(u.getStoreId(), storeId)) {
            throw ApiException.forbidden("STORE_SCOPED", "You don't have access to this shop.");
        }
    }

    private java.util.Optional<User> userOf(Authentication auth) {
        if (auth == null) return java.util.Optional.empty();
        return users.findByEmailIgnoreCase(String.valueOf(auth.getPrincipal()));
    }
}
