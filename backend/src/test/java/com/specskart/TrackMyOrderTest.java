package com.specskart;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** "Where's my order?" in chat answers with the real delivery stage instead of the welcome menu. */
@SpringBootTest
@ActiveProfiles("mock")
class TrackMyOrderTest {

    @Autowired WhatsAppInboundService inbound;
    @Autowired LeadRepository leads;
    @Autowired LensInquiryRepository inquiries;
    @Autowired WhatsAppProvider provider;

    private static String someNumber() {
        return "26097" + (200000 + (int) (Math.random() * 700000));
    }

    private int outboxSize() {
        return ((MockWhatsAppProvider) provider).outbox().size();
    }

    /** The outbox is shared across the whole suite, so only look at what this test just sent. */
    private String replySince(int mark) {
        var outbox = ((MockWhatsAppProvider) provider).outbox();
        return outbox.subList(mark, outbox.size()).stream()
                .map(MockWhatsAppProvider.Sent::text)
                .filter(t -> t != null)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    @Test
    void tellsTheCustomerWhereTheirLensOrderIs() {
        String waId = someNumber();
        inbound.process(new InboundMessage(waId, waId, "Tracker", "hi", null, "t1", Map.of()));
        Lead lead = leads.findByWhatsappWaId(waId).orElseThrow();

        LensInquiry q = new LensInquiry();
        q.setLeadId(lead.getId());
        q.setVerifyTokenHash("hash-" + System.nanoTime());
        q.setPhoneRaw(waId);
        q.setWaId(waId);
        q.setStatus("SUBMITTED");
        q.setFulfilment("OUT_FOR_DELIVERY");
        q.setDeliveryAddress("12 Great East Road");
        inquiries.save(q);

        int mark = outboxSize();
        inbound.process(new InboundMessage(waId, waId, "Tracker", "where is my order", null, "t2", Map.of()));

        assertThat(replySince(mark)).contains("Out for delivery to 12 Great East Road");
    }

    @Test
    void aLeadWithNoOrderIsPointedAtTheLensPageInsteadOfAnEmptyAnswer() {
        String waId = someNumber();
        inbound.process(new InboundMessage(waId, waId, "Browser", "hi", null, "n1", Map.of()));
        int mark = outboxSize();
        inbound.process(new InboundMessage(waId, waId, "Browser", "track my order", null, "n2", Map.of()));

        assertThat(replySince(mark)).contains("can't find an order").contains("/lens");
    }

    @Test
    void aWalkInIsNotTrackable() {
        String waId = someNumber();
        inbound.process(new InboundMessage(waId, waId, "Counter", "hi", null, "w1", Map.of()));
        Lead lead = leads.findByWhatsappWaId(waId).orElseThrow();

        LensInquiry q = new LensInquiry();
        q.setLeadId(lead.getId());
        q.setVerifyTokenHash("hash-" + System.nanoTime());
        q.setPhoneRaw(waId);
        q.setWaId(waId);
        q.setStatus("SOLD");
        q.setWalkIn(true);
        inquiries.save(q);

        int mark = outboxSize();
        inbound.process(new InboundMessage(waId, waId, "Counter", "track my order", null, "w2", Map.of()));

        assertThat(replySince(mark)).contains("can't find an order");
    }
}
