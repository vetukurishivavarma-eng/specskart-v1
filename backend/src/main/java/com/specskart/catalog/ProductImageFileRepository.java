package com.specskart.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductImageFileRepository extends JpaRepository<ProductImageFile, UUID> {
}
