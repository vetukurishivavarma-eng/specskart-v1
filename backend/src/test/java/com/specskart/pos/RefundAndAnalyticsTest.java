package com.specskart.pos;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class RefundAndAnalyticsTest {

    @Autowired SaleService saleService;
    @Autowired InventoryService inventoryService;
    @Autowired PosAnalyticsService analyticsService;
    @Autowired StoreRepository stores;
    @Autowired ProductRepository products;

    private Store store;
    private Product product;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setName("Refund Store");
        store.setCode("RF" + (int) (Math.random() * 100000));
        stores.save(store);

        product = new Product();
        product.setSlug("refund-frame-" + UUID.randomUUID());
        product.setName("Refund Frame");
        product.setPriceMinor(25_000);
        product.setCostPriceMinor(10_000);
        products.save(product);
        inventoryService.adjust(store.getId(), product.getId(), 10, "PURCHASE", "seed", null, null);
    }

    @Test
    void partialRefundReturnsStockAndPostsANegativeSale() {
        var sale = saleService.createSale(new PosDtos.CreateSale(store.getId(),
                List.of(new PosDtos.SaleItemRequest(product.getId(), 3, null, null)),
                List.of(new PosDtos.PaymentRequest("CASH", 75_000, null)),
                null, null, "", null), null, "Cashier A");
        assertThat(inventoryService.quantityOf(store.getId(), product.getId())).isEqualTo(7);

        var refund = saleService.refund(sale.id(), List.of(new PosDtos.SaleItemRequest(product.getId(), 1, null, null)),
                "CASH", "customer changed their mind", null);

        assertThat(refund.totalMinor()).isEqualTo(-25_000);
        assertThat(inventoryService.quantityOf(store.getId(), product.getId())).isEqualTo(8);
    }

    @Test
    void refundingMoreThanWasSoldIsRefused() {
        var sale = saleService.createSale(new PosDtos.CreateSale(store.getId(),
                List.of(new PosDtos.SaleItemRequest(product.getId(), 1, null, null)),
                List.of(new PosDtos.PaymentRequest("CASH", 25_000, null)),
                null, null, "", null), null, "Cashier A");

        assertThatThrownBy(() -> saleService.refund(sale.id(),
                List.of(new PosDtos.SaleItemRequest(product.getId(), 5, null, null)), "CASH", null, null))
                .hasMessageContaining("Refund quantity");
    }

    @Test
    void topProductsRanksByRevenueProfitAndQuantity() {
        saleService.createSale(new PosDtos.CreateSale(store.getId(),
                List.of(new PosDtos.SaleItemRequest(product.getId(), 4, null, null)),
                List.of(new PosDtos.PaymentRequest("CASH", 100_000, null)),
                null, null, "", null), null, "Cashier A");

        var today = LocalDate.now(java.time.ZoneOffset.UTC);
        var ranked = analyticsService.topProducts(store.getId(), today, today, "revenue", 5);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).productName()).isEqualTo("Refund Frame");
        assertThat(ranked.get(0).revenueMinor()).isEqualTo(100_000);
        assertThat(ranked.get(0).quantity()).isEqualTo(4);
        assertThat(ranked.get(0).profitMinor()).isEqualTo(100_000 - 10_000 * 4);
    }

    @Test
    void bulkImportSetsAbsoluteQuantityAndReportsUnknownSkus() {
        product.setSku("REFUND-SKU-1");
        products.save(product);

        var skipped = inventoryService.bulkSetBySku(store.getId(),
                List.of(new InventoryService.SkuQuantity("REFUND-SKU-1", 25),
                        new InventoryService.SkuQuantity("NO-SUCH-SKU", 5)),
                null);

        assertThat(inventoryService.quantityOf(store.getId(), product.getId())).isEqualTo(25);
        assertThat(skipped).containsExactly("NO-SUCH-SKU");
    }
}
