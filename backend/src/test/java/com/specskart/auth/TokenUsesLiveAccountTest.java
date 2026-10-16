package com.specskart.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A token is only as good as the account behind it right now; staff can't message every customer. */
@SpringBootTest
@ActiveProfiles("mock")
class TokenUsesLiveAccountTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    private User user(Role role) {
        User u = new User();
        u.setEmail("live-" + System.nanoTime() + "@specskart.local");
        u.setPasswordHash("x");
        u.setFullName("Live " + role);
        u.setRole(role);
        return users.save(u);
    }

    @Test
    void deactivatedOrDemotedLoginLosesAccessAtOnce() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        User u = user(Role.ADMIN);
        String token = "Bearer " + jwt.issue(u);
        mvc.perform(get("/api/admin/users").header("Authorization", token)).andExpect(status().isOk());

        u.setRole(Role.AGENT);
        users.save(u);
        mvc.perform(get("/api/admin/users").header("Authorization", token)).andExpect(status().isForbidden());

        u.setActive(false);
        users.save(u);
        mvc.perform(get("/api/admin/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotBroadcast() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        mvc.perform(post("/api/admin/whatsapp/broadcast/send").header("Authorization", "Bearer " + jwt.issue(user(Role.AGENT)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
