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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

/** Admin sees every signed-in phone across accounts and can free any of them; till staff can't list them. */
@SpringBootTest
@ActiveProfiles("mock")
class AllDevicesTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired DeviceSessionService deviceSessions;

    private User user(Role role) {
        User u = new User();
        u.setEmail("alldev-" + System.nanoTime() + "@specskart.local");
        u.setPasswordHash("x");
        u.setFullName("Till " + role);
        u.setRole(role);
        return users.save(u);
    }

    @Test
    void adminListsAndReleasesAnyLogin() throws Exception {
        User admin = user(Role.ADMIN), staff = user(Role.AGENT);
        DeviceSession s = deviceSessions.claim(staff.getId(), "dev-" + System.nanoTime(), "Shop Tab", "android", "1.13.0");
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        String adminAuth = "Bearer " + jwt.issue(admin);

        mvc.perform(get("/api/admin/pos/devices").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(s.getId().toString())))
                .andExpect(jsonPath("$[*].userEmail", hasItem(staff.getEmail())));

        mvc.perform(get("/api/admin/pos/devices").header("Authorization", "Bearer " + jwt.issue(staff)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/pos/devices/" + s.getId() + "/release").header("Authorization", adminAuth))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/pos/devices").header("Authorization", adminAuth))
                .andExpect(jsonPath("$[*].id", not(hasItem(s.getId().toString()))));
    }
}
