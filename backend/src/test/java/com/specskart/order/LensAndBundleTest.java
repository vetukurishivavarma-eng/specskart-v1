package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class LensAndBundleTest {

    @Autowired ProductRepository products;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired OrderRepository orders;

    private Product product(String kind, boolean lensable, int kwacha) {
        Product p = new Product();
        p.setSlug(kind.toLowerCase() + "-" + System.nanoTime());
        p.setName(kind + " item");
        p.setPriceMinor(kwacha * 100L);
        p.setStockQty(20);
        p.setStatus("ACTIVE");
        p.setKind(kind);
        p.setLensable(lensable);
        return products.save(p);
    }

    @Test
    void prescriptionLensAddsPerPairAndCarriesToTheOrder() {
        Product frame = product("FRAME", true, 800);
        var cart = carts.addItem(null, frame.getId(), 2);      // 2 lensable pairs, K1600
        var withLens = carts.setLens(cart.token(), "BLUE_LIGHT", "{\"od\":\"-1.25\"}");

        assertThat(withLens.lensType()).isEqualTo("BLUE_LIGHT");
        assertThat(withLens.lensAddMinor()).isEqualTo(8_000 * 2);   // K80 per pair, 2 pairs
        assertThat(withLens.totalMinor()).isEqualTo(160_000 + 16_000);

        var result = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Rx Buyer", "260970001111", null, "1 Rd", "Lusaka", null, null));
        var order = orders.findByOrderNo(result.orderNo()).orElseThrow();
        assertThat(order.getLensType()).isEqualTo("BLUE_LIGHT");
        assertThat(order.getLensAddMinor()).isEqualTo(16_000);
        assertThat(order.getRxJson()).contains("-1.25");
        assertThat(order.getTotalMinor()).isEqualTo(176_000);
    }

    @Test
    void completeTheLookSuggestsAccessoriesWhenAFrameIsInTheBag() {
        product("ACCESSORY", false, 120);
        Product frame = product("FRAME", false, 700);

        var empty = carts.view((String) null);
        assertThat(empty.suggestions()).isEmpty();

        var cart = carts.addItem(null, frame.getId(), 1);
        assertThat(cart.suggestions()).isNotEmpty();
        assertThat(cart.suggestions().get(0).name()).contains("ACCESSORY");

        // once an accessory is in the bag, stop suggesting
        var acc = products.findAll().stream().filter(Product::isAccessory).findFirst().orElseThrow();
        var after = carts.addItem(cart.token(), acc.getId(), 1);
        assertThat(after.suggestions()).isEmpty();
    }
}
