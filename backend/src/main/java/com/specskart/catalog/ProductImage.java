package com.specskart.catalog;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_images")
@Getter
@Setter
public class ProductImage extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private java.util.UUID productId;

    @Column(nullable = false, length = 1024)
    private String url;

    private String alt;

    @Column(nullable = false)
    private int sort = 0;
}
