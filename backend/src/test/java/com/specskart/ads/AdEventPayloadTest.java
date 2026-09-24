package com.specskart.ads;

import com.specskart.lens.LensInquiry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The payloads are the whole risk here: a wrong key is accepted with a 200 and silently
 * ignored, so a broken event looks exactly like a working one until a month of ad spend has
 * been optimised against nothing.
 */
class AdEventPayloadTest {

    private LensInquiry inquiry() {
        LensInquiry q = new LensInquiry();
        q.setId(UUID.fromString("3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"));
        q.setWaId("260977123456");
        q.setPriceMinor(25_000L); // K250.00
        q.setAttribution(new java.util.HashMap<>(Map.of(
                "fbclid", "IwAR-click",
                "ttclid", "tt-click",
                "landing_page", "/lens?fbclid=IwAR-click")));
        return q;
    }

    @Test
    @SuppressWarnings("unchecked")
    void metaGetsAHashedPhoneAFormattedClickIdAndTheValueInKwachaNotNgwee() {
        var body = AdEventService.metaPayload(inquiry(), "Purchase", "LENS-3F2B1C4D:Purchase",
                1_700_000_000L, 25_000L, null);

        var event = ((List<Map<String, Object>>) body.get("data")).get(0);
        assertThat(event.get("event_name")).isEqualTo("Purchase");
        assertThat(event.get("event_id")).isEqualTo("LENS-3F2B1C4D:Purchase");
        assertThat(event.get("action_source")).isEqualTo("website");
        assertThat(event.get("event_source_url")).isEqualTo("/lens?fbclid=IwAR-click");

        var user = (Map<String, Object>) event.get("user_data");
        // never the raw number
        assertThat(user.get("ph")).isEqualTo(List.of(AdEventService.hash("260977123456")));
        assertThat(String.valueOf(user.get("ph"))).doesNotContain("260977123456");
        // fbc is fb.<subdomain>.<click ms>.<fbclid>, not the bare fbclid
        assertThat(user.get("fbc")).isEqualTo("fb.1.1700000000000.IwAR-click");

        var custom = (Map<String, Object>) event.get("custom_data");
        assertThat(custom.get("value")).isEqualTo(250.0);
        assertThat(custom.get("currency")).isEqualTo("ZMW");

        assertThat(body).doesNotContainKey("test_event_code");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tiktokGetsItsOwnShapeAndTheClickIdItIssued() {
        var body = AdEventService.tiktokPayload(inquiry(), "CompletePayment", "e-1",
                1_700_000_000L, 25_000L, "TT-PIXEL");

        assertThat(body.get("event_source")).isEqualTo("web");
        assertThat(body.get("event_source_id")).isEqualTo("TT-PIXEL");

        var event = ((List<Map<String, Object>>) body.get("data")).get(0);
        assertThat(event.get("event")).isEqualTo("CompletePayment");
        var user = (Map<String, Object>) event.get("user");
        assertThat(user.get("phone")).isEqualTo(AdEventService.hash("260977123456"));
        assertThat(user.get("ttclid")).isEqualTo("tt-click");
        assertThat(((Map<String, Object>) event.get("properties")).get("value")).isEqualTo(250.0);
    }

    /** A Lead carries no money — sending value 0 would teach the platform the wrong thing. */
    @Test
    @SuppressWarnings("unchecked")
    void aLeadEventHasNoValue() {
        var body = AdEventService.metaPayload(inquiry(), "Lead", "e-2", 1_700_000_000L, null, null);
        var event = ((List<Map<String, Object>>) body.get("data")).get(0);
        assertThat(event).doesNotContainKey("custom_data");
    }

    /** An organic order still reports — it just has nothing to match on beyond the phone. */
    @Test
    @SuppressWarnings("unchecked")
    void noClickIdsStillProducesAValidEvent() {
        LensInquiry q = inquiry();
        q.setAttribution(new java.util.HashMap<>());

        var event = ((List<Map<String, Object>>) AdEventService
                .metaPayload(q, "Lead", "e-3", 1_700_000_000L, null, null).get("data")).get(0);
        var user = (Map<String, Object>) event.get("user_data");
        assertThat(user).containsKey("ph").doesNotContainKey("fbc");
        assertThat(event).doesNotContainKey("event_source_url");
    }

    @Test
    void hashingIsSha256HexAndCaseInsensitive() {
        // The published SHA-256 of "abc" -- if this drifts, so does every match on the platform side.
        assertThat(AdEventService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(AdEventService.hash("ABC")).isEqualTo(AdEventService.hash("abc"));
        assertThat(AdEventService.hash("260977123456")).matches("[0-9a-f]{64}");
        assertThat(AdEventService.hash(null)).isNull();
        assertThat(AdEventService.hash("  ")).isNull();
    }
}
