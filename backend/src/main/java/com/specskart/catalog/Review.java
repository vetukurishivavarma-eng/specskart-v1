package com.specskart.catalog;

import com.specskart.shared.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** A 1-5 star rating on one product from one order — captured over WhatsApp after
 *  delivery, no free-text comment (nothing to moderate). */
@Entity
@Table(name = "reviews")
@Getter
@Setter
public class Review extends BaseEntity {
    private UUID productId;
    private UUID leadId;
    private UUID orderId;
    private int rating;
}
