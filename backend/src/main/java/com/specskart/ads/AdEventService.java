package com.specskart.ads;

import com.specskart.config.AppProperties;
import com.specskart.lens.LensInquiry;
import com.specskart.lens.LensInquiryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports conversions back to the ad platforms server-side — Meta's Conversions API and
 * TikTok's Events API.
 *
 * Why server-side and not from the pixel: an ad platform can only optimise for what it is
 * told happened. The browser pixel is blocked, cleared or simply not present for a large
 * share of traffic, and our real conversions happen after the shopper has left the page
 * anyway (the number is verified in WhatsApp's browser; the sale is billed in the POS days
 * later). The server sees all of it.
 *
 * The pixel and this service deliberately do NOT send the same events — the pixel only does
 * PageView, for retargeting audiences — so there is nothing to deduplicate. If a browser-side
 * Purchase is ever added, it must share this event_id or Meta will count the sale twice.
 *
 * Every send is best-effort: a dead token or a rate limit must never fail an order.
 */
@Service
public class AdEventService {

    private static final Logger log = LoggerFactory.getLogger(AdEventService.class);

    private static final String META_LEAD = "Lead";
    private static final String META_PURCHASE = "Purchase";
    /** TikTok's own vocabulary for the same two things. */
    private static final String TT_LEAD = "SubmitForm";
    private static final String TT_PURCHASE = "CompletePayment";

    private final RestClient http;
    private final AppProperties props;

    public AdEventService(RestClient http, AppProperties props) {
        this.http = http;
        this.props = props;
    }

    /** They gave us a verified WhatsApp number — the top of the funnel an ad is buying. */
    public void lead(LensInquiry q) {
        send(q, META_LEAD, TT_LEAD, null);
    }

    /**
     * They placed the order. Reported when it is submitted rather than when the money arrives,
     * because with pay-on-collection the cash lands days later and out of the attribution
     * window — the order is the conversion the ad actually produced.
     */
    public void purchase(LensInquiry q) {
        send(q, META_PURCHASE, TT_PURCHASE, q.getPriceMinor());
    }

    private void send(LensInquiry q, String metaEvent, String tiktokEvent, Long valueMinor) {
        var ads = props.ads();
        if (ads == null) return;
        String eventId = LensInquiryService.ref(q) + ":" + metaEvent;
        long now = System.currentTimeMillis() / 1000;
        if (ads.metaConfigured()) {
            post("Meta", ads.metaEventsUrl(),
                    b -> b.header("Content-Type", MediaType.APPLICATION_JSON_VALUE),
                    metaPayload(q, metaEvent, eventId, now, valueMinor, ads.testEventCode()));
        }
        if (ads.tiktokConfigured()) {
            post("TikTok", "https://business-api.tiktok.com/open_api/v1.3/event/track/",
                    b -> b.header("Access-Token", ads.tiktokAccessToken()),
                    tiktokPayload(q, tiktokEvent, eventId, now, valueMinor, ads.tiktokPixelId()));
        }
    }

    private void post(String platform, String url,
                      java.util.function.Consumer<RestClient.RequestBodySpec> headers,
                      Map<String, Object> body) {
        try {
            var spec = http.post().uri(url).contentType(MediaType.APPLICATION_JSON);
            headers.accept(spec);
            spec.body(body).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("{} conversion event failed: {}", platform, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- payloads

    /**
     * Meta's Conversions API event. `user_data` is what it matches against a real person:
     * the hashed phone is our strongest signal (every lens customer has a verified one) and
     * `fbc` ties the event to the exact click that was paid for.
     */
    static Map<String, Object> metaPayload(LensInquiry q, String event, String eventId, long now,
                                           Long valueMinor, String testEventCode) {
        Map<String, Object> user = new LinkedHashMap<>();
        String phone = hash(q.getWaId());
        if (phone != null) user.put("ph", List.of(phone));
        String fbc = fbc(q, now);
        if (fbc != null) user.put("fbc", fbc);

        Map<String, Object> e = new LinkedHashMap<>();
        e.put("event_name", event);
        e.put("event_time", now);
        e.put("event_id", eventId);
        e.put("action_source", "website");
        e.put("user_data", user);
        String landing = str(q, "landing_page");
        if (landing != null) e.put("event_source_url", landing);
        if (valueMinor != null) {
            e.put("custom_data", Map.of("currency", q.getCurrency(), "value", major(valueMinor)));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", List.of(e));
        if (testEventCode != null && !testEventCode.isBlank()) body.put("test_event_code", testEventCode);
        return body;
    }

    /** TikTok's Events API 1.3 event — same idea, different spelling. */
    static Map<String, Object> tiktokPayload(LensInquiry q, String event, String eventId, long now,
                                             Long valueMinor, String pixelId) {
        Map<String, Object> user = new LinkedHashMap<>();
        String phone = hash(q.getWaId());
        if (phone != null) user.put("phone", phone);
        String ttclid = str(q, "ttclid");
        if (ttclid != null) user.put("ttclid", ttclid);

        Map<String, Object> e = new LinkedHashMap<>();
        e.put("event", event);
        e.put("event_time", now);
        e.put("event_id", eventId);
        e.put("user", user);
        if (valueMinor != null) {
            e.put("properties", Map.of("currency", q.getCurrency(), "value", major(valueMinor)));
        }
        String landing = str(q, "landing_page");
        if (landing != null) e.put("page", Map.of("url", landing));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event_source", "web");
        body.put("event_source_id", pixelId);
        body.put("data", List.of(e));
        return body;
    }

    /**
     * Meta's click identifier, which is not the raw fbclid: it wants
     * {@code fb.<subdomain-index>.<click-time-ms>.<fbclid>}. We only know the click happened
     * before this event, so the event's own time is the honest approximation — Meta treats it
     * as a hint for matching, not as billing data.
     */
    private static String fbc(LensInquiry q, long nowSeconds) {
        String fbclid = str(q, "fbclid");
        return fbclid == null ? null : "fb.1." + (nowSeconds * 1000) + "." + fbclid;
    }

    /** Both platforms want SHA-256 hex of the normalised value, never the value itself. */
    static String hash(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null; // SHA-256 is mandated by the JDK; unreachable
        }
    }

    private static String str(LensInquiry q, String key) {
        Object v = q.getAttribution() == null ? null : q.getAttribution().get(key);
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
    }

    /** Both APIs price in major units; we store ngwee. */
    private static double major(long minor) {
        return minor / 100.0;
    }
}
