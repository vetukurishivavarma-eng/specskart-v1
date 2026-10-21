package com.specskart.catalog;

import com.specskart.pos.InventoryService;
import com.specskart.pos.Store;
import com.specskart.pos.StoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** With the shop pinned, "Stock quantity" on the product page used to be ignored silently. */
@SpringBootTest
@ActiveProfiles("mock")
class ProductPageStockTest {

    @Autowired AdminCatalogService catalog;
    @Autowired InventoryService inventory;
    @Autowired StoreRepository stores;
    @Autowired ProductRepository products;

    private Store shop;

    @AfterEach
    void unpin() { // the in-memory DB is shared: don't leave an online shop behind
        shop.setLatitude(null);
        shop.setLongitude(null);
        stores.save(shop);
    }

    @Test
    void typingAStockNumberSetsTheShopShelf() {
        shop = new Store();
        shop.setName("Pinned test");
        shop.setCode("P" + (int) (Math.random() * 1_000_000));
        shop.setLatitude(-15.4);
        shop.setLongitude(28.3);
        stores.save(shop);

        var made = catalog.createProduct(upsert(4));
        assertThat(inventory.quantityOf(shop.getId(), made.id())).isEqualTo(4);

        var saved = catalog.updateProduct(made.id(), upsert(10));
        assertThat(saved.stockQty()).isEqualTo(10);
        assertThat(inventory.quantityOf(shop.getId(), made.id())).isEqualTo(10);
        assertThat(products.findById(made.id()).orElseThrow().getStockQty()).isEqualTo(10);

        catalog.updateProduct(made.id(), upsert(10)); // unchanged: no extra adjustment
        assertThat(inventory.quantityOf(shop.getId(), made.id())).isEqualTo(10);
    }

    private static CatalogDtos.ProductUpsert upsert(int stock) {
        return new CatalogDtos.ProductUpsert(null, "Ray-Ban test", null, null, null, null, null, 120000L,
                null, stock, null, "ACTIVE", null, null, null, null, null, null, null, null, null, "Ray-Ban");
    }
}
