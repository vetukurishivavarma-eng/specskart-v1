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

/** "Where's my order?" in chat answers with the real collection stage instead of the welcome menu. */
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

    /** Message ids are deduped globally against a db shared by the whole suite, so tie them to
     *  this run's waId — a literal id silently turns another test's inbound into a no-op. */
    private static String msgId(String waId, String step) {
        return "track:" + waId + ":" + step;
    }

    private int outboxSize() {
        return ((MockWhatsAppProvider) provider).outbox().size();
    }

    /** Row ids from any list sent since the mark. The mock records list rows as buttons. */
    private java.util.List<String> rowIdsSince(int mark) {
        var outbox = ((MockWhatsAppProvider) provider).outbox();
        return outbox.subList(mark, outbox.size()).stream()
                .filter(sent -> sent.buttons() != null)
                .flatMap(sent -> sent.buttons().stream())
                .map(MockWhatsAppProvider.Button::id)
                .toList();
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
        inbound.process(new InboundMessage(waId, waId, "Tracker", "hi", null, msgId(waId, "t1"), Map.of()));
        Lead lead = leads.findByWhatsappWaId(waId).orElseThrow();

        LensInquiry q = new LensInquiry();
        q.setLeadId(lead.getId());
        q.setVerifyTokenHash("hash-" + System.nanoTime());
        q.setPhoneRaw(waId);
        q.setWaId(waId);
        q.setStatus("SUBMITTED");
        q.setFulfilment("READY");
        q.setShopName("Specskart Lusaka");
        inquiries.save(q);

        int mark = outboxSize();
        inbound.process(new InboundMessage(waId, waId, "Tracker", "where is my order", null, msgId(waId, "t2"), Map.of()));

        // The shop comes from the inventory lookup now, not from the name on the inquiry, so
        // this asserts the shape of the answer rather than a shop this test never created.
        assertThat(replySince(mark)).contains("Ready to collect at:");
    }

    @Test
    void aLeadWithNoOrderIsPointedAtTheLensPageInsteadOfAnEmptyAnswer() {
        String waId = someNumber();
        inbound.process(new InboundMessage(waId, waId, "Browser", "hi", null, msgId(waId, "n1"), Map.of()));
        int mark = outboxSize();
        inbound.process(new InboundMessage(waId, waId, "Browser", "track my order", null, msgId(waId, "n2"), Map.of()));

        assertThat(replySince(mark)).contains("can't find an order").contains("/lens");
    }

    @Test
    void aWalkInIsNotTrackable() {
        String waId = someNumber();
        inbound.process(new InboundMessage(waId, waId, "Counter", "hi", null, msgId(waId, "w1"), Map.of()));
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
        inbound.process(new InboundMessage(waId, waId, "Counter", "track my order", null, msgId(waId, "w2"), Map.of()));

        assertThat(replySince(mark)).contains("can't find an order");
    }

    /**
     * Two orders is where "the newest one" stops being an answer: the customer asked about one
     * of them and has no way to say which. They get a list to pick from instead.
     */
    @Test
    void twoOrdersAreOfferedAsAListToPickFrom() {
        String waId = someNumber();
        inbound.process(new InboundMessage(waId, waId, "Repeat", "hi", null, msgId(waId, "m1"), Map.of()));
        Lead lead = leads.findByWhatsappWaId(waId).orElseThrow();

        LensInquiry older = newInquiry(lead, waId, "DELIVERED");
        LensInquiry newer = newInquiry(lead, waId, "READY");

        int mark = outboxSize();
        inbound.process(new InboundMessage(waId, waId, "Repeat", "track my order", null, msgId(waId, "m2"), Map.of()));

        assertThat(replySince(mark)).contains("Which order did you mean?");
        assertThat(rowIdsSince(mark))
                .contains("TRACK:L:" + older.getId(), "TRACK:L:" + newer.getId());
    }

    /** Tapping a row answers about that order, not whichever happens to be newest. */
    @Test
    void tappingARowAnswersAboutThatOrder() {
        String waId = someNumber();
        inbound.process(new InboundMessage(waId, waId, "Picker", "hi", null, msgId(waId, "p1"), Map.of()));
        Lead lead = leads.findByWhatsappWaId(waId).orElseThrow();

        LensInquiry older = newInquiry(lead, waId, "DELIVERED");
        newInquiry(lead, waId, "READY");

        int mark = outboxSize();
        inbound.process(new InboundMessage(waId, waId, "Picker", null, "TRACK:L:" + older.getId(),
                msgId(waId, "p2"), Map.of()));

        // The older one is DELIVERED; the newer is READY. Getting the collected line back is
        // what proves the tapped row won over recency.
        assertThat(replySince(mark)).contains("Collected");
    }

    private LensInquiry newInquiry(Lead lead, String waId, String fulfilment) {
        LensInquiry q = new LensInquiry();
        q.setLeadId(lead.getId());
        q.setVerifyTokenHash("hash-" + System.nanoTime());
        q.setPhoneRaw(waId);
        q.setWaId(waId);
        q.setStatus("SUBMITTED");
        q.setFulfilment(fulfilment);
        return inquiries.save(q);
    }
}
