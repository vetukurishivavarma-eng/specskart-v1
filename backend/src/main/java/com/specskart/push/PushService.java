package com.specskart.push;

import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Phone notifications to staff through Firebase Cloud Messaging (free, no per-message cost).
 *
 * Configured by one env var, FCM_SERVICE_ACCOUNT_JSON: the service-account key file downloaded from
 * the Firebase console, pasted whole. Unset = push is off and every call is a no-op, so nothing
 * breaks before Firebase is set up. Talks to the FCM HTTP v1 API directly -- the OAuth token is a
 * service-account JWT signed with jjwt, which is already a dependency, so no Google SDK is added.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    /** Must match the channel the POS app creates (src/lib/push.ts). */
    public static final String CHANNEL = "orders";

    private final PushTokenRepository tokens;
    private final RestClient http = RestClient.create();
    private final String projectId, clientEmail, tokenUri, fcmBase;
    private final PrivateKey key;

    private String accessToken;
    private Instant accessTokenExpires = Instant.EPOCH;

    public PushService(PushTokenRepository tokens, ObjectMapper mapper,
                       @Value("${FCM_SERVICE_ACCOUNT_JSON:}") String serviceAccountJson,
                       @Value("${FCM_BASE_URL:https://fcm.googleapis.com}") String fcmBase) {
        this.tokens = tokens;
        this.fcmBase = fcmBase;
        String project = null, email = null, uri = null;
        PrivateKey pk = null;
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            try {
                JsonNode sa = mapper.readTree(serviceAccountJson);
                project = sa.path("project_id").asText();
                email = sa.path("client_email").asText();
                uri = sa.path("token_uri").asText("https://oauth2.googleapis.com/token");
                pk = parseKey(sa.path("private_key").asText());
            } catch (Exception e) {
                log.error("FCM_SERVICE_ACCOUNT_JSON is set but unreadable -- push notifications are OFF: {}", e.getMessage());
                project = null;
            }
        } else {
            log.warn("FCM_SERVICE_ACCOUNT_JSON not set -- staff push notifications are OFF");
        }
        this.projectId = project;
        this.clientEmail = email;
        this.tokenUri = uri;
        this.key = pk;
    }

    public boolean enabled() {
        return projectId != null && key != null;
    }

    /**
     * Sends to every registered staff phone. Never throws: a notification must not fail the order
     * that triggered it. A token Firebase says is dead (app uninstalled) is dropped.
     *
     * @param path POS app route to open on tap, e.g. "/orders?id=...", or null for the home screen
     * @return how many phones accepted it
     */
    @Transactional
    public int notifyStaff(String title, String body, String path) {
        if (!enabled()) return 0;
        int sent = 0;
        for (PushToken t : tokens.findAll()) {
            Map<String, Object> message = new HashMap<>();
            message.put("token", t.getToken());
            message.put("notification", Map.of("title", title, "body", body));
            message.put("data", Map.of("path", path == null ? "/" : path));
            message.put("android", Map.of("priority", "HIGH",
                    "notification", Map.of("channel_id", CHANNEL, "sound", "default")));
            try {
                http.post().uri(fcmBase + "/v1/projects/" + projectId + "/messages:send")
                        .header("Authorization", "Bearer " + accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("message", message))
                        .retrieve().toBodilessEntity();
                sent++;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404 || e.getResponseBodyAsString().contains("UNREGISTERED")) {
                    tokens.delete(t);
                    log.info("push token for {} no longer valid, removed", t.getUserEmail());
                } else {
                    log.warn("push to {} failed: {} {}", t.getUserEmail(), e.getStatusCode(), e.getResponseBodyAsString());
                }
            } catch (Exception e) {
                log.warn("push to {} failed: {}", t.getUserEmail(), e.getMessage());
            }
        }
        return sent;
    }

    /** OAuth access token for the service account, cached until a minute before it expires. */
    private synchronized String accessToken() {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpires)) return accessToken;
        Date now = new Date();
        String assertion = Jwts.builder()
                .issuer(clientEmail)
                .audience().add(tokenUri).and()
                .claim("scope", SCOPE)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000))
                .signWith(key, Jwts.SIG.RS256)
                .compact();
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", assertion);
        JsonNode res = http.post().uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(JsonNode.class);
        accessToken = res.path("access_token").asText();
        accessTokenExpires = Instant.now().plusSeconds(res.path("expires_in").asLong(3600) - 60);
        return accessToken;
    }

    static PrivateKey parseKey(String pem) throws Exception {
        String b64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
    }
}
