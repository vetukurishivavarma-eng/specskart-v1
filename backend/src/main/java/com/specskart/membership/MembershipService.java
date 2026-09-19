package com.specskart.membership;

import com.specskart.config.AppProperties;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.payment.PaymentProvider;
import com.specskart.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * "Specskart Care" — a paid annual membership giving a standing discount on every lens order.
 *
 * <p><b>Turning it off.</b> Flip {@link #ENABLED} to false and rebuild. That one constant gates
 * the menu row, the purchase, and the discount, so a client who doesn't want the feature gets a
 * build with no trace of it in the customer's experience — no env var to forget, nothing an
 * operator can switch on by accident. Note that disabling it also stops honouring memberships
 * already sold, which is the intended behaviour for "we never launched this" and the wrong one
 * for "we are winding it down" — for a wind-down, set {@link #PRICE_MINOR} out of reach instead
 * and let the existing memberships expire.
 */
@Service
public class MembershipService {

    /** The kill switch. One line, no configuration, deliberately compile-time. */
    public static final boolean ENABLED = true;

    /** K150 a year, 15% off every lens order — it pays for itself on the second pair. */
    public static final long PRICE_MINOR = 15_000;
    public static final int DISCOUNT_PERCENT = 15;
    public static final int DURATION_DAYS = 365;
    public static final String NAME = "Specskart Care";

    /** Marks a gateway reference as a membership, the way "LENS-" marks a lens order. */
    private static final String TX_PREFIX = "MEMB-";

    private static final Logger log = LoggerFactory.getLogger(MembershipService.class);

    private final MembershipRepository memberships;
    private final LeadRepository leads;
    private final PaymentProvider payments;
    private final AppProperties props;

    public MembershipService(MembershipRepository memberships, LeadRepository leads,
                             PaymentProvider payments, AppProperties props) {
        this.memberships = memberships;
        this.leads = leads;
        this.payments = payments;
        this.props = props;
    }

    /** The live membership for a lead, or null. Always null while the feature is off. */
    @Transactional(readOnly = true)
    public Membership activeFor(UUID leadId) {
        if (!ENABLED || leadId == null) return null;
        return memberships
                .findFirstByLeadIdAndPaidAtIsNotNullAndExpiresAtAfterOrderByExpiresAtDesc(leadId, Instant.now())
                .orElse(null);
    }

    /**
     * What to take off a lens quote for this lead, as a percentage. Zero for everyone who
     * isn't a paid-up member, and zero for everyone when the feature is off.
     */
    @Transactional(readOnly = true)
    public int discountPercentFor(UUID leadId) {
        Membership m = activeFor(leadId);
        return m == null ? 0 : m.getDiscountPercent();
    }

    /** Apply this lead's member discount to a price. Rounded down, to the customer's benefit. */
    public long applyDiscount(long priceMinor, UUID leadId) {
        int percent = discountPercentFor(leadId);
        return percent <= 0 ? priceMinor : priceMinor - (priceMinor * percent / 100);
    }

    /**
     * Start a membership purchase: a pending row plus a gateway checkout link. The row is only
     * honoured once the webhook confirms payment, so an abandoned checkout costs nothing.
     */
    @Transactional
    public Purchase startPurchase(UUID leadId) {
        if (!ENABLED) throw ApiException.badRequest("MEMBERSHIP_DISABLED", "Memberships aren't available.");
        Membership existing = activeFor(leadId);
        if (existing != null) {
            throw ApiException.badRequest("ALREADY_A_MEMBER", "This number already has " + NAME + ".");
        }
        Lead lead = leads.findById(leadId).orElseThrow(
                () -> ApiException.notFound("LEAD_NOT_FOUND", "No such customer."));

        Membership m = new Membership();
        m.setLeadId(leadId);
        m.setPriceMinor(PRICE_MINOR);
        m.setDiscountPercent(DISCOUNT_PERCENT);
        m = memberships.save(m);

        String waId = lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
        var payment = payments.start(new PaymentProvider.PaymentRequest(
                TX_PREFIX + m.getId(), m.getPriceMinor(), m.getCurrency(),
                lead.getName() == null ? "Specskart customer" : lead.getName(),
                null, waId, props.frontendBaseUrl() + "/lens"));
        m.setPaymentRef(payment.providerRef());
        memberships.save(m);
        return new Purchase(payment.checkoutUrl(), m.getPriceMinor(), m.getCurrency());
    }

    public record Purchase(String checkoutUrl, long priceMinor, String currency) {}

    /** True when this gateway reference belongs to a membership rather than an order. */
    public static boolean isMembershipRef(String providerRef) {
        return providerRef != null && providerRef.startsWith(TX_PREFIX);
    }

    /**
     * Called from the payment webhook. Idempotent, and re-verifies with the gateway — a callback
     * on its own proves nothing, same rule as lens orders.
     */
    @Transactional
    public void confirmPayment(String providerRef) {
        UUID id;
        try {
            id = UUID.fromString(providerRef.substring(TX_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            log.warn("membership payment ref {} is not one of ours", providerRef);
            return;
        }
        Membership m = memberships.findById(id).orElse(null);
        if (m == null || m.getPaidAt() != null) return; // unknown, or already handled
        if (!payments.verify(m.getPaymentRef() == null ? providerRef : m.getPaymentRef())) {
            log.warn("membership payment {} did not verify", providerRef);
            return;
        }
        Instant now = Instant.now();
        m.setPaidAt(now);
        m.setExpiresAt(now.plus(DURATION_DAYS, ChronoUnit.DAYS));
        memberships.save(m);
        log.info("{} activated for lead {} until {}", NAME, m.getLeadId(), m.getExpiresAt());
    }
}
