package com.specskart.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class ProductImageUploadTest {

    @Autowired AdminCatalogService admin;
    @Autowired ProductRepository products;
    @Autowired ProductImageRepository images;
    @Autowired ProductImageFileRepository files;

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void uploadStoresResizedJpegAndSurfacesIt() throws Exception {
        Product p = new Product();
        p.setSlug("upload-test-" + System.nanoTime());
        p.setName("Upload Test");
        p.setPriceMinor(50000);
        p.setStatus("ACTIVE");
        products.save(p);

        var file = new MockMultipartFile("file", "frame.png", "image/png", png(3000, 2000));
        var view = admin.addImage(p.getId(), file);

        assertThat(view.images()).hasSize(1);
        var imgRow = images.findByProductIdOrderBySortAsc(p.getId()).get(0);
        assertThat(imgRow.getFileId()).isNotNull();
        assertThat(imgRow.getUrl()).isEqualTo("/api/public/product-images/" + imgRow.getFileId());

        var blob = files.findById(imgRow.getFileId()).orElseThrow();
        assertThat(blob.getContentType()).isEqualTo("image/jpeg");
        var decoded = ImageIO.read(new java.io.ByteArrayInputStream(blob.getBytes()));
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isLessThanOrEqualTo(1400); // downscaled

        // deleting the product cleans up the blob too
        admin.deleteProduct(p.getId());
        assertThat(files.findById(imgRow.getFileId())).isEmpty();
    }

    @Test
    void nonImageIsRejected() {
        Product p = new Product();
        p.setSlug("bad-upload-" + System.nanoTime());
        p.setName("Bad Upload");
        p.setPriceMinor(50000);
        p.setStatus("ACTIVE");
        products.save(p);

        var notAnImage = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> admin.addImage(p.getId(), notAnImage))
                .hasMessageContaining("image");
    }
}
