package com.specskart;

import com.specskart.framefinder.FrameFinderService;
import com.specskart.framefinder.FrameFinderSession;
import com.specskart.framefinder.FrameFinderSessionRepository;
import com.specskart.framefinder.FrameFinderSessionStatus;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("mock")
class ApiContractTest {

    @Autowired WebApplicationContext ctx;
    @Autowired FrameFinderService frameFinder;
    @Autowired FrameFinderSessionRepository sessions;
    @Autowired LeadRepository leads;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    @Test
    void badLoginReturns401WithCode() throws Exception {
        mvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@x.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void adminEndpointsRequireAuth() throws Exception {
        mvc().perform(get("/api/admin/leads")).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredFrameFinderSessionReturns410WithCode() throws Exception {
        Lead lead = new Lead();
        lead.setWhatsappWaId("contract-" + System.nanoTime());
        lead = leads.save(lead);
        var created = frameFinder.createForLead(lead.getId(), null);
        FrameFinderSession s = sessions.findById(created.session().getId()).orElseThrow();
        s.setExpiresAt(Instant.now().minusSeconds(5));
        s.setStatus(FrameFinderSessionStatus.OPENED);
        sessions.save(s);

        // token is only in the URL that was generated; re-derive from the created URL
        String token = created.url().substring(created.url().indexOf("s=") + 2);
        mvc().perform(get("/api/frame-finder/session/{t}", token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("FRAME_SESSION_EXPIRED"));
    }

    @Test
    void unknownFrameFinderTokenReturns404WithCode() throws Exception {
        mvc().perform(get("/api/frame-finder/session/{t}", "definitely-not-a-real-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRAME_SESSION_NOT_FOUND"));
    }
}
