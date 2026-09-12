package com.specskart.order;

import com.specskart.catalog.CatalogService;
import com.specskart.catalog.Product;
import com.specskart.catalog.ProductImageRepository;
import com.specskart.catalog.ProductRepository;
import com.specskart.catalog.ProductView;
import com.specskart.catalog.ProductViewRepository;
import com.specskart.catalog.PromoCode;
import com.specskart.catalog.PromoCodeRepository;
import com.specskart.catalog.StoreConfig;
import com.specskart.catalog.StoreConfigRepository;
import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CartService {

    private static final SecureRandom RNG = new SecureRandom();

    /** How long a frame stays reserved for a shopper once it's in their bag. */
    public static final Duration HOLD = Duration.ofMinutes(15);

    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final PromoCodeRepository promos;
    private final StoreConfigRepository storeConfig;
    private final com.specskart.lead.LeadRepository leads;
    private final com.specskart.config.AppProperties props;

    private final OrderRepository orders;
    private final ProductViewRepository views;
    private final PrescriptionFileRepository prescriptionFiles;

    public CartService(CartRepository carts, CartItemRepository items, ProductRepository products,
                       ProductImageRepository images, PromoCodeRepository promos, StoreConfigRepository storeConfig,
                       com.specskart.lead.LeadRepository leads, com.specskart.config.AppProperties props,
                       OrderRepository orders, ProductViewRepository views,
                       PrescriptionFileRepository prescriptionFiles) {
        this.carts = carts;
        this.items = items;
        this.products = products;
        this.images = images;
        this.promos = promos;
        this.storeConfig = storeConfig;
        this.leads = leads;
        this.props = props;
        this.orders = orders;
        this.views = views;
        this.prescriptionFiles = prescriptionFiles;
    }

    private static final long MAX_PRESCRIPTION_BYTES = 8_000_000;

    /** A photo/PDF of the customer's prescription, so a lens order doesn't need a manual
     *  WhatsApp back-and-forth to collect it. Replaces any previous upload on this cart. */
    @Transactional
    public OrderDtos.CartView uploadPrescription(String token, org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Choose a photo of your prescription first.");
        }
        if (file.getSize() > MAX_PRESCRIPTION_BYTES) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "That file's too big — please upload a photo under 8MB.");
        }
        String ct = file.getContentType();
        if (ct == null || !(ct.startsWith("image/") || ct.equals("application/pdf"))) {
            throw ApiException.badRequest("BAD_FILE_TYPE", "Upload a photo (JPG/PNG) or PDF of your prescription.");
        }
        Cart cart = getOrCreate(token);
        PrescriptionFile f = new PrescriptionFile();
        f.setContentType(ct);
        try {
            f.setBytes(file.getBytes());
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("UPLOAD_FAILED", "Could not read that file — please try again.");
        }
        f = prescriptionFiles.save(f);
        cart.setPrescriptionFileId(f.getId());
        carts.save(cart);
        return view(cart);
    }

    /**
     * Record that a known lead (their cart token is already linked) looked at a product —
     * upserted, so repeat browsing just refreshes the timestamp and re-arms the recovery
     * nudge. Silently a no-op for an anonymous visitor (no cart / no lead on it yet); browse
     * recovery only ever targets leads we can already reach on WhatsApp.
     */
    @Transactional
    public void recordProductView(String token, String slug) {
        if (token == null || slug == null) return;
        Cart cart = carts.findByToken(token).orElse(null);
        if (cart == null || cart.getLeadId() == null) return;
        Product p = products.findBySlug(slug).orElse(null);
        if (p == null) return;
        ProductView v = views.findByLeadIdAndProductId(cart.getLeadId(), p.getId()).orElseGet(ProductView::new);
        v.setLeadId(cart.getLeadId());
        v.setProductId(p.getId());
        v.setViewedAt(Instant.now());
        v.setNotifiedAt(null);
        views.save(v);
    }

    /** No lead (anonymous) or a lead who has never ordered before. */
    private boolean isFirstOrder(java.util.UUID leadId) {
        return leadId == null || orders.findByLeadIdOrderByCreatedAtDesc(leadId).isEmpty();
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
        CartItem item = items.findByCartIdAndProductId(cart.getId(), productId).orElseGet(() -> {
            CartItem ci = new CartItem();
            ci.setCartId(cart.getId());
            ci.setProductId(productId);
            ci.setQty(0);
            return ci;
        });
        int want = Math.min(Math.max(1, qty), p.getStockQty()); // stock_qty is what's still available
        if (want <= 0 || products.reserve(productId, want) == 0) {
            throw ApiException.badRequest("OUT_OF_STOCK", "\"" + p.getName() + "\" just sold out.");
        }
        item.setQty(item.getQty() + want);
        item.setUnitPriceMinor(p.getPriceMinor());
        item.setHeldUntil(Instant.now().plus(HOLD));
        items.save(item);
        touch(cart);
        return view(cart);
    }

    @Transactional
    public OrderDtos.CartView setQty(String token, UUID productId, int qty) {
        Cart cart = getOrCreate(token);
        CartItem item = items.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> ApiException.notFound("NOT_IN_CART", "That frame isn't in your bag."));
        int target = Math.max(0, qty);
        int delta = target - item.getQty();
        if (delta > 0) {
            Product p = products.findById(productId).orElseThrow();
            int grab = Math.min(delta, p.getStockQty());
            if (grab <= 0 || products.reserve(productId, grab) == 0) {
                throw ApiException.badRequest("OUT_OF_STOCK", "No more of \"" + p.getName() + "\" available.");
            }
            item.setQty(item.getQty() + grab);
        } else if (delta < 0) {
            products.release(productId, -delta);
            item.setQty(target);
        }
        if (item.getQty() <= 0) {
            items.delete(item);
        } else {
            item.setHeldUntil(Instant.now().plus(HOLD));
            items.save(item);
        }
        touch(cart);
        return view(cart);
    }

    /** Keep an active shopper's holds from expiring while they browse / sit on the cart page. */
    @Transactional
    public void refreshHolds(Cart cart) {
        if (cart.getOrderedAt() != null) return;
        Instant until = Instant.now().plus(HOLD);
        for (CartItem ci : items.findByCartId(cart.getId())) {
            ci.setHeldUntil(until);
            items.save(ci);
        }
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

    @Transactional
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
        int lensablePairs = 0;
        boolean hasFrame = false, hasAccessory = false;
        Instant earliestHold = null;
        for (CartItem ci : lines) {
            Product p = byId.get(ci.getProductId());
            if (p == null) { items.delete(ci); continue; }
            subtotal += ci.lineTotalMinor();
            if (p.isLensable()) lensablePairs += ci.getQty();
            if (p.isAccessory()) hasAccessory = true; else hasFrame = true;
            if (ci.getHeldUntil() != null && (earliestHold == null || ci.getHeldUntil().isBefore(earliestHold))) {
                earliestHold = ci.getHeldUntil();
            }
            out.add(new OrderDtos.CartLine(p.getId(), p.getSlug(), p.getName(), imageByProduct.get(p.getId()),
                    ci.getQty(), ci.getUnitPriceMinor(), ci.lineTotalMinor(), p.inStock(), p.getStockQty(),
                    ci.getHeldUntil(), p.isLensable()));
        }

        // prescription lenses: a per-pair add-on applied to every lensable frame in the bag
        long lensAdd = cart.getLensType() == null ? 0
                : props.lenses().addFor(cart.getLensType()) * lensablePairs;

        final long sub = subtotal;
        long discount = 0;
        String promoCode = cart.getPromoCode();
        if (promoCode != null) {
            var promo = promos.findByCodeIgnoreCase(promoCode).filter(pc -> pc.usable(sub));
            if (promo.isPresent()) discount = promo.get().discountFor(sub);
            else promoCode = null;
        }
        StoreConfig sc = storeConfig.current();
        long goods = subtotal + lensAdd;
        long shipping = out.isEmpty() ? 0 : sc.shippingFor(goods - discount);
        if (shipping > 0 && sc.isFirstOrderFreeShipping() && isFirstOrder(cart.getLeadId())) {
            shipping = 0;
        }
        long total = Math.max(0, goods - discount) + shipping;
        int points = cart.getLeadId() == null ? 0
                : leads.findById(cart.getLeadId()).map(com.specskart.lead.Lead::getPoints).orElse(0);

        // "complete the look": suggest accessories once there's a frame and none added yet
        List<OrderDtos.Suggestion> suggestions = List.of();
        if (hasFrame && !hasAccessory) {
            suggestions = products.findByStatusAndKindOrderByCreatedAtDesc("ACTIVE", "ACCESSORY").stream()
                    .limit(3)
                    .map(a -> new OrderDtos.Suggestion(a.getId(), a.getSlug(), a.getName(),
                            a.getPriceMinor(), imageByProduct.get(a.getId())))
                    .toList();
        }

        return new OrderDtos.CartView(cart.getToken(), out, promoCode, subtotal, discount, shipping, total,
                sc.getCurrency(), sc.getDeliveryEta(), earliestHold,
                points, props.loyalty().pointValueMinor(),
                cart.getLensType(), lensAdd, suggestions, cart.getPrescriptionFileId() != null);
    }

    /** Set the prescription-lens choice + optional Rx for the lensable frames in this cart. */
    @Transactional
    public OrderDtos.CartView setLens(String token, String lensType, String rxJson) {
        Cart cart = getOrCreate(token);
        cart.setLensType(lensType == null || lensType.isBlank() ? null : lensType.trim().toUpperCase(java.util.Locale.ROOT));
        cart.setRxJson(rxJson);
        carts.save(cart);
        return view(cart);
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
