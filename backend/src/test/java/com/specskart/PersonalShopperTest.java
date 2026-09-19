package com.specskart;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
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

/** WhatsApp "help me choose" personal-shopper flow: budget question -> picks -> BUY n
 *  adds to a lead-linked cart entirely inside the chat, no LLM. */
@SpringBootTest
@ActiveProfiles("mock")
class PersonalShopperTest {

    @Autowired WhatsAppInboundService inbound;
    @Autowired LeadRepository leads;
    @Autowired ProductRepository products;
    @Autowired WhatsAppProvider provider;

    private Product cheapFrame() {
        Product p = new Product();
        p.setSlug("shopper-frame-" + System.nanoTime());
        p.setName("Budget Frame");
        p.setPriceMinor(20_000); // K200 -> "low" tier
        p.setStockQty(5);
        p.setStatus("ACTIVE");
        return products.save(p);
    }

    private int outboxSize() {
        return ((MockWhatsAppProvider) provider).outbox().size();
    }

    /** The outbox is shared across the whole suite — only ever look at what this test sent. */
    private java.util.List<MockWhatsAppProvider.Sent> sentSince(int mark) {
        var outbox = ((MockWhatsAppProvider) provider).outbox();
        return java.util.List.copyOf(outbox.subList(mark, outbox.size()));
    }

    @Test
    void budgetQuestionThenBuyAddsToACartAndRepliesWithALink() {
        cheapFrame();
        String waId = "26097" + (200000 + (int) (Math.random() * 700000));
        // Message ids are deduped globally and the H2 db is shared across the suite, so derive
        // them from this run's waId — a literal "m1" collides with whatever else used one.
        String msg = "shopper:" + waId + ":";
        int start = outboxSize();
        inbound.process(new InboundMessage(waId, waId, "Shopper", "help me choose", null, msg + "1", Map.of()));
        Lead lead = leads.findByWhatsappWaId(waId).orElseThrow();

        assertThat(sentSince(start)).anyMatch(s -> s.text() != null && s.text().contains("budget"));

        int afterBudget = outboxSize();
        inbound.process(new InboundMessage(waId, waId, "Shopper", null, "BUDGET_LOW", msg + "2", Map.of()));
        assertThat(leads.findById(lead.getId()).orElseThrow().getStyleBudget()).isEqualTo("low");
        // The picks arrive as a tappable list, one row per product, priced in the description.
        var picksMsg = sentSince(afterBudget).stream()
                .filter(s -> s.buttons().stream().anyMatch(b -> b.id().startsWith("BUY:")))
                .findFirst().orElseThrow();
        assertThat(picksMsg.buttons()).anyMatch(b -> b.title().equals("Budget Frame"));
        String rowId = picksMsg.buttons().stream()
                .filter(b -> b.title().equals("Budget Frame")).findFirst().orElseThrow().id();

        int afterPicks = outboxSize();
        inbound.process(new InboundMessage(waId, waId, "Shopper", "Budget Frame", rowId, msg + "3", Map.of()));
        var confirm = sentSince(afterPicks).stream()
                .filter(s -> s.text() != null && s.text().contains("Added")).findFirst().orElseThrow();
        assertThat(confirm.text()).contains("Budget Frame").contains("/store?c=");
    }
}
