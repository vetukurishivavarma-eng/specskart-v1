package com.specskart.catalog;

import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminCatalogService {

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final PromoCodeRepository promos;
    private final StoreConfigRepository storeConfig;

    public AdminCatalogService(ProductRepository products, ProductImageRepository images,
                               PromoCodeRepository promos, StoreConfigRepository storeConfig) {
        this.products = products;
        this.images = images;
        this.promos = promos;
        this.storeConfig = storeConfig;
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.AdminProduct> listProducts() {
        return products.findAll(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toAdmin).toList();
    }

    @Transactional(readOnly = true)
    public CatalogDtos.AdminProduct getProduct(UUID id) {
        return toAdmin(products.findById(id)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "No such product.")));
    }

    @Transactional
    public CatalogDtos.AdminProduct createProduct(CatalogDtos.ProductUpsert in) {
        Product p = new Product();
        applyProduct(p, in, true);
        products.save(p);
        replaceImages(p.getId(), in.images());
        return toAdmin(p);
    }

    @Transactional
    public CatalogDtos.AdminProduct updateProduct(UUID id, CatalogDtos.ProductUpsert in) {
        Product p = products.findById(id)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "No such product."));
        applyProduct(p, in, false);
        products.save(p);
        if (in.images() != null) replaceImages(id, in.images());
        return toAdmin(p);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        images.deleteByProductId(id);
        products.deleteById(id);
    }

    private void applyProduct(Product p, CatalogDtos.ProductUpsert in, boolean isNew) {
        if (in.name() == null || in.name().isBlank()) throw ApiException.badRequest("NAME_REQUIRED", "Name is required.");
        p.setName(in.name().trim());
        String desired = (in.slug() == null || in.slug().isBlank() ? in.name() : in.slug())
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (isNew || !desired.equals(p.getSlug())) {
            String slug = desired;
            int n = 2;
            while (products.findBySlug(slug).filter(x -> !java.util.Objects.equals(x.getId(), p.getId())).isPresent()) {
                slug = desired + "-" + n++;
            }
            p.setSlug(slug);
        }
        p.setDescription(in.description());
        p.setFrameCategoryCode(in.frameCategoryCode() == null ? null : in.frameCategoryCode().toUpperCase(Locale.ROOT));
        p.setMaterial(in.material());
        p.setColour(in.colour());
        if (in.gender() != null) p.setGender(in.gender().toUpperCase(Locale.ROOT));
        if (in.priceMinor() != null) p.setPriceMinor(Math.max(0, in.priceMinor()));
        p.setCompareAtMinor(in.compareAtMinor());
        if (in.stockQty() != null) p.setStockQty(Math.max(0, in.stockQty()));
        if (in.lensable() != null) p.setLensable(in.lensable());
        if (in.status() != null) p.setStatus(in.status().toUpperCase(Locale.ROOT));
        if (in.featured() != null) p.setFeatured(in.featured());
    }

    private void replaceImages(UUID productId, List<CatalogDtos.ImageInput> in) {
        images.deleteByProductId(productId);
        if (in == null) return;
        int sort = 0;
        for (CatalogDtos.ImageInput img : in) {
            if (img.url() == null || img.url().isBlank()) continue;
            ProductImage pi = new ProductImage();
            pi.setProductId(productId);
            pi.setUrl(img.url().trim());
            pi.setAlt(img.alt());
            pi.setSort(sort++);
            images.save(pi);
        }
    }

    private CatalogDtos.AdminProduct toAdmin(Product p) {
        var imgs = images.findByProductIdOrderBySortAsc(p.getId()).stream()
                .map(i -> new CatalogDtos.ImageDto(i.getUrl(), i.getAlt())).toList();
        return new CatalogDtos.AdminProduct(p.getId(), p.getSlug(), p.getName(), p.getDescription(),
                p.getFrameCategoryCode(), p.getMaterial(), p.getColour(), p.getGender(),
                p.getPriceMinor(), p.getCompareAtMinor(), p.getCurrency(), p.getStockQty(),
                p.isLensable(), p.getStatus(), p.isFeatured(), imgs);
    }

    // ---- promos ----
    @Transactional(readOnly = true)
    public List<CatalogDtos.AdminPromo> listPromos() {
        return promos.findAllByOrderByCreatedAtDesc().stream().map(this::toAdmin).toList();
    }

    @Transactional
    public CatalogDtos.AdminPromo upsertPromo(UUID id, CatalogDtos.PromoUpsert in) {
        PromoCode p = id == null ? new PromoCode() : promos.findById(id)
                .orElseThrow(() -> ApiException.notFound("PROMO_NOT_FOUND", "No such code."));
        if (id == null) {
            if (in.code() == null || in.code().isBlank()) throw ApiException.badRequest("CODE_REQUIRED", "Code is required.");
            String code = in.code().trim().toUpperCase(Locale.ROOT);
            promos.findByCodeIgnoreCase(code).ifPresent(x -> {
                throw ApiException.badRequest("CODE_EXISTS", "That code already exists.");
            });
            p.setCode(code);
        }
        if (in.discountType() != null) p.setDiscountType(in.discountType().toUpperCase(Locale.ROOT));
        if (in.discountValue() != null) p.setDiscountValue(in.discountValue());
        if (in.minSubtotalMinor() != null) p.setMinSubtotalMinor(Math.max(0, in.minSubtotalMinor()));
        p.setMaxRedemptions(in.maxRedemptions());
        p.setExpiresAt(in.expiresAt());
        if (in.autoIssue() != null) p.setAutoIssue(in.autoIssue());
        if (in.active() != null) p.setActive(in.active());
        promos.save(p);
        return toAdmin(p);
    }

    @Transactional
    public void deletePromo(UUID id) {
        promos.deleteById(id);
    }

    private CatalogDtos.AdminPromo toAdmin(PromoCode p) {
        return new CatalogDtos.AdminPromo(p.getId(), p.getCode(), p.getDiscountType(), p.getDiscountValue(),
                p.getMinSubtotalMinor(), p.getMaxRedemptions(), p.getRedeemedCount(),
                p.getExpiresAt(), p.isAutoIssue(), p.isActive());
    }

    // ---- store config ----
    @Transactional
    public CatalogDtos.StoreConfigDto updateStoreConfig(CatalogDtos.StoreConfigUpsert in) {
        StoreConfig c = storeConfig.current();
        if (in.heroTitle() != null) c.setHeroTitle(in.heroTitle());
        if (in.heroSubtitle() != null) c.setHeroSubtitle(in.heroSubtitle());
        c.setHeroImageUrl(in.heroImageUrl());
        if (in.shippingFeeMinor() != null) c.setShippingFeeMinor(Math.max(0, in.shippingFeeMinor()));
        c.setFreeShippingOverMinor(in.freeShippingOverMinor());
        if (in.deliveryEta() != null) c.setDeliveryEta(in.deliveryEta());
        if (in.currency() != null) c.setCurrency(in.currency().toUpperCase(Locale.ROOT));
        storeConfig.save(c);
        return new CatalogDtos.StoreConfigDto(c.getHeroTitle(), c.getHeroSubtitle(), c.getHeroImageUrl(),
                c.getShippingFeeMinor(), c.getFreeShippingOverMinor(), c.getDeliveryEta(), c.getCurrency());
    }
}
