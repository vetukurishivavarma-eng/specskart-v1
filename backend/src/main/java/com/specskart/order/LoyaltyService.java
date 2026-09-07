package com.specskart.order;

import com.specskart.config.AppProperties;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.lead.LeadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Loyalty points earn/redeem + referral rewards. Driven by checkout + order status. */
@Service
public class LoyaltyService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyService.class);

    private final LeadRepository leads;
    private final LeadService leadService;
    private final AppProperties props;

    public LoyaltyService(LeadRepository leads, LeadService leadService, AppProperties props) {
        this.leads = leads;
        this.leadService = leadService;
        this.props = props;
    }

    private AppProperties.Loyalty cfg() {
        return props.loyalty();
    }

    public record CheckoutOutcome(long discountMinor, int pointsRedeemed, UUID referrerLeadId, String referralNote) {}

    /**
     * Apply a referral code and/or a points redemption at checkout. Decrements the buyer's
     * points now (restored if the order is cancelled). {@code redeemableBase} is the order
     * value the discount may eat into (subtotal minus any promo discount).
     */
    @Transactional
    public CheckoutOutcome applyAtCheckout(UUID buyerLeadId, long redeemableBase,
                                           Integer redeemPointsReq, String referralCode) {
        Lead buyer = leads.findById(buyerLeadId).orElse(null);
        if (buyer == null) return new CheckoutOutcome(0, 0, null, null);

        long referralDiscount = 0;
        UUID referrerId = null;
        String note = null;
        if (referralCode != null && !referralCode.isBlank()) {
            var referrer = leadService.byReferralCode(referralCode).orElse(null);
            if (referrer != null && !referrer.getId().equals(buyerLeadId)) {
                referralDiscount = Math.round(redeemableBase * (cfg().referralFriendPercent() / 100.0));
                referrerId = referrer.getId();
                note = cfg().referralFriendPercent() + "% referral discount";
            }
        }

        long remaining = Math.max(0, redeemableBase - referralDiscount);
        int redeemed = 0;
        long pointsDiscount = 0;
        int want = redeemPointsReq == null ? 0 : Math.max(0, redeemPointsReq);
        if (want >= cfg().minRedeemPoints() && buyer.getPoints() > 0 && cfg().pointValueMinor() > 0) {
            int affordableByBalance = Math.min(want, buyer.getPoints());
            int affordableByOrder = (int) (remaining / cfg().pointValueMinor());
            redeemed = Math.min(affordableByBalance, affordableByOrder);
            pointsDiscount = (long) redeemed * cfg().pointValueMinor();
            if (redeemed > 0) {
                buyer.setPoints(buyer.getPoints() - redeemed);
                leads.save(buyer);
            }
        }
        return new CheckoutOutcome(referralDiscount + pointsDiscount, redeemed, referrerId, note);
    }

    /** Award earn points, credit the referrer, ensure the buyer has a referral code. */
    @Transactional
    public void onOrderPaid(Order order) {
        if (order.getLeadId() == null) return;
        Lead buyer = leads.findById(order.getLeadId()).orElse(null);
        if (buyer == null) return;

        int earned = (int) Math.round(order.getSubtotalMinor() / 100.0 * cfg().pointsPerKwacha());
        if (earned > 0) {
            buyer.setPoints(buyer.getPoints() + earned);
            order.setPointsEarned(earned);
            leads.save(buyer);
        }
        leadService.ensureReferralCode(buyer.getId());

        if (order.getReferredByLeadId() != null && !order.isReferralCredited()) {
            leadService.addPoints(order.getReferredByLeadId(), cfg().referralRewardPoints());
            order.setReferralCredited(true);
            log.info("referral: lead {} earned {} points from order {}",
                    order.getReferredByLeadId(), cfg().referralRewardPoints(), order.getOrderNo());
        }
    }

    /** Undo everything on cancel/refund. */
    @Transactional
    public void onOrderReversed(Order order) {
        if (order.getLeadId() == null) return;
        if (order.getPointsRedeemed() > 0) leadService.addPoints(order.getLeadId(), order.getPointsRedeemed());
        if (order.getPointsEarned() > 0) leadService.addPoints(order.getLeadId(), -order.getPointsEarned());
        if (order.isReferralCredited() && order.getReferredByLeadId() != null) {
            leadService.addPoints(order.getReferredByLeadId(), -cfg().referralRewardPoints());
            order.setReferralCredited(false);
        }
    }
}
