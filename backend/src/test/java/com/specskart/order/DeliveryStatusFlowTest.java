package com.specskart.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status ladder the POS app's Delivery screen drives: received by the shop, packed, out for
 * delivery, delivered. ACCEPTED is new, so the point of most of this is that adding it did not
 * close the older PAID -> PACKED path that existing orders and the admin web app still use.
 */
class DeliveryStatusFlowTest {

    @Test
    void theFullDeliveryLadderIsWalkable() {
        assertThat(OrderStatus.PAID.canMoveTo(OrderStatus.ACCEPTED)).isTrue();
        assertThat(OrderStatus.ACCEPTED.canMoveTo(OrderStatus.PACKED)).isTrue();
        assertThat(OrderStatus.PACKED.canMoveTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canMoveTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void acceptingIsOptionalSoOlderOrdersStillMove() {
        assertThat(OrderStatus.PAID.canMoveTo(OrderStatus.PACKED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canMoveTo(OrderStatus.PACKED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canMoveTo(OrderStatus.ACCEPTED)).isTrue();
    }

    @Test
    void theLadderStillRefusesToRunBackwardsOrSkipToDelivered() {
        assertThat(OrderStatus.PAID.canMoveTo(OrderStatus.DELIVERED)).isFalse();
        assertThat(OrderStatus.ACCEPTED.canMoveTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.PACKED.canMoveTo(OrderStatus.ACCEPTED)).isFalse();
        assertThat(OrderStatus.DELIVERED.canMoveTo(OrderStatus.PACKED)).isFalse();
    }

    @Test
    void anAcceptedOrderCanStillBeCancelledOrRefunded() {
        assertThat(OrderStatus.ACCEPTED.canMoveTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.ACCEPTED.canMoveTo(OrderStatus.REFUNDED)).isTrue();
    }

    @Test
    void everyDeliveryStepTellsTheCustomerSomething() {
        for (OrderStatus s : new OrderStatus[]{OrderStatus.ACCEPTED, OrderStatus.PACKED,
                OrderStatus.SHIPPED, OrderStatus.DELIVERED}) {
            assertThat(OrderNotificationService.statusLine(s))
                    .as("customer-facing line for %s", s)
                    .isNotBlank();
        }
    }
}
