package com.specskart.pos;

import com.specskart.auth.JwtService;
import com.specskart.auth.Role;
import com.specskart.auth.User;
import com.specskart.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two generations of till read this one endpoint and must both behave.
 *
 * Build 7 and older take `mandatory` and compare `minimumBuild` themselves. Build 8 onward
 * sends its `build` and lets the server decide, reading "blocked = mandatory || used >=
 * graceCount". Getting this wrong in either direction is expensive: too strict locks every
 * shop out of its till at once, too loose leaves them on a build that cannot sell.
 */
@SpringBootTest
@ActiveProfiles("mock")
class AppVersionCheckTest {

    @Autowired AppVersionController controller;
    @Autowired AppReleaseRepository releases;
    @Autowired WebApplicationContext ctx;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    @BeforeEach
    void clear() {
        releases.deleteAll();
    }

    private void publish(int build, int minimumBuild, boolean mandatory, int graceCount) {
        AppRelease r = new AppRelease();
        r.setVersion("1." + build + ".0");
        r.setBuildNumber(build);
        r.setMinimumBuild(minimumBuild);
        r.setDownloadUrl("https://example.test/app.apk");
        r.setMandatory(mandatory);
        r.setGraceCount(graceCount);
        releases.save(r);
    }

    /** No release row must never lock a shop out — the till treats a failed check the same way. */
    @Test
    void nothingPublishedMeansNoUpdate() {
        var v = controller.version("android", 7);
        assertThat(v.updateAvailable()).isFalse();
        assertThat(v.mandatory()).isFalse();
    }

    @Test
    void aTillOnTheCurrentBuildIsOfferedNothing() {
        publish(8, 8, false, 2);
        assertThat(controller.version("android", 8).updateAvailable()).isFalse();
        // and one somehow ahead of the server is left alone too
        assertThat(controller.version("android", 9).updateAvailable()).isFalse();
    }

    @Test
    void anOptionalUpdateCarriesItsPostponements() {
        publish(9, 7, false, 2);
        var v = controller.version("android", 8);
        assertThat(v.updateAvailable()).isTrue();
        assertThat(v.mandatory()).isFalse();
        assertThat(v.graceCount()).isEqualTo(2);
    }

    /** Below the floor is compulsory even when the release was not flagged mandatory. Build 8
     *  reads that off graceCount = 0, which makes "used >= graceCount" true from the first ask. */
    @Test
    void aTillUnderTheFloorGetsNoPostponements() {
        publish(9, 9, false, 2);
        var v = controller.version("android", 8);
        assertThat(v.updateAvailable()).isTrue();
        assertThat(v.graceCount()).isZero();
    }

    @Test
    void aMandatoryReleaseSpendsNoGraceEither() {
        publish(9, 7, true, 2);
        var v = controller.version("android", 8);
        assertThat(v.mandatory()).isTrue();
        assertThat(v.graceCount()).isZero();
    }

    /**
     * There is no edit or delete for a release, so the only way to correct one published with
     * a wrong URL or floor is to publish that build again through the admin endpoint. (platform,
     * build_number) is unique, so that must overwrite the row, not 500 on the constraint -- which
     * is exactly what happened in prod on 2026-10-15 with a mistyped download URL.
     */
    @Test
    void republishingTheSameBuildCorrectsIt() throws Exception {
        User admin = new User();
        admin.setEmail("rel-" + System.nanoTime() + "@specskart.local");
        admin.setPasswordHash("x");
        admin.setFullName("Admin");
        admin.setRole(Role.ADMIN);
        String auth = "Bearer " + jwt.issue(users.save(admin));
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        String body = "{\"version\":\"1.9.0\",\"buildNumber\":9,\"minimumBuild\":%d,\"downloadUrl\":\"%s\",\"mandatory\":true}";

        mvc.perform(post("/api/admin/pos/releases").header("Authorization", auth).contentType("application/json")
                .content(body.formatted(7, "https://bad\\n\\n/app.apk"))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/pos/releases").header("Authorization", auth).contentType("application/json")
                .content(body.formatted(9, "https://good/app.apk"))).andExpect(status().isOk());

        var v = controller.version("android", 7);
        assertThat(v.minimumBuild()).isEqualTo(9);
        assertThat(v.downloadUrl()).isEqualTo("https://good/app.apk");
        assertThat(releases.count()).isEqualTo(1);
    }

    /**
     * Build 7 sends no `build` at all. It must still see the raw flag and the floor, because it
     * does its own comparing — and it must NOT be handed a mandatory derived from the floor,
     * which for a missing build would always be true and would wall every old till instantly.
     */
    @Test
    void anOlderTillThatSendsNoBuildStillReadsTheOriginalFields() {
        publish(9, 7, false, 2);
        var v = controller.version("android", null);
        assertThat(v.version()).isEqualTo("1.9.0");
        assertThat(v.buildNumber()).isEqualTo(9);
        assertThat(v.minimumBuild()).isEqualTo(7);
        assertThat(v.mandatory()).isFalse(); // the raw flag, not the computed one
    }
}
