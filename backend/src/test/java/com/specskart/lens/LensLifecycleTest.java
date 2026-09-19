package com.specskart.lens;

import com.specskart.catalog.Review;
import com.specskart.catalog.ReviewRepository;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.InboundMessage;
import com.specskart.whatsapp.WhatsAppInboundService;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two ends of the lens funnel that nothing used to chase: a shopper who saw their price and
 * stopped, and a customer who has had their lenses for a few days.
 */
@SpringBootTest
@ActiveProfiles("mock")
class LensLifecycleTest {

    @Autowired LensInquiryFollowUpJob followUpJob;
    @Autowired LensPostPurchaseJob postPurchaseJob;
    @Autowired LensInquiryRepository inquiries;
    @Autowired LeadRepository leads;
    @Autowired WhatsAppProvider provider;
    @Autowired ReviewRepository reviews;
    @Autowired WhatsAppInboundService inbound;
    @Autowired EntityManager em;

    private int mark() {
        return ((MockWhatsAppProvider) provider).outbox().size();
    }

    /** The outbox is shared across the whole suite — only look at what this test provoked. */
    private List<MockWhatsAppProvider.Sent> since(int mark) {
        var outbox = ((MockWhatsAppProvider) provider).outbox();
        return List.copyOf(outbox.subList(mark, outbox.size()));
    }

    private static String someNumber() {
        return "2609" + (long) (Math.random() * 100_000_000L);
    }

    private Lead lead(String waId) {
        Lead l = new Lead();
        l.setWhatsappWaId(waId);
        l.setWhatsappNumber(waId);
        l.setName("Mercy Tembo");
        return leads.saveAndFlush(l);
    }

    private LensInquiry inquiry(String waId, java.util.UUID leadId, String status) {
        LensInquiry q = new LensInquiry();
        q.setLeadId(leadId);
        q.setVerifyTokenHash("life-" + System.nanoTime());
        q.setPhoneRaw(waId);
        q.setWaId(waId);
        q.setLensType("PHOTOCHROMATIC");
        q.setStatus(status);
        q.setPriceMinor(45_000L);
        return inquiries.saveAndFlush(q);
    }

    /** createdAt/updatedAt are set by the entity's own auditing, so age them by hand. */
    private void age(LensInquiry q, int daysAgo) {
        Instant when = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        em.createQuery("update LensInquiry i set i.createdAt = :when, i.updatedAt = :when where i.id = :id")
                .setParameter("when", when)
                .setParameter("id", q.getId())
                .executeUpdate();
        em.clear();
    }

    @Test
    @Transactional
    void aShopperWhoSawThePriceAndStoppedGetsItQuotedBack() {
        String waId = someNumber();
        LensInquiry q = inquiry(waId, lead(waId).getId(), "PRICED");
        age(q, 1);

        int mark = mark();
        followUpJob.nudge();

        var mine = since(mark).stream().filter(s -> waId.equals(s.toWaId())).toList();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).text()).contains("450.00").contains("/lens?resume=");
    }

    @Test
    @Transactional
    void theOlderStallPointStillGetsThePlainNudge() {
        String waId = someNumber();
        LensInquiry q = inquiry(waId, lead(waId).getId(), "VERIFIED");
        age(q, 1);

        int mark = mark();
        followUpJob.nudge();

        var mine = since(mark).stream().filter(s -> waId.equals(s.toWaId())).toList();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).text()).contains("Still want your");
    }

    @Test
    @Transactional
    void aLensCustomerIsThankedOnceWithAComebackCodeAndAReferralCode() {
        String waId = someNumber();
        LensInquiry q = inquiry(waId, lead(waId).getId(), "SOLD");
        age(q, 5);

        int mark = mark();
        postPurchaseJob.run();

        var mine = since(mark).stream().filter(s -> waId.equals(s.toWaId())).toList();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).text())
                .contains("How are the new lenses")
                .contains("off your next pair")
                .contains("Share code")
                .contains("STOP");

        // Once only — the next hourly pass must not thank them again.
        int second = mark();
        postPurchaseJob.run();
        assertThat(since(second).stream().filter(s -> waId.equals(s.toWaId()))).isEmpty();
    }

    @Test
    @Transactional
    void aStarRatingRepliedToThatAskIsRecordedAgainstTheLensSale() {
        String waId = someNumber();
        LensInquiry q = inquiry(waId, lead(waId).getId(), "SOLD");
        age(q, 5);
        postPurchaseJob.run();

        int mark = mark();
        inbound.process(new InboundMessage(waId, waId, "Mercy", "5", null, "rate:" + waId, java.util.Map.of()));

        Review saved = reviews.findAll().stream()
                .filter(r -> q.getId().equals(r.getLensInquiryId())).findFirst().orElseThrow();
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(saved.getProductId()).isNull();
        assertThat(saved.getOrderId()).isNull();
        assertThat(since(mark)).anyMatch(s -> s.text() != null && s.text().contains("Thanks for the"));

        // A lens rating must never land in a product's average.
        assertThat(reviews.aggregateAll()).allMatch(a -> a.getProductId() != null);
    }

    @Test
    @Transactional
    void abareNumberIsNotARatingWhenNothingWasAsked() {
        String waId = someNumber();
        lead(waId);

        int mark = mark();
        inbound.process(new InboundMessage(waId, waId, "Mercy", "5", null, "bare:" + waId, java.util.Map.of()));

        assertThat(reviews.findAll()).noneMatch(r -> r.getRating() == 5 && r.getLensInquiryId() != null
                && r.getLeadId().equals(leads.findByWhatsappWaId(waId).orElseThrow().getId()));
        assertThat(since(mark)).noneMatch(s -> s.text() != null && s.text().contains("Thanks for the"));
    }

    @Test
    @Transactional
    void afreshSaleIsLeftAloneUntilTheLensesHaveBeenWornABit() {
        String waId = someNumber();
        inquiry(waId, lead(waId).getId(), "SOLD"); // sold just now

        int mark = mark();
        postPurchaseJob.run();

        assertThat(since(mark).stream().filter(s -> waId.equals(s.toWaId()))).isEmpty();
    }
}
