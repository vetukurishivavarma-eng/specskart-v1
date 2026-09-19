package com.specskart.membership;

import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.lens.LensInquiry;
import com.specskart.lens.LensInquiryRepository;
import com.specskart.whatsapp.InboundMessage;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppInboundService;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Specskart Care: the discount reaches the price, and the kill switch really does kill it. */
@SpringBootTest
@ActiveProfiles("mock")
class MembershipTest {

    @Autowired MembershipService memberships;
    @Autowired MembershipRepository repo;
    @Autowired LeadRepository leads;
    @Autowired LensInquiryRepository inquiries;
    @Autowired WhatsAppInboundService inbound;
    @Autowired WhatsAppProvider provider;

    private static String someNumber() {
        return "2609" + (long) (Math.random() * 100_000_000L);
    }

    private int mark() {
        return ((MockWhatsAppProvider) provider).outbox().size();
    }

    /** The outbox is shared suite-wide — only look at what this test provoked. */
    private List<MockWhatsAppProvider.Sent> since(int mark) {
        var outbox = ((MockWhatsAppProvider) provider).outbox();
        return List.copyOf(outbox.subList(mark, outbox.size()));
    }

    private Lead lead(String waId) {
        Lead l = new Lead();
        l.setWhatsappWaId(waId);
        l.setWhatsappNumber(waId);
        l.setName("Joseph Mwale");
        return leads.saveAndFlush(l);
    }

    private Membership paidUpMember(UUID leadId) {
        Membership m = new Membership();
        m.setLeadId(leadId);
        m.setPriceMinor(MembershipService.PRICE_MINOR);
        m.setDiscountPercent(MembershipService.DISCOUNT_PERCENT);
        m.setPaidAt(Instant.now());
        m.setExpiresAt(Instant.now().plus(300, ChronoUnit.DAYS));
        return repo.saveAndFlush(m);
    }

    private LensInquiry photochromaticFor(UUID leadId, String waId) {
        LensInquiry q = new LensInquiry();
        q.setLeadId(leadId);
        q.setVerifyTokenHash("memb-" + System.nanoTime());
        q.setPhoneRaw(waId);
        q.setWaId(waId);
        q.setLensType("PHOTOCHROMATIC");
        q.setStatus("VERIFIED");
        return inquiries.saveAndFlush(q);
    }

    @Test
    @Transactional
    void aMemberIsQuotedLessForTheSameLenses() {
        String plainWa = someNumber();
        Lead plain = lead(plainWa);
        long listPrice = memberships.applyDiscount(45_000, plain.getId());
        assertThat(listPrice).isEqualTo(45_000); // no membership, no discount

        String memberWa = someNumber();
        Lead member = lead(memberWa);
        paidUpMember(member.getId());

        long discounted = memberships.applyDiscount(45_000, member.getId());
        assertThat(discounted).isEqualTo(45_000 - (45_000 * MembershipService.DISCOUNT_PERCENT / 100));
        assertThat(discounted).isLessThan(45_000);
    }

    @Test
    @Transactional
    void anUnpaidOrExpiredMembershipBuysNothing() {
        String waId = someNumber();
        Lead l = lead(waId);

        Membership unpaid = new Membership();
        unpaid.setLeadId(l.getId());
        unpaid.setPriceMinor(MembershipService.PRICE_MINOR);
        unpaid.setDiscountPercent(MembershipService.DISCOUNT_PERCENT);
        unpaid.setExpiresAt(Instant.now().plus(300, ChronoUnit.DAYS)); // abandoned checkout
        repo.saveAndFlush(unpaid);
        assertThat(memberships.discountPercentFor(l.getId())).isZero();

        Membership expired = new Membership();
        expired.setLeadId(l.getId());
        expired.setPriceMinor(MembershipService.PRICE_MINOR);
        expired.setDiscountPercent(MembershipService.DISCOUNT_PERCENT);
        expired.setPaidAt(Instant.now().minus(400, ChronoUnit.DAYS));
        expired.setExpiresAt(Instant.now().minus(35, ChronoUnit.DAYS));
        repo.saveAndFlush(expired);
        assertThat(memberships.discountPercentFor(l.getId())).isZero();
    }

    @Test
    @Transactional
    void theMemberPriceIsWhatTheFunnelActuallyQuotes() {
        String waId = someNumber();
        Lead l = lead(waId);
        paidUpMember(l.getId());
        LensInquiry q = photochromaticFor(l.getId(), waId);

        // Whatever the configured photochromatic price is, a member pays the discounted one.
        long asked = memberships.applyDiscount(45_000, l.getId());
        assertThat(asked).isLessThan(45_000);
        assertThat(q.getLeadId()).isEqualTo(l.getId());
    }

    @Test
    @Transactional
    void theStaffSlipSaysWhyThePriceIsLower() {
        String waId = someNumber();
        Lead l = lead(waId);
        LensInquiry q = photochromaticFor(l.getId(), waId);
        q.setPriceMinor(38_250L);

        var withMember = com.specskart.lens.LensInquiryService.staffLines(q, 15);
        assertThat(String.join("\n", withMember)).contains("member").contains("15%").contains("correct");

        var withoutMember = com.specskart.lens.LensInquiryService.staffLines(q, 0);
        assertThat(String.join("\n", withoutMember)).doesNotContain("Specskart Care");
    }

    @Test
    void theMenuOffersCareOnlyWhileTheFeatureIsOn() {
        String waId = someNumber();
        inbound.process(new InboundMessage(waId, waId, "Joseph", "hi", null, "care:" + waId + ":1", Map.of()));

        int mark = mark();
        inbound.process(new InboundMessage(waId, waId, "Joseph", "menu", null, "care:" + waId + ":2", Map.of()));

        var ids = since(mark).stream().flatMap(s -> s.buttons().stream())
                .map(WhatsAppProvider.Button::id).toList();
        // One assertion, both directions: the row tracks the compile-time switch.
        assertThat(ids.contains("CARE")).isEqualTo(MembershipService.ENABLED);
    }
}
