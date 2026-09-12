package com.specskart.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByOrderIdAndProductId(UUID orderId, UUID productId);

    interface RatingAgg {
        UUID getProductId();
        Double getAvgRating();
        Long getCnt();
    }

    @Query("select r.productId as productId, avg(r.rating) as avgRating, count(r) as cnt "
            + "from Review r group by r.productId")
    List<RatingAgg> aggregateAll();
}
