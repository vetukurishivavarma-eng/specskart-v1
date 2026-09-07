package com.specskart.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** Atomically take {@code qty} units off the shelf. Returns 1 if there was enough, 0 otherwise. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stockQty = p.stockQty - :qty where p.id = :id and p.stockQty >= :qty")
    int reserve(@Param("id") UUID id, @Param("qty") int qty);

    /** Put units back (hold expired, line removed, order cancelled). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stockQty = p.stockQty + :qty where p.id = :id")
    void release(@Param("id") UUID id, @Param("qty") int qty);


    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Product> findByStatusOrderByCreatedAtDesc(String status);

    List<Product> findByStatusAndFeaturedTrueOrderByCreatedAtDesc(String status);

    List<Product> findByStatusAndFrameCategoryCodeInOrderByStockQtyDesc(String status, List<String> codes);

    List<Product> findByStatusAndKindOrderByCreatedAtDesc(String status, String kind);
}
