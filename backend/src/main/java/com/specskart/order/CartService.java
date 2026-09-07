package com.specskart.order;

import com.specskart.catalog.CatalogService;
import com.specskart.catalog.Product;
import com.specskart.catalog.ProductImageRepository;
import com.specskart.catalog.ProductRepository;
import com.specskart.catalog.PromoCode;
import com.specskart.catalog.PromoCodeRepository;
import com.specskart.catalog.StoreConfig;
import com.specskart.catalog.StoreConfigRepository;
import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CartService {

    private static final SecureRandom RNG = new SecureRandom();

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final PromoCodeRepository promos;
    private final StoreConfigRepository storeConfig;

    public CartService(CartRepository carts, CartItemRepository items, ProductRepository products,
                       ProductImageRepository images, PromoCodeRepository promos, StoreConfigRepository storeConfig) {
        this.carts = carts;
        this.items = items;
        this.products = products;
        this.images = images;
        this.promos = promos;
        this.storeConfig = storeConfig;
    }

    @Transactional
    public Cart getOrCreate(String token) {
        if (token != null && !token.isBlank()) {
            var existing = carts.findByToken(token);
            if (existing.isPresent()) return existing.get();
        }
        Cart c = new Cart();
        byte[] b = new byte[24];
        RNG.nextBytes(b);
        c.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(b));
        return carts.save(c);
    }

    /** A fresh cart already tied to a lead — used when the WhatsApp bot sends a shop link. */
    @Transactional
    public Cart startForLead(UUID leadId) {
        Cart c = getOrCreate(null);
        c.setLeadId(leadId);
        return carts.save(c);
    }

    @Transactional
    public void linkLead(Cart cart, UUID leadId) {
        if (leadId != null && cart.getLeadId() == null) {
            cart.setLeadId(leadId);
            carts.save(cart);
        }
    }

    @Transactional
    public OrderDtos.CartView addItem(String token, UUID productId, int qty) {
        Cart cart = getOrCreate(token);
        Product p = products.findById(productId).filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "That frame is no longer available."));
        int q = Math.max(1, qty);
        CartItem item = items.findByCartIdAndProductId(cart.getId(), productId).orElseGet(() -> {
            CartItem ci = new CartItem();
            ci.setCartId(cart.getId());
            ci.setProductId(productId);
            ci.setQty(0);
            return ci;
        });
        item.setQty(Math.min(item.getQty() + q, Math.max(1, p.getStockQty())));
        item.setUnitPriceMinor(p.getPriceMinor());
        items.save(item);
        touch(cart);
        return view(cart);
    }

    @Transactional
    public OrderDtos.CartView setQty(String token, UUID productId, int qty) {
        Cart cart = getOrCreate(token);
        CartItem item = items.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> ApiException.notFound("NOT_IN_CART", "That frame isn't in your bag."));
        if (qty <= 0) {
            items.delete(item);
        } else {
            Product p = products.findById(productId).orElseThrow();
            item.setQty(Math.min(qty, Math.max(1, p.getStockQty())));
            items.save(item);
        }
        touch(cart);
        return view(cart);
    }

    @Transactional
    public OrderDtos.CartView applyPromo(String token, String code) {
        Cart cart = getOrCreate(token);
        if (code == null || code.isBlank()) {
            cart.setPromoCode(null);
        } else {
            PromoCode promo = promos.findByCodeIgnoreCase(code.trim())
                    .orElseThrow(() -> ApiException.badRequest("PROMO_INVALID", "That code isn't valid."));
            long subtotal = subtotal(cart);
            if (!promo.usable(subtotal)) {
                throw ApiException.badRequest("PROMO_NOT_APPLICABLE",
                        promo.getMinSubtotalMinor() > subtotal ? "Add more to use this code." : "That code can't be used.");
            }
            cart.setPromoCode(promo.getCode());
        }
        touch(cart);
        return view(cart);
    }

    @Transactional(readOnly = true)
    public OrderDtos.CartView view(String token) {
        return view(getOrCreate(token));
    }

    OrderDtos.CartView view(Cart cart) {
        List<CartItem> lines = items.findByCartId(cart.getId());
        Map<UUID, Product> byId = products.findAllById(lines.stream().map(CartItem::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getId, x -> x));
        Map<UUID, String> imageByProduct = new java.util.HashMap<>();
        images.findAll().forEach(i -> imageByProduct.putIfAbsent(i.getProductId(), i.getUrl()));

        List<OrderDtos.CartLine> out = new ArrayList<>();
        long subtotal = 0;
        for (CartItem ci : lines) {
            Product p = byId.get(ci.getProductId());
            if (p == null) { items.delete(ci); continue; }
            subtotal += ci.lineTotalMinor();
            out.add(new OrderDtos.CartLine(p.getId(), p.getSlug(), p.getName(), imageByProduct.get(p.getId()),
                    ci.getQty(), ci.getUnitPriceMinor(), ci.lineTotalMinor(), p.inStock(), p.getStockQty()));
        }

        final long sub = subtotal;
        long discount = 0;
        String promoCode = cart.getPromoCode();
        if (promoCode != null) {
            var promo = promos.findByCodeIgnoreCase(promoCode).filter(pc -> pc.usable(sub));
            if (promo.isPresent()) discount = promo.get().discountFor(sub);
            else promoCode = null;
        }
        StoreConfig sc = storeConfig.current();
        long shipping = out.isEmpty() ? 0 : sc.shippingFor(subtotal - discount);
        long total = Math.max(0, subtotal - discount) + shipping;
        return new OrderDtos.CartView(cart.getToken(), out, promoCode, subtotal, discount, shipping, total,
                sc.getCurrency(), sc.getDeliveryEta());
    }

    long subtotal(Cart cart) {
        return items.findByCartId(cart.getId()).stream().mapToLong(CartItem::lineTotalMinor).sum();
    }

    List<CartItem> lines(Cart cart) {
        return items.findByCartId(cart.getId());
    }

    private void touch(Cart cart) {
        cart.setNudgedAt(null);                     // activity resets the abandoned-cart timer
        cart.setUpdatedAt(java.time.Instant.now()); // and the "last active" clock the nudge job reads
        carts.save(cart);
    }
}
