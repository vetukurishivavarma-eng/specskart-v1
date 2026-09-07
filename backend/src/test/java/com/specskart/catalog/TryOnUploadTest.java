package com.specskart.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class TryOnUploadTest {

    @Autowired AdminCatalogService admin;
    @Autowired ProductRepository products;
    @Autowired ProductImageFileRepository files;

    /** Half-transparent glasses-ish PNG: left half opaque red, right half fully transparent. */
    private static byte[] transparentPng(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                img.setRGB(x, y, x < w / 2 ? new Color(255, 0, 0, 255).getRGB() : 0x00000000);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private Product product() {
        Product p = new Product();
        p.setSlug("tryon-" + System.nanoTime());
        p.setName("Try-on Frame");
        p.setPriceMinor(80000);
        p.setStatus("ACTIVE");
        return products.save(p);
    }

    @Test
    void uploadKeepsPngAndAlphaAndSurfacesUrl() throws Exception {
        Product p = product();
        var file = new MockMultipartFile("file", "frame.png", "image/png", transparentPng(2000, 800));

        var view = admin.setTryOnImage(p.getId(), file);
        assertThat(view.tryOnImageUrl()).startsWith("/api/public/product-images/");

        Product saved = products.findById(p.getId()).orElseThrow();
        assertThat(saved.getTryOnImageUrl()).isEqualTo(view.tryOnImageUrl());

        var fileId = java.util.UUID.fromString(saved.getTryOnImageUrl().replaceAll(".*/", ""));
        var blob = files.findById(fileId).orElseThrow();
        assertThat(blob.getContentType()).isEqualTo("image/png");

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(blob.getBytes()));
        assertThat(decoded.getColorModel().hasAlpha()).isTrue();
        assertThat(decoded.getWidth()).isLessThanOrEqualTo(900);           // downscaled
        int alphaTopRight = (decoded.getRGB(decoded.getWidth() - 1, 0) >>> 24);
        assertThat(alphaTopRight).isZero();                                // transparent pixel stayed transparent

        // replacing swaps the blob; clearing removes it and the url
        var view2 = admin.setTryOnImage(p.getId(), new MockMultipartFile("file", "f2.png", "image/png", transparentPng(600, 240)));
        assertThat(files.findById(fileId)).isEmpty();
        var lastFileId = java.util.UUID.fromString(view2.tryOnImageUrl().replaceAll(".*/", ""));
        admin.clearTryOnImage(p.getId());
        assertThat(products.findById(p.getId()).orElseThrow().getTryOnImageUrl()).isNull();
        assertThat(files.findById(lastFileId)).isEmpty();
    }

    @Test
    void nonImageIsRejected() {
        Product p = product();
        var bad = new MockMultipartFile("file", "notes.txt", "text/plain", "hi".getBytes());
        assertThatThrownBy(() -> admin.setTryOnImage(p.getId(), bad)).hasMessageContaining("image");
    }
}
