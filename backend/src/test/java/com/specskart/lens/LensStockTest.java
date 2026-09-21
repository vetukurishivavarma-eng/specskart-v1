package com.specskart.lens;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.pos.InventoryService;
import com.specskart.pos.Store;
import com.specskart.pos.StoreRepository;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lens blanks are counted per shop (V45): each order takes one pair, from the right shop. */
@SpringBootTest
@ActiveProfiles("mock")
class LensStockTest {

    @Autowired LensInquiryService service;
    @Autowired LensInquiryRepository inquiries;
    @Autowired InventoryService inventory;
    @Autowired StoreRepository stores;
    @Autowired ProductRepository products;
    @Autowired WhatsAppProvider provider;

    private Store lusaka, kitwe;
    private UUID photo;

    @BeforeEach
    void setUp() {
        lusaka = shop("Lusaka lens test", "Lusaka", -15.4167, 28.2833);
        kitwe = shop("Kitwe lens test", "Kitwe", -12.8024, 28.2132);
        // V45 seeds this in prod; the mock profile builds its schema without Flyway
        photo = products.findBySkuIgnoreCase("LENS-PHOTOCHROMATIC").orElseGet(() -> {
            Product p = new Product();
            p.setSlug("lens-photochromatic");
            p.setName("Lens blank - Photochromatic (pair)");
            p.setSku("LENS-PHOTOCHROMATIC");
            p.setKind("LENS");
            return products.save(p);
        }).getId();
    }

    // the in-memory DB is shared by every test context: don't leave online shops behind
    @AfterEach
    void unpin() {
        for (Store s : new Store[]{lusaka, kitwe}) {
            s.setLatitude(null);
            s.setLongitude(null);
            stores.save(s);
        }
    }

    private Store shop(String name, String city, Double lat, Double lng) {
        Store s = new Store();
        s.setName(name);
        s.setCode("L" + (int) (Math.random() * 1_000_000));
        s.setCity(city);
        s.setLatitude(lat);
        s.setLongitude(lng);
        return stores.save(s);
    }

    private UUID webOrder(String area) {
        String phone = "097" + (7000000 + (int) (Math.random() * 900000));
        UUID id = service.start(phone, "PHOTOCHROMATIC", false);
        var sent = ((MockWhatsAppProvider) provider).outbox();
        String text = sent.get(sent.size() - 1).text();
        service.verify(text.substring(text.lastIndexOf('/') + 1));
        service.setDelivery(id, new LensDtos.Delivery("Ann", "12 Freedom Way", area, null));
        service.submit(id);
        return id;
    }

    @Test
    void webOrdersTakeAPairFromTheShopInTheirTownThenTheNextOneHoldingIt() {
        inventory.adjust(kitwe.getId(), photo, 1, "PURCHASE", "LB-1", null, null);
        inventory.adjust(lusaka.getId(), photo, 3, "PURCHASE", "LB-2", null, null);

        UUID first = webOrder("Riverside, Kitwe");
        service.submit(first); // a retried submit must not take a second pair
        assertThat(inventory.quantityOf(kitwe.getId(), photo)).isZero();
        LensInquiry q = inquiries.findById(first).orElseThrow();
        assertThat(q.getStockStoreId()).isEqualTo(kitwe.getId());
        assertThat(q.isBackorder()).isFalse();
        assertThat(LensInquiryService.staffLines(q)).contains("Make at: Kitwe lens test");

        UUID second = webOrder("Riverside, Kitwe"); // Kitwe is out now
        assertThat(inquiries.findById(second).orElseThrow().getStockStoreId()).isEqualTo(lusaka.getId());
        assertThat(inventory.quantityOf(lusaka.getId(), photo)).isEqualTo(2);

        // cancelling puts the pair back where it came from and takes it off the staff queue
        assertThat(service.cancel(second).status()).isEqualTo("CANCELLED");
        assertThat(inventory.quantityOf(lusaka.getId(), photo)).isEqualTo(3);
        assertThat(service.pendingWebOrders()).noneSatisfy(v -> assertThat(v.id()).isEqualTo(second));
        assertThatThrownBy(() -> service.submit(second)).hasMessageContaining("cancelled");
    }

    @Test
    void aCounterSaleWithNoStockStillSellsAsABackorder() {
        var sale = service.walkInSale(new LensDtos.WalkInSale("Walk In", null, "PHOTOCHROMATIC", false,
                null, null, "CASH", "Staff A", null, "lens-stock-" + UUID.randomUUID(), kitwe.getId()));

        assertThat(sale.status()).isEqualTo("SOLD");
        LensInquiry q = inquiries.findById(sale.id()).orElseThrow();
        assertThat(q.isBackorder()).isTrue();
        assertThat(q.getShopName()).isEqualTo("Kitwe lens test");
        assertThat(inventory.quantityOf(kitwe.getId(), photo)).isEqualTo(-1); // the POS shows the shortfall
        assertThat(LensInquiryService.staffLines(q)).anyMatch(l -> l.contains("BACKORDER"));
    }
}
