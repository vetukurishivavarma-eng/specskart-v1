package com.specskart.catalog;

import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class StockAlertTest {

    @Autowired CatalogService catalog;
    @Autowired ProductRepository products;
    @Autowired StockAlertRepository alerts;
    @Autowired StockAlertJob job;
    @Autowired WhatsAppProvider whatsapp;

    private Product frame(int stock) {
        Product p = new Product();
        p.setSlug("alert-frame-" + System.nanoTime());
        p.setName("Alert Frame");
        p.setPriceMinor(60000);
        p.setStockQty(stock);
        p.setStatus("ACTIVE");
        return products.save(p);
    }

    @Test
    void firesOnceWhenTheFrameComesBackInStock() {
        Product p = frame(0);
        catalog.registerStockAlert(p.getSlug(), "+260 97 1234567");
        catalog.registerStockAlert(p.getSlug(), "260971234567"); // same number, idempotent
        assertThat(alerts.findByProductIdAndWaId(p.getId(), "260971234567")).isPresent();

        var mock = (MockWhatsAppProvider) whatsapp;
        int before = mock.outbox().size();
        job.run();
        assertThat(mock.outbox().size()).isEqualTo(before); // still out of stock

        p.setStockQty(3);
        products.save(p);
        job.run();

        var sent = mock.outbox().subList(before, mock.outbox().size());
        assertThat(sent).anyMatch(s -> s.text() != null && s.text().contains("Alert Frame"));
        assertThat(alerts.findByProductIdAndWaId(p.getId(), "260971234567").orElseThrow().getNotifiedAt()).isNotNull();

        int after = mock.outbox().size();
        job.run();
        assertThat(mock.outbox().size()).isEqualTo(after); // not sent again
    }
}
