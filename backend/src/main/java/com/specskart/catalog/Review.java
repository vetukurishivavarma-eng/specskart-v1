package com.specskart.catalog;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** A 1-5 star rating from one lead about one purchase — captured over WhatsApp after
 *  delivery, no free-text comment (nothing to moderate).
 *
 *  <p>Exactly one of (productId + orderId) or lensInquiryId is set: a lens sale has no
 *  catalogue product to point at. Product rating averages skip the lens rows. */
@Entity
@Table(name = "reviews")
@Getter
@Setter
public class Review extends BaseEntity {
    private UUID productId;
    private UUID leadId;
    private UUID orderId;
    /** Set instead of productId/orderId when the rating is about a lens sale. */
    private UUID lensInquiryId;
    private int rating;
}
