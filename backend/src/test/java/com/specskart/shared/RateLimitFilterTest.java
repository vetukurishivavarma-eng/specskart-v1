package com.specskart.shared;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    private MockHttpServletRequest req(String uri, String ip) {
        var r = new MockHttpServletRequest("POST", uri);
        r.setRemoteAddr(ip);
        return r;
    }

    @Test
    void allowsUpToTheLimitThenReturns429() throws Exception {
        FilterChain chain = (request, response) -> {};
        int passed = 0;
        MockHttpServletResponse last = null;
        for (int i = 0; i < 65; i++) {
            last = new MockHttpServletResponse();
            filter.doFilter(req("/api/frame-finder/session/x", "1.2.3.4"), last, chain);
            if (last.getStatus() == 200) passed++;
        }
        assertThat(passed).isEqualTo(60);
        assertThat(last.getStatus()).isEqualTo(429);
        assertThat(last.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void doesNotRateLimitAuthenticatedAdminPaths() {
        assertThat(filter.shouldNotFilter(req("/api/admin/leads", "1.1.1.1"))).isTrue();
        assertThat(filter.shouldNotFilter(req("/api/webhooks/whatsapp", "1.1.1.1"))).isFalse();
    }

    @Test
    void bucketsAreSeparatePerClientIp() throws Exception {
        FilterChain chain = (request, response) -> {};
        for (int i = 0; i < 60; i++) {
            filter.doFilter(req("/api/sim/whatsapp/inbound", "9.9.9.9"), new MockHttpServletResponse(), chain);
        }
        var fresh = new MockHttpServletResponse();
        filter.doFilter(req("/api/sim/whatsapp/inbound", "8.8.8.8"), fresh, chain);
        assertThat(fresh.getStatus()).isEqualTo(200);
    }
}
