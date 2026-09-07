package com.specskart.catalog;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Raw bytes of an uploaded product photo. Isolated so product/image listings never load it. */
@Entity
@Table(name = "product_image_files")
@Getter
@Setter
public class ProductImageFile extends BaseEntity {

    @Column(nullable = false)
    private String contentType;

    // length drives the column width; prod is Postgres bytea (see V5 migration), this keeps
    // the H2 dev/test schema (create-drop) from defaulting to BINARY VARYING(255)
    @Column(nullable = false, length = 12_000_000)
    private byte[] bytes;
}
