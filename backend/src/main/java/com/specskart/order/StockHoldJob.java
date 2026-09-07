package com.specskart.order;

import com.specskart.catalog.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Releases lapsed cart holds. A frame reserved when it went into a bag goes back on the
 * shelf 15 min later unless the shopper is still active (each cart view / edit renews the
 * hold) or has checked out (cart.orderedAt set — that reservation is now permanent).
 */
@Component
class StockHoldJob {

    private static final Logger log = LoggerFactory.getLogger(StockHoldJob.class);

    private final CartItemRepository items;
    private final CartRepository carts;
    private final ProductRepository products;

    StockHoldJob(CartItemRepository items, CartRepository carts, ProductRepository products) {
        this.items = items;
        this.carts = carts;
        this.products = products;
    }

    @Scheduled(fixedDelayString = "PT2M", initialDelayString = "PT1M")
    @Transactional
    public void releaseExpired() {
        var expired = items.findByHeldUntilBefore(Instant.now());
        if (expired.isEmpty()) return;

        Map<UUID, Boolean> cartOrdered = new HashMap<>();
        int released = 0;
        for (CartItem ci : expired) {
            boolean ordered = cartOrdered.computeIfAbsent(ci.getCartId(),
                    id -> carts.findById(id).map(c -> c.getOrderedAt() != null).orElse(true));
            if (ordered) { ci.setHeldUntil(null); items.save(ci); continue; } // keep the line, stop re-checking
            products.release(ci.getProductId(), ci.getQty());
            items.delete(ci);
            released += ci.getQty();
        }
        if (released > 0) log.info("released {} unit(s) from {} expired cart hold(s)", released, expired.size());
    }
}
