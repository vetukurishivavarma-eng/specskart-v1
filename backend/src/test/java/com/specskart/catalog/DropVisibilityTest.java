package com.specskart.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class DropVisibilityTest {

    @Autowired CatalogService catalog;
    @Autowired ProductRepository products;

    @Test
    void upcomingDropIsHiddenFromTheStoreUntilItsTime() {
        Product p = new Product();
        p.setSlug("drop-frame-" + System.nanoTime());
        p.setName("Drop Frame");
        p.setPriceMinor(90000);
        p.setStockQty(5);
        p.setStatus("ACTIVE");
        p.setLimitedEdition(true);
        p.setDropsAt(Instant.now().plus(2, ChronoUnit.DAYS));
        products.save(p);

        assertThat(catalog.browse(null, null, null, null))
                .noneMatch(c -> c.slug().equals(p.getSlug()));
        // but the detail page still resolves, with the countdown info
        var detail = catalog.detail(p.getSlug());
        assertThat(detail.dropsAt()).isNotNull();
        assertThat(detail.limitedEdition()).isTrue();

        p.setDropsAt(Instant.now().minus(1, ChronoUnit.HOURS));
        products.save(p);
        assertThat(catalog.browse(null, null, null, null))
                .anyMatch(c -> c.slug().equals(p.getSlug()) && c.limitedEdition());
    }
}
