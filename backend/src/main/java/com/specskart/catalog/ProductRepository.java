package com.specskart.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Product> findByStatusOrderByCreatedAtDesc(String status);

    List<Product> findByStatusAndFeaturedTrueOrderByCreatedAtDesc(String status);

    List<Product> findByStatusAndFrameCategoryCodeInOrderByStockQtyDesc(String status, List<String> codes);
}
