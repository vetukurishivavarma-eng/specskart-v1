package com.specskart.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The jump page a staff WhatsApp alert links to. It has to be reachable without a login — the
 * packer taps it straight from the message — and it has to carry the order's id through to the
 * app's own URL scheme, or the app opens on a list with no idea which order was meant.
 */
@SpringBootTest
@ActiveProfiles("mock")
class AppLinkTest {

    @Autowired WebApplicationContext ctx;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    @Test
    void lensLinkOpensTheAppOnThatOrder() throws Exception {
        UUID id = UUID.randomUUID();
        String html = mvc().perform(get("/api/public/open/lens/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("specskartpos://orders?id=" + id);
        // Both the automatic jump and the tappable fallback, or a phone that blocks the
        // redirect leaves the packer on a dead page.
        assertThat(html).contains("location.replace(\"specskartpos://orders?id=" + id + "\")");
        assertThat(html).contains("href=\"specskartpos://orders?id=" + id + "\"");
    }

    @Test
    void webOrderLinkOpensTheDeliveriesScreen() throws Exception {
        UUID id = UUID.randomUUID();
        String html = mvc().perform(get("/api/public/open/order/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("specskartpos://deliveries?id=" + id);
    }

    /** An id that isn't a UUID never reaches the markup — that is what keeps the page unescaped. */
    @Test
    void aNonUuidIsRefused() throws Exception {
        mvc().perform(get("/api/public/open/lens/not-a-uuid\"><script>"))
                .andExpect(status().is4xxClientError());
    }
}
