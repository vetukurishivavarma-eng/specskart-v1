package com.specskart.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/** Expiring HMAC-signed query strings for public links that must not be guessable from an id
 *  alone — e.g. the prescription PDF Meta fetches when it delivers a staff WhatsApp. */
@Component
public class SignedLinks {

    private final byte[] key;

    public SignedLinks(@Value("${specskart.security.jwt-secret}") String secret) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** "?exp=…&sig=…" granting access to {@code resource} for {@code ttl}. */
    public String query(String resource, Duration ttl) {
        long exp = Instant.now().plus(ttl).getEpochSecond();
        return "?exp=" + exp + "&sig=" + sign(resource, exp);
    }

    public boolean valid(String resource, long exp, String sig) {
        if (sig == null || Instant.now().getEpochSecond() > exp) return false;
        return MessageDigest.isEqual(sign(resource, exp).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String resource, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal((resource + "|" + exp).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
