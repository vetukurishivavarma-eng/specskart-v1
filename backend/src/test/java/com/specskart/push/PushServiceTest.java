package com.specskart.push;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class PushServiceTest {

    @Autowired PushTokenRepository tokens;
    @Autowired ObjectMapper mapper;

    @Test
    void offWithoutServiceAccount() {
        PushService push = new PushService(tokens, mapper, "", "http://unused");
        assertThat(push.enabled()).isFalse();
        assertThat(push.notifyStaff("t", "b", null)).isZero();
    }

    @Test
    void signsInSendsToEveryPhoneAndDropsDeadTokens() throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("RSA").genKeyPair();
        List<String> sends = new CopyOnWriteArrayList<>();
        List<String> assertions = new CopyOnWriteArrayList<>();
        String live = "live-" + UUID.randomUUID(), dead = "dead-" + UUID.randomUUID();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", ex -> {
            String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            for (String kv : form.split("&")) {
                if (kv.startsWith("assertion=")) assertions.add(URLDecoder.decode(kv.substring(10), StandardCharsets.UTF_8));
            }
            byte[] out = "{\"access_token\":\"at-1\",\"expires_in\":3600}".getBytes();
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.createContext("/v1/projects/proj/messages:send", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            sends.add(ex.getRequestHeaders().getFirst("Authorization") + " " + body);
            boolean gone = body.contains(dead);
            byte[] out = (gone ? "{\"error\":{\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}" : "{}").getBytes();
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(gone ? 404 : 200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            String pem = "-----BEGIN PRIVATE KEY-----\\n"
                    + Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()) + "\\n-----END PRIVATE KEY-----\\n";
            String sa = "{\"project_id\":\"proj\",\"client_email\":\"svc@proj.iam.gserviceaccount.com\","
                    + "\"token_uri\":\"" + base + "/token\",\"private_key\":\"" + pem + "\"}";

            for (String t : List.of(live, dead)) {
                PushToken row = new PushToken();
                row.setToken(t);
                row.setUserEmail("staff@specskart.local");
                tokens.save(row);
            }

            PushService push = new PushService(tokens, mapper, sa, base);
            assertThat(push.enabled()).isTrue();
            int ok = push.notifyStaff("New lens order LENS-1", "Clear lenses", "/orders?id=abc");

            assertThat(ok).isGreaterThanOrEqualTo(1);
            // Google gets a JWT signed with the service account's key, for the messaging scope.
            var claims = Jwts.parser().verifyWith(kp.getPublic()).build().parseSignedClaims(assertions.get(0)).getPayload();
            assertThat(claims.getIssuer()).isEqualTo("svc@proj.iam.gserviceaccount.com");
            assertThat(claims.get("scope", String.class)).contains("firebase.messaging");
            // Each phone gets the notification, on the orders channel, with where to go on tap.
            assertThat(sends).anyMatch(s -> s.startsWith("Bearer at-1") && s.contains(live)
                    && s.contains("New lens order LENS-1") && s.contains("/orders?id=abc") && s.contains("\"channel_id\":\"orders\""));
            // An uninstalled app's token is forgotten.
            assertThat(tokens.findByToken(dead)).isEmpty();
            assertThat(tokens.findByToken(live)).isPresent();
        } finally {
            server.stop(0);
        }
    }
}
