package com.specskart;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.whatsapp.Faq;
import com.specskart.whatsapp.FaqRepository;
import com.specskart.whatsapp.InboundMessage;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppInboundService;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The top-level list menu, and the typed "BUY n" path kept working alongside tappable rows. */
@SpringBootTest
@ActiveProfiles("mock")
class ChatMenuTest {

    @Autowired WhatsAppInboundService inbound;
    @Autowired ProductRepository products;
    @Autowired WhatsAppProvider provider;
    @Autowired FaqRepository faqs;

    private static String someNumber() {
        return "26097" + (200000 + (int) (Math.random() * 700000));
    }

    /** Ids are deduped globally against a db shared by the whole suite — keep them unique. */
    private static String msgId(String waId, String step) {
        return "menu:" + waId + ":" + step;
    }

    private int mark() {
        return ((MockWhatsAppProvider) provider).outbox().size();
    }

    private List<MockWhatsAppProvider.Sent> since(int mark) {
        var outbox = ((MockWhatsAppProvider) provider).outbox();
        return List.copyOf(outbox.subList(mark, outbox.size()));
    }

    @Test
    void anOffScriptMessageGetsTheMenuInsteadOfADeadEnd() {
        String waId = someNumber();
        int mark = mark();
        inbound.process(new InboundMessage(waId, waId, "Lost", "do you fix hinges", null, msgId(waId, "x"), Map.of()));

        var ids = since(mark).stream().flatMap(s -> s.buttons().stream())
                .map(WhatsAppProvider.Button::id).toList();
        assertThat(ids).contains("EXPLORE_LENS", "TRACK_ORDER", "VISIT_WEBSITE");

        // a second off-script message right after: no second menu, a person is promised -- once
        int again = mark();
        inbound.process(new InboundMessage(waId, waId, "Lost", "hello??", null, msgId(waId, "x2"), Map.of()));
        inbound.process(new InboundMessage(waId, waId, "Lost", "anyone there", null, msgId(waId, "x3"), Map.of()));
        var mine = since(again).stream().filter(s -> waId.equals(s.toWaId())).toList();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).text()).contains("Someone from our team will reply");
    }

    /** What the Oct 2026 ad leads actually asked, answered in the chat instead of with a menu. */
    @Test
    void typedQuestionsGetTheirAnswer() {
        // no Flyway seed in the mock profile -- the FAQs V50/V62 give prod
        faq("Where are you?", "We're at Radian Retail Park, Great North Road.\n📍 https://maps.app.goo.gl/4Y1xnLdVeKX95Ezh9");
        faq("Offers", "Yes, our 50% off frames sale is still on!");
        faq("Eye check-up", "It's free right now!");
        faq("Lens prices", "Lenses start from K200.");
        String waId = someNumber();
        int mark = mark();
        inbound.process(new InboundMessage(waId, waId, "Cathy", "Where are you located", null, msgId(waId, "q1"), Map.of()));
        inbound.process(new InboundMessage(waId, waId, "Cathy", "Is the promotion still on", null, msgId(waId, "q2"), Map.of()));
        inbound.process(new InboundMessage(waId, waId, "Cathy", "How much is eye test", null, msgId(waId, "q3"), Map.of()));
        inbound.process(new InboundMessage(waId, waId, "Cathy", "How much are your lenses", null, msgId(waId, "q4"), Map.of()));

        var texts = since(mark).stream().filter(s -> waId.equals(s.toWaId())).map(MockWhatsAppProvider.Sent::text).toList();
        assertThat(texts).hasSize(4);
        assertThat(texts.get(0)).contains("Radian Retail Park").contains("maps.app.goo.gl").doesNotContain("72.97");
        assertThat(texts.get(1)).contains("50%");
        assertThat(texts.get(2)).contains("free");
        assertThat(texts.get(3)).contains("K200").contains("photo of your prescription").doesNotContain("/lens?");
    }

    private void faq(String title, String answer) {
        if (faqs.findAll().stream().anyMatch(f -> f.getTitle().equals(title))) return;
        Faq f = new Faq();
        f.setTitle(title);
        f.setQuestion(title + "?");
        f.setAnswer(answer);
        faqs.save(f);
    }

    @Test
    void aPhotoIsHandedToAPersonNotAnsweredWithTheMenu() {
        String waId = someNumber();
        int mark = mark();
        inbound.process(new InboundMessage(waId, waId, "Audrina", "[image]", null, msgId(waId, "p1"), Map.of(), "media-" + waId, null));
        inbound.process(new InboundMessage(waId, waId, "Audrina", "[image]", null, msgId(waId, "p2"), Map.of(), "media2-" + waId, null));

        var mine = since(mark).stream().filter(s -> waId.equals(s.toWaId())).toList();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).text()).contains("Someone from our team will reply");
    }

    @Test
    void typingMenuAlsoOpensIt() {
        String waId = someNumber();
        inbound.process(new InboundMessage(waId, waId, "Asker", "hi", null, msgId(waId, "m0"), Map.of()));

        int mark = mark();
        inbound.process(new InboundMessage(waId, waId, "Asker", "menu", null, msgId(waId, "m1"), Map.of()));

        var ids = since(mark).stream().flatMap(s -> s.buttons().stream())
                .map(WhatsAppProvider.Button::id).toList();
        assertThat(ids).contains("EXPLORE_LENS");
    }

    @Test
    void faqsListTheAdminsQuestionsAndAnswerATappedOne() {
        // The mock profile builds its schema from JPA, not Flyway, so V50's seed isn't here.
        Faq faq = new Faq();
        faq.setTitle("Insurance");
        faq.setQuestion("Do you take insurance?");
        faq.setAnswer("Yes, we are with Prudential, Onelife and ZISC insurances.");
        faqs.save(faq);

        String waId = someNumber();
        inbound.process(new InboundMessage(waId, waId, "Curious", "hi", null, msgId(waId, "f0"), Map.of()));
        assertThat(since(0).stream().filter(s -> waId.equals(s.toWaId())).flatMap(s -> s.buttons().stream())
                .map(WhatsAppProvider.Button::id)).contains("FAQ");

        int mark = mark();
        inbound.process(new InboundMessage(waId, waId, "Curious", null, "FAQ", msgId(waId, "f1"), Map.of()));
        var rows = since(mark).stream().filter(s -> waId.equals(s.toWaId()))
                .flatMap(s -> s.buttons().stream()).toList();
        assertThat(rows).isNotEmpty().allMatch(r -> r.id().startsWith("FAQ:"));
        String insurance = "FAQ:" + faq.getId();
        assertThat(rows).anyMatch(r -> r.id().equals(insurance));

        mark = mark();
        inbound.process(new InboundMessage(waId, waId, "Curious", null, insurance, msgId(waId, "f2"), Map.of()));
        assertThat(since(mark)).anyMatch(s -> waId.equals(s.toWaId()) && s.text().contains("Prudential"));
    }

    @Test
    void typedBuyNStillWorksForSomeoneMidConversation() {
        Product p = new Product();
        p.setSlug("menu-frame-" + System.nanoTime());
        p.setName("Menu Frame");
        p.setPriceMinor(20_000); // "low" tier
        p.setStockQty(5);
        p.setStatus("ACTIVE");
        products.save(p);

        String waId = someNumber();
        inbound.process(new InboundMessage(waId, waId, "Typer", "help me choose", null, msgId(waId, "b1"), Map.of()));
        inbound.process(new InboundMessage(waId, waId, "Typer", null, "BUDGET_LOW", msgId(waId, "b2"), Map.of()));

        int mark = mark();
        inbound.process(new InboundMessage(waId, waId, "Typer", "BUY 1", null, msgId(waId, "b3"), Map.of()));

        assertThat(since(mark)).anyMatch(s -> s.text() != null && s.text().contains("Added"));
    }
}
