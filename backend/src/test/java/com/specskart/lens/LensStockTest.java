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

    private UUID webOrder() {
        String phone = "097" + (7000000 + (int) (Math.random() * 900000));
        UUID id = service.start(phone, "PHOTOCHROMATIC", false, null);
        var sent = ((MockWhatsAppProvider) provider).outbox();
        String text = sent.get(sent.size() - 1).text();
        service.verify(text.substring(text.lastIndexOf('/') + 1));
        service.submit(id);
        return id;
    }

    private void collect(UUID id) {
        service.advanceFulfilment(id, new LensDtos.AdvanceFulfilment("READY", null, null, null));
        service.advanceFulfilment(id, new LensDtos.AdvanceFulfilment("DELIVERED", "CASH", "Staff A", null));
    }

    /** Shop pickup: placing an order reserves nothing, collecting it is what moves the shelf. */
    @Test
    void thePairComesOffTheShelfWhenTheCustomerCollectsItNotWhenTheyOrder() {
        inventory.adjust(lusaka.getId(), photo, 3, "PURCHASE", "LB-2", null, null);

        UUID first = webOrder();
        assertThat(inventory.quantityOf(lusaka.getId(), photo)).isEqualTo(3); // untouched
        assertThat(inquiries.findById(first).orElseThrow().getStockStoreId()).isNull();
        assertThat(inquiries.findById(first).orElseThrow().isBackorder()).isFalse();

        collect(first);
        LensInquiry q = inquiries.findById(first).orElseThrow();
        assertThat(inventory.quantityOf(lusaka.getId(), photo)).isEqualTo(2);
        assertThat(q.getStockStoreId()).isEqualTo(lusaka.getId());
        assertThat(q.getStatus()).isEqualTo("SOLD");
        assertThat(LensInquiryService.staffLines(q)).contains("Make at: Lusaka lens test");

        // billing a collected order twice must not take a second pair
        service.completeSale(first, new LensDtos.CompleteSale("CASH", "Staff A", null));
        assertThat(inventory.quantityOf(lusaka.getId(), photo)).isEqualTo(2);
    }

    /** An empty shelf never blocks a web order -- the lab is told to order blanks in. */
    @Test
    void anOrderWithNoStockIsTakenAnywayAndFlaggedAsABackorder() {
        UUID id = webOrder(); // nothing purchased into either shop
        LensInquiry placed = inquiries.findById(id).orElseThrow();
        assertThat(placed.getStatus()).isEqualTo("SUBMITTED");
        assertThat(placed.isBackorder()).isTrue();
        assertThat(LensInquiryService.staffLines(placed)).anyMatch(l -> l.contains("BACKORDER"));

        // cancelling before collection has no shelf to put anything back on
        assertThat(service.cancel(id).status()).isEqualTo("CANCELLED");
        assertThat(inquiries.findById(id).orElseThrow().getStockStoreId()).isNull();
        assertThat(service.pendingWebOrders()).noneSatisfy(v -> assertThat(v.id()).isEqualTo(id));
        assertThatThrownBy(() -> service.submit(id)).hasMessageContaining("cancelled");
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
