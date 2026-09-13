package com.specskart.shared;

import com.specskart.auth.Role;
import com.specskart.auth.User;
import com.specskart.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Each shop's login must not be able to touch another shop's data (see CurrentUser). */
@SpringBootTest
@ActiveProfiles("mock")
class StoreScopedAccessTest {

    @Autowired UserRepository users;
    @Autowired CurrentUser currentUser;

    private User agentAt(UUID storeId) {
        User u = new User();
        u.setEmail("agent-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        u.setFullName("Agent");
        u.setRole(Role.AGENT);
        u.setStoreId(storeId);
        return users.save(u);
    }

    private Authentication authOf(User u) {
        return new UsernamePasswordAuthenticationToken(u.getEmail(), null);
    }

    @Test
    void agentCanTouchTheirOwnStore() {
        UUID storeId = UUID.randomUUID();
        User u = agentAt(storeId);
        currentUser.assertStoreAccess(authOf(u), storeId); // does not throw
    }

    @Test
    void agentCannotTouchAnotherStore() {
        User u = agentAt(UUID.randomUUID());
        assertThatThrownBy(() -> currentUser.assertStoreAccess(authOf(u), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "STORE_SCOPED");
    }

    @Test
    void unscopedAgentCanTouchAnyStore() {
        User u = agentAt(null);
        currentUser.assertStoreAccess(authOf(u), UUID.randomUUID()); // does not throw
    }

    @Test
    void adminCanTouchAnyStoreEvenWhenScoped() {
        User u = agentAt(UUID.randomUUID());
        u.setRole(Role.ADMIN);
        users.save(u);
        currentUser.assertStoreAccess(authOf(u), UUID.randomUUID()); // does not throw
    }

    @Test
    void loginStoreIdRoundTrips() {
        UUID storeId = UUID.randomUUID();
        User u = agentAt(storeId);
        assertThat(currentUser.storeIdOf(authOf(u))).isEqualTo(storeId);
        assertThat(currentUser.isAdmin(authOf(u))).isFalse();
    }
}
