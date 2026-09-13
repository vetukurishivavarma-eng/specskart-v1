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

@SpringBootTest
@ActiveProfiles("mock")
class DailyReportTest {

    @Autowired SaleService saleService;
    @Autowired InventoryService inventoryService;
    @Autowired DailyReportService reportService;
    @Autowired StoreRepository stores;
    @Autowired ProductRepository products;

    @Test
    void aDayWithTwoSalesAggregatesIntoOneReport() {
        Store store = new Store();
        store.setName("Report Store");
        store.setCode("RP" + (int) (Math.random() * 100000));
        stores.save(store);

        Product product = new Product();
        product.setSlug("report-frame-" + UUID.randomUUID());
        product.setName("Report Frame");
        product.setPriceMinor(30_000);
        products.save(product);
        inventoryService.adjust(store.getId(), product.getId(), 10, "PURCHASE", "seed", null, null);

        saleService.createSale(new PosDtos.CreateSale(store.getId(),
                List.of(new PosDtos.SaleItemRequest(product.getId(), 1, null, null)),
                List.of(new PosDtos.PaymentRequest("CASH", 30_000, null)),
                null, null, "", null), null, "Cashier A");
        saleService.createSale(new PosDtos.CreateSale(store.getId(),
                List.of(new PosDtos.SaleItemRequest(product.getId(), 2, null, null)),
                List.of(new PosDtos.PaymentRequest("CARD", 60_000, null)),
                null, null, "", null), null, "Cashier B");

        var report = reportService.generate(store.getId(), LocalDate.now());

        assertThat(report.saleCount()).isEqualTo(2);
        assertThat(report.grossTotalMinor()).isEqualTo(90_000);
        assertThat(report.cashTotalMinor()).isEqualTo(30_000);
        assertThat(report.cardTotalMinor()).isEqualTo(60_000);
        assertThat(report.topItems()).hasSize(1);
        assertThat(report.topItems().get(0).name()).isEqualTo("Report Frame");
        assertThat(report.topItems().get(0).quantity()).isEqualTo(3);
    }

    @Test
    void voidedSalesAreExcludedFromTheReport() {
        Store store = new Store();
        store.setName("Void Store");
        store.setCode("VD" + (int) (Math.random() * 100000));
        stores.save(store);

        Product product = new Product();
        product.setSlug("void-frame-" + UUID.randomUUID());
        product.setName("Void Frame");
        product.setPriceMinor(20_000);
        products.save(product);
        inventoryService.adjust(store.getId(), product.getId(), 5, "PURCHASE", "seed", null, null);

        var sale = saleService.createSale(new PosDtos.CreateSale(store.getId(),
                List.of(new PosDtos.SaleItemRequest(product.getId(), 1, null, null)),
                List.of(new PosDtos.PaymentRequest("CASH", 20_000, null)),
                null, null, "", null), null, "Cashier A");
        saleService.voidSale(sale.id(), "test void", null);

        var report = reportService.generate(store.getId(), LocalDate.now());
        assertThat(report.saleCount()).isZero();
        assertThat(report.grossTotalMinor()).isZero();
    }
}
