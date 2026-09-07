package com.specskart.catalog;

import java.util.List;
import java.util.UUID;

public final class CatalogDtos {

    private CatalogDtos() {}

    public record ImageDto(UUID id, String url, String alt) {}

    public record ProductCard(UUID id, String slug, String name, String frameCategoryCode,
                              String colour, String material, String gender,
                              long priceMinor, Long compareAtMinor, String currency,
                              boolean inStock, boolean featured, String imageUrl) {}

    public record ProductDetail(UUID id, String slug, String name, String description,
                                String frameCategoryCode, String colour, String material, String gender,
                                long priceMinor, Long compareAtMinor, String currency,
                                int stockQty, boolean lensable, boolean featured,
                                List<ImageDto> images) {}

    public record StoreConfigDto(String heroTitle, String heroSubtitle, String heroImageUrl,
                                 long shippingFeeMinor, Long freeShippingOverMinor,
                                 String deliveryEta, String currency) {}

    public record PromoPreview(String code, String discountType, int discountValue,
                               long discountMinor, boolean applicable, String message) {}

    // ---- admin ----
    public record AdminProduct(UUID id, String slug, String name, String description,
                               String frameCategoryCode, String material, String colour, String gender,
                               long priceMinor, Long compareAtMinor, String currency, int stockQty,
                               boolean lensable, String status, boolean featured, List<ImageDto> images) {}

    public record ProductUpsert(String slug, String name, String description, String frameCategoryCode,
                                String material, String colour, String gender, Long priceMinor,
                                Long compareAtMinor, Integer stockQty, Boolean lensable, String status,
                                Boolean featured, List<ImageInput> images) {}

    public record ImageInput(String url, String alt) {}

    public record AdminPromo(UUID id, String code, String discountType, int discountValue,
                             long minSubtotalMinor, Integer maxRedemptions, int redeemedCount,
                             java.time.Instant expiresAt, boolean autoIssue, boolean active) {}

    public record PromoUpsert(String code, String discountType, Integer discountValue, Long minSubtotalMinor,
                              Integer maxRedemptions, java.time.Instant expiresAt, Boolean autoIssue, Boolean active) {}

    public record StoreConfigUpsert(String heroTitle, String heroSubtitle, String heroImageUrl,
                                    Long shippingFeeMinor, Long freeShippingOverMinor,
                                    String deliveryEta, String currency) {}
}
