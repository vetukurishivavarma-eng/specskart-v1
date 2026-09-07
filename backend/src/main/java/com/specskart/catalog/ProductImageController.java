package com.specskart.catalog;

import com.specskart.shared.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/** Serves uploaded product photos straight from the database. Public, immutable, cached hard. */
@RestController
public class ProductImageController {

    private final ProductImageFileRepository files;

    public ProductImageController(ProductImageFileRepository files) {
        this.files = files;
    }

    @GetMapping("/api/public/product-images/{id}")
    public ResponseEntity<byte[]> get(@PathVariable UUID id) {
        ProductImageFile f = files.findById(id)
                .orElseThrow(() -> ApiException.notFound("IMAGE_NOT_FOUND", "No such image."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(f.getBytes());
    }
}
