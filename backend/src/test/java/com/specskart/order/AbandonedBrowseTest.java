package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.catalog.ProductView;
import com.specskart.catalog.ProductViewRepository;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class AbandonedBrowseTest {

    @Autowired ProductRepository products;
    @Autowired LeadRepository leads;
    @Autowired CartService carts;
    @Autowired ProductViewRepository views;
    @Autowired AbandonedBrowseJob job;
    @Autowired WhatsAppProvider provider;

    private Product frame() {
        Product p = new Product();
        p.setSlug("browse-frame-" + System.nanoTime());
        p.setName("Looked-At Frame");
        p.setPriceMinor(45_000);
        p.setStockQty(3);
        p.setStatus("ACTIVE");
        return products.save(p);
    }

    @Test
    void viewedButNotBoughtGetsOneNudgeAfterTheWindowOpens() {
        Lead lead = new Lead();
        lead.setWhatsappWaId("260977" + (100000 + (int) (Math.random() * 800000)));
        lead.setWhatsappNumber(lead.getWhatsappWaId());
        lead = leads.save(lead);

        var cart = carts.startForLead(lead.getId());
        Product p = frame();
        carts.recordProductView(cart.getToken(), p.getSlug());

        ProductView v = views.findByLeadIdAndProductId(lead.getId(), p.getId()).orElseThrow();
        v.setViewedAt(Instant.now().minus(10, ChronoUnit.HOURS)); // inside the 3-96h window
        views.save(v);

        job.nudge();

        var outbox = ((MockWhatsAppProvider) provider).outbox();
        assertThat(outbox).anyMatch(s -> s.text() != null && s.text().contains("Looked-At Frame"));
        assertThat(views.findByLeadIdAndProductId(lead.getId(), p.getId()).orElseThrow().getNotifiedAt()).isNotNull();

        // second pass: already notified, no repeat
        int before = outbox.size();
        job.nudge();
        assertThat(((MockWhatsAppProvider) provider).outbox()).hasSize(before);
    }

    @Test
    void notRecordedWithoutALeadLinkedCart() {
        var cart = carts.getOrCreate(null); // anonymous, no lead
        Product p = frame();
        carts.recordProductView(cart.getToken(), p.getSlug());
        assertThat(views.findAll()).noneMatch(v -> v.getProductId().equals(p.getId()));
    }
}
