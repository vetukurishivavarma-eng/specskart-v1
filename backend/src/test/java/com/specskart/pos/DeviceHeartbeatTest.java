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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** A POS phone that updates in place shows its new version on the next request, not the next sign-in. */
@SpringBootTest
@ActiveProfiles("mock")
class DeviceHeartbeatTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired DeviceSessionService deviceSessions;
    @Autowired DeviceSessionRepository sessions;

    @Test
    void anyRequestRecordsTheAppVersion() throws Exception {
        User u = new User();
        u.setEmail("heartbeat-" + System.nanoTime() + "@specskart.local");
        u.setPasswordHash("x");
        u.setFullName("Heartbeat Till");
        u.setRole(Role.ADMIN);
        u = users.save(u);
        String deviceId = "dev-" + System.nanoTime();
        DeviceSession s = deviceSessions.claim(u.getId(), deviceId, "Test Tab", "android", "1.11.0");

        MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        mvc.perform(get("/api/admin/leads").header("Authorization", "Bearer " + jwt.issue(u))
                .header("X-Device-Id", deviceId).header("X-App-Version", "1.13.0"));

        assertThat(sessions.findById(s.getId()).orElseThrow().getAppVersion()).isEqualTo("1.13.0");

        // a request from another device id must not touch this session
        mvc.perform(get("/api/admin/leads").header("Authorization", "Bearer " + jwt.issue(u))
                .header("X-Device-Id", "someone-else").header("X-App-Version", "9.9.9"));
        assertThat(sessions.findById(s.getId()).orElseThrow().getAppVersion()).isEqualTo("1.13.0");
    }
}
