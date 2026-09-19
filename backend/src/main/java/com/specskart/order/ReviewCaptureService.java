package com.specskart.order;

import com.specskart.catalog.Review;
import com.specskart.catalog.ReviewRepository;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Captures a bare "1"-"5" WhatsApp reply to the post-purchase rating ask, for a frame order
 *  or a lens sale. What is awaiting a rating lives on Lead.providerMetadata — no separate
 *  session store needed. */
@Service
public class ReviewCaptureService {

    private static final String KEY = "pendingReviewOrderId";
    private static final String KEY_LENS = "pendingReviewLensId";

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final ReviewRepository reviews;
    private final LeadRepository leads;

    public ReviewCaptureService(OrderRepository orders, OrderItemRepository items,
                                ReviewRepository reviews, LeadRepository leads) {
        this.orders = orders;
        this.items = items;
        this.reviews = reviews;
        this.leads = leads;
    }

    public UUID pendingOrderId(Lead lead) {
        return uuid(lead, KEY);
    }

    public UUID pendingLensInquiryId(Lead lead) {
        return uuid(lead, KEY_LENS);
    }

    /** Is a bare "1"-"5" from this lead a star rating right now, or just a menu shortcut? */
    public boolean awaitingRating(Lead lead) {
        return pendingOrderId(lead) != null || pendingLensInquiryId(lead) != null;
    }

    private static UUID uuid(Lead lead, String key) {
        Object raw = lead.getProviderMetadata().get(key);
        try {
            return raw == null ? null : UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Transactional
    public void markPending(UUID leadId, UUID orderId) {
        put(leadId, KEY, orderId);
    }

    @Transactional
    public void markPendingLens(UUID leadId, UUID lensInquiryId) {
        put(leadId, KEY_LENS, lensInquiryId);
    }

    private void put(UUID leadId, String key, UUID value) {
        Lead lead = leads.findById(leadId).orElseThrow();
        Map<String, Object> meta = new HashMap<>(lead.getProviderMetadata());
        meta.put(key, value.toString());
        lead.setProviderMetadata(meta);
        leads.save(lead);
    }

    /** One rating applies to every product in the order — a multi-frame order is rare, and
     *  asking per-line would add friction the WhatsApp channel can't cheaply support. */
    @Transactional
    public int recordAndClear(UUID leadId, int rating) {
        Lead lead = leads.findById(leadId).orElseThrow();
        int r = Math.max(1, Math.min(5, rating));
        UUID orderId = pendingOrderId(lead);
        UUID lensId = pendingLensInquiryId(lead);
        int recorded = 0;
        if (lensId != null && !reviews.existsByLensInquiryId(lensId)) {
            Review review = new Review();
            review.setLensInquiryId(lensId);
            review.setLeadId(leadId);
            review.setRating(r);
            reviews.save(review);
            recorded++;
        }
        if (orderId != null && orders.existsById(orderId)) {
            for (OrderItem it : items.findByOrderId(orderId)) {
                if (it.getProductId() == null || reviews.existsByOrderIdAndProductId(orderId, it.getProductId())) continue;
                Review review = new Review();
                review.setOrderId(orderId);
                review.setProductId(it.getProductId());
                review.setLeadId(leadId);
                review.setRating(r);
                reviews.save(review);
                recorded++;
            }
        }
        Map<String, Object> meta = new HashMap<>(lead.getProviderMetadata());
        meta.remove(KEY);
        meta.remove(KEY_LENS);
        lead.setProviderMetadata(meta);
        leads.save(lead);
        return recorded;
    }
}
