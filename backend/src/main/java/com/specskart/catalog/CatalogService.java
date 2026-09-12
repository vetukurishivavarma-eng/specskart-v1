package com.specskart.catalog;

import com.specskart.recommendation.RecommendationService;
import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CatalogService {

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final PromoCodeRepository promos;
    private final StoreConfigRepository storeConfig;
    private final RecommendationService recommendations;
    private final StockAlertRepository stockAlerts;
    private final ReviewRepository reviews;

    private final com.specskart.config.AppProperties props;

    public CatalogService(ProductRepository products, ProductImageRepository images, PromoCodeRepository promos,
                          StoreConfigRepository storeConfig, RecommendationService recommendations,
                          StockAlertRepository stockAlerts, ReviewRepository reviews,
                          com.specskart.config.AppProperties props) {
        this.products = products;
        this.images = images;
        this.promos = promos;
        this.storeConfig = storeConfig;
        this.recommendations = recommendations;
        this.stockAlerts = stockAlerts;
        this.reviews = reviews;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.ProductCard> browse(String faceShape, String category, String gender, String sort) {
        List<Product> list = products.findByStatusOrderByCreatedAtDesc("ACTIVE");

        List<String> shapeCategories = faceShape == null || faceShape.isBlank() ? null
                : recommendedCategoryCodes(faceShape);

        var stream = list.stream().filter(p -> {
            if (p.isUpcoming()) return false; // scheduled drop — not yet on sale
            if (category != null && !category.isBlank() && !category.equalsIgnoreCase(p.getFrameCategoryCode())) return false;
            if (gender != null && !gender.isBlank() && !gender.equalsIgnoreCase(p.getGender())
                    && !"UNISEX".equalsIgnoreCase(p.getGender())) return false;
            if (shapeCategories != null && (p.getFrameCategoryCode() == null
                    || !shapeCategories.contains(p.getFrameCategoryCode()))) return false;
            return true;
        });

        Comparator<Product> cmp = switch (sort == null ? "" : sort) {
            case "price_asc" -> Comparator.comparingLong(Product::getPriceMinor);
            case "price_desc" -> Comparator.comparingLong(Product::getPriceMinor).reversed();
            default -> Comparator.comparing(Product::isFeatured).reversed()
                    .thenComparing(Product::getCreatedAt, Comparator.reverseOrder());
        };

        Map<java.util.UUID, String> firstImage = firstImageByProduct();
        Map<java.util.UUID, ReviewRepository.RatingAgg> ratings = ratingsByProduct();
        return stream.sorted(cmp)
                .map(p -> card(p, firstImage.get(p.getId()), ratings.get(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.ProductCard> featured() {
        Map<java.util.UUID, String> firstImage = firstImageByProduct();
        Map<java.util.UUID, ReviewRepository.RatingAgg> ratings = ratingsByProduct();
        return products.findByStatusAndFeaturedTrueOrderByCreatedAtDesc("ACTIVE").stream()
                .filter(p -> !p.isUpcoming())
                .map(p -> card(p, firstImage.get(p.getId()), ratings.get(p.getId()))).toList();
    }

    /**
     * "Trending" / best-seller picks for marketing. The admin `featured` flag is the
     * curation signal; topped up with the newest in-stock products if there aren't
     * enough featured ones.
     * ponytail: featured-flag as the best-seller proxy — swap for a real order-count
     * query if the client wants sales-ranked.
     */
    @Transactional(readOnly = true)
    public List<Product> trending(int limit) {
        List<Product> featured = products.findByStatusAndFeaturedTrueOrderByCreatedAtDesc("ACTIVE").stream()
                .filter(Product::inStock).filter(p -> !p.isUpcoming()).limit(limit).toList();
        if (featured.size() >= limit) return featured;
        List<Product> topUp = products.findByStatusOrderByCreatedAtDesc("ACTIVE").stream()
                .filter(Product::inStock).filter(p -> !p.isUpcoming())
                .filter(p -> featured.stream().noneMatch(f -> f.getId().equals(p.getId())))
                .limit(limit - featured.size()).toList();
        return java.util.stream.Stream.concat(featured.stream(), topUp.stream()).toList();
    }

    /** Top in-stock products whose category suits this face shape — used by the WhatsApp bot. */
    @Transactional(readOnly = true)
    public List<Product> forFaceShape(String faceShape, int limit) {
        List<String> codes = recommendedCategoryCodes(faceShape);
        if (codes.isEmpty()) return List.of();
        return products.findByStatusAndFrameCategoryCodeInOrderByStockQtyDesc("ACTIVE", codes).stream()
                .filter(Product::inStock)
                .filter(p -> !p.isUpcoming())
                .limit(limit)
                .toList();
    }

    /**
     * WhatsApp "help me choose" personal shopper: filters by budget tier ("low" &lt; K300,
     * "mid" K300-600, "high" &gt; K600) and, when known, face-shape-suited categories.
     * Relaxes face-shape first, then price, rather than ever returning nothing.
     */
    @Transactional(readOnly = true)
    public List<Product> forBudget(String faceShapeOrNull, String tier, int limit) {
        long lo, hi;
        switch (tier == null ? "" : tier) {
            case "low" -> { lo = 0; hi = 30_000; }
            case "high" -> { lo = 60_000; hi = Long.MAX_VALUE; }
            default -> { lo = 30_000; hi = 60_000; }
        }
        List<String> codes = faceShapeOrNull == null ? List.of() : recommendedCategoryCodes(faceShapeOrNull);
        List<Product> pool = codes.isEmpty() ? products.findByStatusOrderByCreatedAtDesc("ACTIVE")
                : products.findByStatusAndFrameCategoryCodeInOrderByStockQtyDesc("ACTIVE", codes);
        List<Product> inBudget = pool.stream().filter(Product::inStock).filter(p -> !p.isUpcoming())
                .filter(p -> p.getPriceMinor() >= lo && p.getPriceMinor() <= hi)
                .limit(limit).toList();
        if (!inBudget.isEmpty() || codes.isEmpty()) return inBudget;
        // nothing in that price band for the suited categories — relax the face-shape filter
        return products.findByStatusOrderByCreatedAtDesc("ACTIVE").stream()
                .filter(Product::inStock).filter(p -> !p.isUpcoming())
                .filter(p -> p.getPriceMinor() >= lo && p.getPriceMinor() <= hi)
                .limit(limit).toList();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Product> bySlug(String slug) {
        return products.findBySlug(slug).filter(Product::isActive);
    }

    @Transactional(readOnly = true)
    public CatalogDtos.ProductDetail detail(String slug) {
        Product p = products.findBySlug(slug)
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "No such product: " + slug));
        var imgs = images.findByProductIdOrderBySortAsc(p.getId()).stream()
                .map(i -> new CatalogDtos.ImageDto(i.getId(), i.getUrl(), i.getAlt())).toList();
        var agg = ratingsByProduct().get(p.getId());
        return new CatalogDtos.ProductDetail(p.getId(), p.getSlug(), p.getName(), p.getDescription(),
                p.getFrameCategoryCode(), p.getColour(), p.getMaterial(), p.getGender(),
                p.getPriceMinor(), p.getCompareAtMinor(), p.getCurrency(),
                p.getStockQty(), p.inStock(), p.isLensable(), p.isFeatured(), imgs,
                p.getDropsAt(), p.isLimitedEdition(), p.getTryOnImageUrl(),
                agg == null ? null : Math.round(agg.getAvgRating() * 10) / 10.0,
                agg == null ? 0 : agg.getCnt().intValue());
    }

    /** Register a "notify me" request for a product (sold out, or a scheduled drop). Idempotent. */
    @Transactional
    public void registerStockAlert(String slug, String whatsapp) {
        Product p = products.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "No such product: " + slug));
        String waId = whatsapp == null ? "" : whatsapp.replaceAll("\\D", "");
        if (waId.length() < 8) throw ApiException.badRequest("BAD_NUMBER", "Enter a valid WhatsApp number.");
        stockAlerts.findByProductIdAndWaId(p.getId(), waId).orElseGet(() -> {
            StockAlert a = new StockAlert();
            a.setProductId(p.getId());
            a.setWaId(waId);
            return stockAlerts.save(a);
        });
    }

    @Transactional(readOnly = true)
    public CatalogDtos.StoreConfigDto storeConfig() {
        StoreConfig c = storeConfig.current();
        return new CatalogDtos.StoreConfigDto(c.getHeroTitle(), c.getHeroSubtitle(), c.getHeroImageUrl(),
                c.getShippingFeeMinor(), c.getFreeShippingOverMinor(), c.getDeliveryEta(), c.getCurrency(),
                c.isFirstOrderFreeShipping(), props.cod().on(), c.getPaymentNote(), c.getGuaranteeNote());
    }

    /** Validate a promo against a subtotal without redeeming it. */
    @Transactional(readOnly = true)
    public CatalogDtos.PromoPreview previewPromo(String code, long subtotalMinor) {
        PromoCode promo = promos.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> ApiException.badRequest("PROMO_INVALID", "That code isn't valid."));
        boolean ok = promo.usable(subtotalMinor);
        long discount = ok ? promo.discountFor(subtotalMinor) : 0;
        String msg = ok ? "Applied."
                : promo.getMinSubtotalMinor() > subtotalMinor
                    ? "Spend more to use this code."
                    : "This code has expired or been fully used.";
        return new CatalogDtos.PromoPreview(promo.getCode(), promo.getDiscountType(), promo.getDiscountValue(),
                discount, ok, msg);
    }

    List<String> recommendedCategoryCodes(String faceShape) {
        try {
            return recommendations.forFaceShape(faceShape).recommended().stream()
                    .map(f -> f.code().toUpperCase(Locale.ROOT))
                    .collect(Collectors.toList());
        } catch (ApiException e) {
            return new ArrayList<>();
        }
    }

    // ponytail: loads every image row to pick each product's cover. Fine for a boutique
    // catalog; add a `select distinct on (product_id) ... order by sort` query if it grows.
    private Map<java.util.UUID, String> firstImageByProduct() {
        Map<java.util.UUID, String> out = new java.util.HashMap<>();
        images.findAll().stream()
                .sorted(Comparator.comparingInt(ProductImage::getSort))
                .forEach(i -> out.putIfAbsent(i.getProductId(), i.getUrl()));
        return out;
    }

    private Map<java.util.UUID, ReviewRepository.RatingAgg> ratingsByProduct() {
        return reviews.aggregateAll().stream()
                .collect(Collectors.toMap(ReviewRepository.RatingAgg::getProductId, a -> a));
    }

    private CatalogDtos.ProductCard card(Product p, String imageUrl, ReviewRepository.RatingAgg rating) {
        return new CatalogDtos.ProductCard(p.getId(), p.getSlug(), p.getName(), p.getFrameCategoryCode(),
                p.getColour(), p.getMaterial(), p.getGender(),
                p.getPriceMinor(), p.getCompareAtMinor(), p.getCurrency(),
                p.inStock(), p.isFeatured(), imageUrl,
                p.getDropsAt(), p.isLimitedEdition(), p.getTryOnImageUrl(),
                rating == null ? null : Math.round(rating.getAvgRating() * 10) / 10.0,
                rating == null ? 0 : rating.getCnt().intValue(), p.getStockQty());
    }
}
