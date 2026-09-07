package com.specskart.catalog;

import com.specskart.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminCatalogService {

    private static final int MAX_DIM = 1400;

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ProductImageFileRepository imageFiles;
    private final PromoCodeRepository promos;
    private final StoreConfigRepository storeConfig;

    public AdminCatalogService(ProductRepository products, ProductImageRepository images,
                               ProductImageFileRepository imageFiles,
                               PromoCodeRepository promos, StoreConfigRepository storeConfig) {
        this.products = products;
        this.images = images;
        this.imageFiles = imageFiles;
        this.promos = promos;
        this.storeConfig = storeConfig;
    }

    // ---- uploaded photos ----

    @Transactional
    public CatalogDtos.AdminProduct addImage(UUID productId, MultipartFile file) {
        Product p = products.findById(productId)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Save the product before adding photos."));
        if (file == null || file.isEmpty()) throw ApiException.badRequest("NO_FILE", "Choose an image to upload.");

        byte[] jpeg = toJpeg(file);
        ProductImageFile blob = new ProductImageFile();
        blob.setContentType("image/jpeg");
        blob.setBytes(jpeg);
        imageFiles.save(blob);

        int nextSort = images.findByProductIdOrderBySortAsc(productId).stream()
                .mapToInt(ProductImage::getSort).max().orElse(-1) + 1;
        ProductImage img = new ProductImage();
        img.setProductId(productId);
        img.setFileId(blob.getId());
        // relative path; the SPA resolves it against the API base (see shop.ts assetUrl)
        img.setUrl("/api/public/product-images/" + blob.getId());
        img.setAlt(p.getName());
        img.setSort(nextSort);
        images.save(img);
        return toAdmin(p);
    }

    @Transactional
    public void deleteImage(UUID imageId) {
        images.findById(imageId).ifPresent(img -> {
            if (img.getFileId() != null) imageFiles.deleteById(img.getFileId());
            images.delete(img);
        });
    }

    /** Downscale to {@value #MAX_DIM}px on the long edge and re-encode as JPEG (flattening any alpha). */
    private byte[] toJpeg(MultipartFile file) {
        BufferedImage src;
        try {
            src = ImageIO.read(file.getInputStream());
        } catch (IOException e) {
            throw ApiException.badRequest("BAD_IMAGE", "Couldn't read that file.");
        }
        if (src == null) throw ApiException.badRequest("BAD_IMAGE", "That doesn't look like an image.");

        int w = src.getWidth(), h = src.getHeight();
        double scale = Math.min(1.0, (double) MAX_DIM / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));

        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, tw, th);
        g.drawImage(src, 0, 0, tw, th, null);
        g.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "jpg", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw ApiException.badRequest("BAD_IMAGE", "Couldn't process that image.");
        }
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
        for (ProductImage img : images.findByProductIdOrderBySortAsc(id)) {
            if (img.getFileId() != null) imageFiles.deleteById(img.getFileId());
        }
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

    /** Replace the external-URL images. Uploaded photos are managed via add/deleteImage, not here. */
    private void replaceImages(UUID productId, List<CatalogDtos.ImageInput> in) {
        if (in == null) return;
        for (ProductImage old : images.findByProductIdOrderBySortAsc(productId)) {
            if (old.getFileId() != null) imageFiles.deleteById(old.getFileId());
        }
        images.deleteByProductId(productId);
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
                .map(i -> new CatalogDtos.ImageDto(i.getId(), i.getUrl(), i.getAlt())).toList();
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
