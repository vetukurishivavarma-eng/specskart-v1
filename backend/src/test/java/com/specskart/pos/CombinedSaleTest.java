package com.specskart.pos;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.lens.LensDtos;
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
class CombinedSaleTest {

    @Autowired CombinedSaleService combined;
    @Autowired InventoryService inventory;
    @Autowired StoreRepository stores;
    @Autowired ProductRepository products;
    @Autowired PosSaleRepository posSales;

    private Store store;
    private Product frame;

    private void seed() {
        store = new Store();
        store.setName("Combined Store");
        store.setCode("CB" + (int) (Math.random() * 100000));
        stores.save(store);
        frame = new Product();
        frame.setSlug("combined-frame-" + UUID.randomUUID());
        frame.setName("Combined Frame");
        frame.setPriceMinor(50_000);
        products.save(frame);
        inventory.adjust(store.getId(), frame.getId(), 5, "PURCHASE", "seed", null, null);
    }

    private PosDtos.CreateSale frameHalf(String ref, long discount) {
        return new PosDtos.CreateSale(store.getId(),
                List.of(new PosDtos.SaleItemRequest(frame.getId(), 1, null, discount)),
                List.of(new PosDtos.PaymentRequest("CASH", 50_000 - discount, null)),
                "Both Customer", null, "", ref);
    }

    private LensDtos.WalkInSale lensHalf(String ref, long discount) {
        return new LensDtos.WalkInSale("Both Customer", null, "CLEAR", true, null, null, null, null, null, null,
                "CASH", "Staff A", null, ref, null, discount);
    }

    @Test
    void oneBillRecordsFrameAndLensesWithOneTotal() {
        seed();
        String ref = UUID.randomUUID().toString();
        var bill = combined.create(new CombinedSaleService.CombinedSale(frameHalf(ref + "-f", 5_000), lensHalf(ref + "-l", 1_000)), null, "Cashier");

        assertThat(bill.frame().totalMinor()).isEqualTo(45_000);
        assertThat(bill.lens().priceMinor()).isEqualTo(35_000); // 36,000 Clear BB minus 1,000
        assertThat(bill.totalMinor()).isEqualTo(80_000);
        assertThat(posSales.findById(bill.frame().id()).orElseThrow().getNotes()).startsWith("With lenses LENS-");

        // offline replay of the same bill is a no-op
        var replay = combined.create(new CombinedSaleService.CombinedSale(frameHalf(ref + "-f", 5_000), lensHalf(ref + "-l", 1_000)), null, "Cashier");
        assertThat(replay.frame().id()).isEqualTo(bill.frame().id());
        assertThat(replay.lens().id()).isEqualTo(bill.lens().id());
    }

    @Test
    void aRejectedLensHalfRollsTheFrameSaleBack() {
        seed();
        String ref = UUID.randomUUID().toString();
        assertThatThrownBy(() -> combined.create(
                new CombinedSaleService.CombinedSale(frameHalf(ref + "-f", 0), lensHalf(ref + "-l", 99_999_999)), null, "Cashier"))
                .hasMessageContaining("Discount");
        assertThat(posSales.findByClientReference(ref + "-f")).isEmpty();
    }
}
