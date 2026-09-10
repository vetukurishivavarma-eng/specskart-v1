package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.catalog.StoreConfig;
import com.specskart.catalog.StoreConfigRepository;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.lead.LeadStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mock")
class CashOnDeliveryTest {

    @Autowired ProductRepository products;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired OrderQueryService orderQuery;
    @Autowired OrderRepository orders;
    @Autowired LeadRepository leads;
    @Autowired StoreConfigRepository storeConfig;

    private Product frame() {
        Product p = new Product();
        p.setSlug("cod-frame-" + System.nanoTime());
        p.setName("COD Frame");
        p.setPriceMinor(60_000);
        p.setStockQty(5);
        p.setStatus("ACTIVE");
        return products.save(p);
    }

    @Test
    void codOrderConfirmsWithoutPaymentThenSettlesOnCashReceived() {
        var cart = carts.addItem(null, frame().getId(), 1);
        var res = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Chanda M", "260970111222", null, "5 Great East Rd", "Lusaka", null, null, true));

        assertThat(res.checkoutUrl()).isNull();                       // nothing to pay online
        var view = orderQuery.byOrderNo(res.orderNo());
        assertThat(view.status()).isEqualTo("CONFIRMED");
        assertThat(view.paymentMethod()).isEqualTo("COD");
        assertThat(view.cashDueMinor()).isEqualTo(view.totalMinor());
        assertThat(view.paidAt()).isNull();

        // lead exists (created by phone) but is NOT converted yet — no cash collected
        Lead lead = leads.findByWhatsappNumber("260970111222").orElseThrow();
        assertThat(lead.getStatus()).isNotEqualTo(LeadStatus.CONVERTED);

        var id = orders.findByOrderNo(res.orderNo()).orElseThrow().getId();
        checkout.markCashReceived(id);

        var settled = orderQuery.byOrderNo(res.orderNo());
        assertThat(settled.paidAt()).isNotNull();
        assertThat(settled.cashDueMinor()).isZero();
        assertThat(leads.findById(lead.getId()).orElseThrow().getStatus()).isEqualTo(LeadStatus.CONVERTED);
    }

    @Test
    void deliveringACodOrderSettlesItIfCashWasNotMarked() {
        var cart = carts.addItem(null, frame().getId(), 1);
        var res = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Bwalya K", "260970333444", null, "9 Kabwe Rd", "Kitwe", null, null, true));
        var id = orders.findByOrderNo(res.orderNo()).orElseThrow().getId();

        checkout.updateStatus(id, OrderStatus.PACKED, null);
        checkout.updateStatus(id, OrderStatus.SHIPPED, null);
        checkout.updateStatus(id, OrderStatus.DELIVERED, null);

        assertThat(orderQuery.byOrderNo(res.orderNo()).paidAt()).isNotNull();
    }

    @Test
    void firstOrderFreeShippingWaivesDelivery() {
        StoreConfig sc = storeConfig.current();
        long origFee = sc.getShippingFeeMinor();
        boolean origFlag = sc.isFirstOrderFreeShipping();
        try {
            sc.setShippingFeeMinor(3_000);
            sc.setFirstOrderFreeShipping(true);
            storeConfig.save(sc);
            var cart = carts.addItem(null, frame().getId(), 1);
            assertThat(carts.view(cart.token()).shippingMinor()).isZero();

            sc.setFirstOrderFreeShipping(false);
            storeConfig.save(sc);
            var cart2 = carts.addItem(null, frame().getId(), 1);
            assertThat(carts.view(cart2.token()).shippingMinor()).isEqualTo(3_000);
        } finally {
            sc.setShippingFeeMinor(origFee);
            sc.setFirstOrderFreeShipping(origFlag);
            storeConfig.save(sc);
        }
    }
}
