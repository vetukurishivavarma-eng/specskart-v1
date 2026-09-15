package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.pos.InventoryService;
import com.specskart.pos.Store;
import com.specskart.pos.StoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Shops with a map pin sell online: their shelves are the website's stock, and each web order
 *  ships from the nearest one holding the frames. */
@SpringBootTest
@ActiveProfiles("mock")
class WebStockSyncTest {

    // Lusaka and Kitwe are ~290 km apart
    static final double LSK_LAT = -15.4167, LSK_LNG = 28.2833, KIT_LAT = -12.8024, KIT_LNG = 28.2132;

    @Autowired InventoryService inventory;
    @Autowired StoreRepository stores;
    @Autowired ProductRepository products;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired OrderNotificationService notifications;

    private Store lusaka, kitwe;
    private Product frame;

    @BeforeEach
    void setUp() {
        lusaka = shop("Lusaka test", "Lusaka", LSK_LAT, LSK_LNG);
        kitwe = shop("Kitwe test", "Kitwe", KIT_LAT, KIT_LNG);
        frame = product("Sync Frame");
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
        s.setCode("T" + (int) (Math.random() * 1_000_000));
        s.setCity(city);
        s.setLatitude(lat);
        s.setLongitude(lng);
        return stores.save(s);
    }

    private Product product(String name) {
        Product p = new Product();
        p.setSlug("web-sync-" + UUID.randomUUID());
        p.setName(name);
        p.setPriceMinor(500_00L);
        p.setStockQty(99); // stale online-only number from before the POS existed
        p.setStatus("ACTIVE");
        return products.save(p);
    }

    private int online(Product p) {
        return products.findById(p.getId()).orElseThrow().getStockQty();
    }

    private int shelf(Store s, Product p) {
        return inventory.quantityOf(s.getId(), p.getId());
    }

    private OrderDtos.CheckoutRequest request(String city, Double lat, Double lng) {
        return new OrderDtos.CheckoutRequest("Grace Banda", "260971234567", null, "5 Great East Rd", city,
                null, null, false, "DOOR", null, lat, lng);
    }

    @Test
    void pinnedShopShelvesAreTheWebsiteStock() {
        inventory.resyncWebStock();
        assertThat(online(frame)).isZero();

        inventory.adjust(lusaka.getId(), frame.getId(), 2, "PURCHASE", "INV-1", null, null);
        inventory.adjust(kitwe.getId(), frame.getId(), 3, "PURCHASE", "INV-2", null, null);
        assertThat(online(frame)).isEqualTo(5);

        inventory.decrementForSale(kitwe.getId(), frame.getId(), 1, "R-1", null);
        assertThat(online(frame)).isEqualTo(4);

        Store backRoom = shop("Unpinned store", "Ndola", null, null);
        inventory.adjust(backRoom.getId(), frame.getId(), 10, "PURCHASE", "INV-3", null, null);
        assertThat(online(frame)).isEqualTo(4); // no pin = not an online shop
    }

    @Test
    void orderShipsFromTheNearestShopAndACancelPutsItBackThere() {
        inventory.adjust(lusaka.getId(), frame.getId(), 2, "PURCHASE", "INV-4", null, null);
        inventory.adjust(kitwe.getId(), frame.getId(), 2, "PURCHASE", "INV-5", null, null);
        inventory.resyncWebStock();

        var cart = carts.addItem(null, frame.getId(), 1);
        var preview = checkout.shipsFrom(cart.token(), KIT_LAT + 0.02, KIT_LNG + 0.02, null);
        assertThat(preview.shopName()).isEqualTo("Kitwe test");
        assertThat(preview.distanceKm()).isLessThan(5.0);

        var result = checkout.start(cart.token(), request("Kitwe", KIT_LAT + 0.02, KIT_LNG + 0.02));
        Order order = orders.findByOrderNo(result.orderNo()).orElseThrow();
        assertThat(order.getFulfilStoreId()).isEqualTo(kitwe.getId());
        assertThat(order.getDeliveryLat()).isEqualTo(KIT_LAT + 0.02);
        assertThat(notifications.staffLines(order))
                .anyMatch(l -> l.equals("🏬 Ships from: Kitwe test"))
                .anyMatch(l -> l.contains("google.com/maps/dir/?api=1&origin=" + KIT_LAT + "," + KIT_LNG + "&destination="));
        assertThat(shelf(kitwe, frame)).isEqualTo(1);
        assertThat(shelf(lusaka, frame)).isEqualTo(2);
        assertThat(online(frame)).isEqualTo(3);

        checkout.updateStatus(order.getId(), OrderStatus.CANCELLED, "test");
        assertThat(shelf(kitwe, frame)).isEqualTo(2);
        assertThat(online(frame)).isEqualTo(4);
    }

    @Test
    void withoutLocationTheCustomersCityWinsAndAnOrderSplitsWhenNoShopHasItAll() {
        Product onlyInLusaka = frame, onlyInKitwe = product("Kitwe-only Frame");
        inventory.adjust(lusaka.getId(), onlyInLusaka.getId(), 1, "PURCHASE", "INV-6", null, null);
        inventory.adjust(kitwe.getId(), onlyInKitwe.getId(), 1, "PURCHASE", "INV-7", null, null);
        inventory.resyncWebStock();

        var cart = carts.addItem(null, onlyInLusaka.getId(), 1);
        carts.addItem(cart.token(), onlyInKitwe.getId(), 1);
        var result = checkout.start(cart.token(), request("Kitwe", null, null));

        Order order = orders.findByOrderNo(result.orderNo()).orElseThrow();
        assertThat(order.getFulfilStoreId()).isEqualTo(kitwe.getId());
        assertThat(orderItems.findByOrderId(order.getId()))
                .extracting(OrderItem::getProductId, OrderItem::getStoreId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(onlyInLusaka.getId(), lusaka.getId()),
                        org.assertj.core.groups.Tuple.tuple(onlyInKitwe.getId(), kitwe.getId()));
        assertThat(shelf(lusaka, onlyInLusaka)).isZero();
        assertThat(shelf(kitwe, onlyInKitwe)).isZero();
    }

    @Test
    void theTillCantSellAUnitSittingInAnOnlineBag() {
        inventory.adjust(lusaka.getId(), frame.getId(), 1, "PURCHASE", "INV-8", null, null);
        inventory.resyncWebStock();
        carts.addItem(null, frame.getId(), 1);
        assertThat(online(frame)).isZero();

        assertThatThrownBy(() -> inventory.decrementForSale(lusaka.getId(), frame.getId(), 1, "R-2", null))
                .hasMessageContaining("reserved for an online order");
        assertThat(shelf(lusaka, frame)).isEqualTo(1);
    }
}
