package com.specskart.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByOrderIdAndProductId(UUID orderId, UUID productId);

    boolean existsByLensInquiryId(UUID lensInquiryId);

    interface RatingAgg {
        UUID getProductId();
        Double getAvgRating();
        Long getCnt();
    }

    /** Per-product averages. Lens ratings carry no productId and must not form a null group. */
    @Query("select r.productId as productId, avg(r.rating) as avgRating, count(r) as cnt "
            + "from Review r where r.productId is not null group by r.productId")
    List<RatingAgg> aggregateAll();
}
