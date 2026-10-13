package com.specskart.pos;

import com.specskart.auth.JwtService;
import com.specskart.auth.Role;
import com.specskart.auth.User;
import com.specskart.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Signing out on one phone must free the login for the next phone, no admin needed. */
@SpringBootTest
@ActiveProfiles("mock")
class SignOutReleasesDeviceTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired DeviceSessionService deviceSessions;

    @Test
    void signOutFreesTheLoginForAnotherPhone() throws Exception {
        User u = new User();
        u.setEmail("signout-" + System.nanoTime() + "@specskart.local");
        u.setPasswordHash("x");
        u.setFullName("Staff");
        u.setRole(Role.AGENT);
        User staff = users.save(u);
        deviceSessions.claim(staff.getId(), "phone-a", "Phone A", "android", "1.22.0");
        assertThatThrownBy(() -> deviceSessions.claim(staff.getId(), "phone-b", "Phone B", "android", "1.22.0"))
                .hasMessageContaining("another device");

        MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        mvc.perform(post("/api/admin/pos/devices/me/sign-out").header("Authorization", "Bearer " + jwt.issue(staff))
                        .header("X-Device-Id", "phone-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.released").value(true));

        assertThat(deviceSessions.claim(staff.getId(), "phone-b", "Phone B", "android", "1.22.0").getDeviceId())
                .isEqualTo("phone-b");
    }
}
