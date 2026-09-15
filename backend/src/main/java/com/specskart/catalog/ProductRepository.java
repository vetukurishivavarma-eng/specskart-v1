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

    /** reserve() without clearing the persistence context — for POS paths mid-way through
     *  building other entities. Returns 1 if there was enough, 0 otherwise. */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stockQty = p.stockQty - :qty where p.id = :id and p.stockQty >= :qty")
    int takeStock(@Param("id") UUID id, @Param("qty") int qty);

    /** Signed stock change mirrored from the web-fulfilment shop's shelf, floored at 0. */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stockQty = case when p.stockQty + :delta < 0 then 0 else p.stockQty + :delta end where p.id = :id")
    void shiftStock(@Param("id") UUID id, @Param("delta") int delta);

    /** Units of this product sitting in not-yet-ordered bags under a live hold. */
    @Query("select coalesce(sum(ci.qty), 0) from CartItem ci where ci.productId = :id and ci.heldUntil > :now "
            + "and ci.cartId in (select c.id from Cart c where c.orderedAt is null)")
    long heldInOpenCarts(@Param("id") UUID id, @Param("now") java.time.Instant now);

    Optional<Product> findBySlug(String slug);

    Optional<Product> findBySkuIgnoreCase(String sku);

    boolean existsBySlug(String slug);

    List<Product> findByStatusOrderByCreatedAtDesc(String status);

    List<Product> findByStatusAndFeaturedTrueOrderByCreatedAtDesc(String status);

    List<Product> findByStatusAndFrameCategoryCodeInOrderByStockQtyDesc(String status, List<String> codes);

    List<Product> findByStatusAndKindOrderByCreatedAtDesc(String status, String kind);
}
