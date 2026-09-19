package com.specskart;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
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
        inbound.process(new InboundMessage(waId, waId, "Lost", "hi", null, msgId(waId, "g"), Map.of()));

        int mark = mark();
        inbound.process(new InboundMessage(waId, waId, "Lost", "do you fix hinges", null, msgId(waId, "x"), Map.of()));

        var ids = since(mark).stream().flatMap(s -> s.buttons().stream())
                .map(WhatsAppProvider.Button::id).toList();
        assertThat(ids).contains("EXPLORE_LENS", "TRACK_ORDER", "VISIT_WEBSITE");
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
