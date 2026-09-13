package com.specskart.pos;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class PosSaleFlowTest {

    @Autowired SaleService saleService;
    @Autowired InventoryService inventoryService;
    @Autowired StoreRepository stores;
    @Autowired ProductRepository products;

    private Store store;
    private Product product;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setName("Cairo Road");
        store.setCode("CR" + (int) (Math.random() * 100000));
        stores.save(store);

        product = new Product();
        product.setSlug("test-frame-" + UUID.randomUUID());
        product.setName("Test Frame");
        product.setPriceMinor(25_000);
        products.save(product);

        inventoryService.adjust(store.getId(), product.getId(), 10, "PURCHASE", "seed", "test stock", null);
    }

    @Test
    void sellingDecrementsStockAndRecordsTheReceipt() {
        var sale = saleService.createSale(new PosDtos.CreateSale(
                store.getId(),
                List.of(new PosDtos.SaleItemRequest(product.getId(), 2, null, null)),
                List.of(new PosDtos.PaymentRequest("CASH", 50_000, null)),
                "Walk-in", null, "", null), null, "Cashier A");

        assertThat(sale.totalMinor()).isEqualTo(50_000);
        assertThat(sale.receiptNumber()).startsWith(store.getCode());
        assertThat(inventoryService.quantityOf(store.getId(), product.getId())).isEqualTo(8);
    }

    @Test
    void refusesToOversell() {
        assertThatThrownBy(() -> saleService.createSale(new PosDtos.CreateSale(
                store.getId(),
                List.of(new PosDtos.SaleItemRequest(product.getId(), 999, null, null)),
                List.of(new PosDtos.PaymentRequest("CASH", 999 * 25_000L, null)),
                null, null, "", null), null, "Cashier A"))
                .hasMessageContaining("Not enough stock");
        assertThat(inventoryService.quantityOf(store.getId(), product.getId())).isEqualTo(10);
    }

    @Test
    void replayingTheSameClientReferenceDoesNotDoubleSell() {
        String ref = "device-" + UUID.randomUUID();
        var req = new PosDtos.CreateSale(store.getId(),
                List.of(new PosDtos.SaleItemRequest(product.getId(), 1, null, null)),
                List.of(new PosDtos.PaymentRequest("CASH", 25_000, null)),
                null, null, "", ref);

        var first = saleService.createSale(req, null, "Cashier A");
        var replay = saleService.createSale(req, null, "Cashier A");

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(inventoryService.quantityOf(store.getId(), product.getId())).isEqualTo(9);
    }

    @Test
    void voidingASaleReturnsTheStock() {
        var sale = saleService.createSale(new PosDtos.CreateSale(
                store.getId(),
                List.of(new PosDtos.SaleItemRequest(product.getId(), 3, null, null)),
                List.of(new PosDtos.PaymentRequest("CASH", 75_000, null)),
                null, null, "", null), null, "Cashier A");
        assertThat(inventoryService.quantityOf(store.getId(), product.getId())).isEqualTo(7);

        var voided = saleService.voidSale(sale.id(), "customer changed their mind", null);

        assertThat(voided.status()).isEqualTo("VOIDED");
        assertThat(inventoryService.quantityOf(store.getId(), product.getId())).isEqualTo(10);
    }
}
