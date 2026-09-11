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

    @Test
    void budgetQuestionThenBuyAddsToACartAndRepliesWithALink() {
        cheapFrame();
        String waId = "26097" + (200000 + (int) (Math.random() * 700000));
        inbound.process(new InboundMessage(waId, waId, "Shopper", "help me choose", null, "m1", Map.of()));
        Lead lead = leads.findByWhatsappWaId(waId).orElseThrow();

        var outbox = ((MockWhatsAppProvider) provider).outbox();
        assertThat(outbox).anyMatch(s -> s.text() != null && s.text().contains("budget"));

        inbound.process(new InboundMessage(waId, waId, "Shopper", null, "BUDGET_LOW", "m2", Map.of()));
        assertThat(leads.findById(lead.getId()).orElseThrow().getStyleBudget()).isEqualTo("low");
        var picksMsg = ((MockWhatsAppProvider) provider).outbox().stream()
                .filter(s -> s.text() != null && s.text().contains("BUY 1")).findFirst().orElseThrow();
        assertThat(picksMsg.text()).contains("Budget Frame");

        inbound.process(new InboundMessage(waId, waId, "Shopper", "BUY 1", null, "m3", Map.of()));
        var confirm = ((MockWhatsAppProvider) provider).outbox().stream()
                .filter(s -> s.text() != null && s.text().contains("Added")).findFirst().orElseThrow();
        assertThat(confirm.text()).contains("Budget Frame").contains("/store?c=");
    }
}
