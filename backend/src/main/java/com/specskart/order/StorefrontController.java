package com.specskart.order;

import com.specskart.framefinder.FrameFinderService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Cart + checkout + order tracking for the public storefront. Cart identity is the X-Cart-Token header. */
@RestController
@RequestMapping("/api/public")
public class StorefrontController {

    private final CartService cart;
    private final CheckoutService checkout;
    private final OrderQueryService orderQuery;
    private final FrameFinderService frameFinder;

    public StorefrontController(CartService cart, CheckoutService checkout, OrderQueryService orderQuery,
                                FrameFinderService frameFinder) {
        this.cart = cart;
        this.checkout = checkout;
        this.orderQuery = orderQuery;
        this.frameFinder = frameFinder;
    }

    @GetMapping("/cart")
    public OrderDtos.CartView getCart(@RequestHeader(value = "X-Cart-Token", required = false) String token,
                                     @RequestParam(value = "s", required = false) String frameFinderToken) {
        var c = cart.getOrCreate(token);
        cart.refreshHolds(c);                       // an active shopper keeps their reservations
        linkLeadFromSession(c.getToken(), frameFinderToken);
        return cart.view(c);
    }

    @PostMapping("/cart/items")
    public OrderDtos.CartView add(@RequestHeader(value = "X-Cart-Token", required = false) String token,
                                  @RequestBody OrderDtos.AddItem body) {
        return cart.addItem(token, body.productId(), body.qty() == null ? 1 : body.qty());
    }

    @PatchMapping("/cart/items/{productId}")
    public OrderDtos.CartView setQty(@RequestHeader(value = "X-Cart-Token", required = false) String token,
                                     @PathVariable UUID productId, @RequestBody OrderDtos.SetQty body) {
        return cart.setQty(token, productId, body.qty());
    }

    @PostMapping("/cart/promo")
    public OrderDtos.CartView promo(@RequestHeader(value = "X-Cart-Token", required = false) String token,
                                    @RequestBody OrderDtos.ApplyPromo body) {
        return cart.applyPromo(token, body.code());
    }

    @PostMapping("/checkout")
    public OrderDtos.CheckoutResult checkout(@RequestHeader(value = "X-Cart-Token", required = false) String token,
                                             @RequestParam(value = "s", required = false) String frameFinderToken,
                                             @RequestBody OrderDtos.CheckoutRequest body) {
        var view = cart.view(token);
        linkLeadFromSession(view.token(), frameFinderToken);
        return checkout.start(view.token(), body);
    }

    @GetMapping("/orders/{orderNo}")
    public OrderDtos.OrderView track(@PathVariable String orderNo) {
        return orderQuery.byOrderNo(orderNo);
    }

    /** The shopper's return from the payment page. Re-verifies with the gateway; safe to call repeatedly. */
    @PostMapping("/orders/{orderNo}/confirm")
    public OrderDtos.OrderView confirm(@PathVariable String orderNo) {
        checkout.confirmPayment(orderNo);
        return orderQuery.byOrderNo(orderNo);
    }

    private void linkLeadFromSession(String cartToken, String frameFinderToken) {
        if (frameFinderToken == null || frameFinderToken.isBlank()) return;
        frameFinder.findLeadIdByToken(frameFinderToken).ifPresent(leadId -> {
            var c = cart.getOrCreate(cartToken);
            cart.linkLead(c, leadId);
        });
    }
}
