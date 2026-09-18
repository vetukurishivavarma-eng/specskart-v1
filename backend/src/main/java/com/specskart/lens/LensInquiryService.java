package com.specskart.lens;

import com.specskart.config.AppProperties;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadService;
import com.specskart.order.OrderNotificationService;
import com.specskart.order.StaffDocController;
import com.specskart.shared.SignedLinks;
import com.specskart.whatsapp.StaffAlerts;
import com.specskart.shared.ApiException;
import com.specskart.shared.PhoneNumbers;
import com.specskart.shared.TokenGenerator;
import com.specskart.payment.PaymentProvider;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The client's lens-only funnel: pick lens type + blue block, verify the WhatsApp number
 * with a magic link, then (once verified) fill the prescription and get a price. The
 * verified number + lens type + blue block are saved the moment verification succeeds —
 * that alone is enough for {@link LensInquiryFollowUpJob} to chase the lead even if the
 * Rx form is never finished.
 */
@Service
public class LensInquiryService {

    private static final Logger log = LoggerFactory.getLogger(LensInquiryService.class);

    /** The doorstep ladder, in order. Staff move an order one rung at a time; the last rung
     *  also bills it, because with cash on delivery that is when the money arrives. */
    /** tx_ref prefix that marks a gateway reference as a lens order, not a frame order. */
    private static final String TX_PREFIX = "LENS-";

    static final List<String> FULFILMENT = List.of("ORDERED", "PACKED", "OUT_FOR_DELIVERY", "DELIVERED");

    private final LensInquiryRepository inquiries;
    private final LeadService leadService;
    private final WhatsAppProvider whatsapp;
    private final TokenGenerator tokens;
    private final AppProperties props;
    private final LensPricing pricing;
    private final StaffAlerts staffAlerts;
    private final SignedLinks links;
    private final PaymentProvider payments;

    public LensInquiryService(LensInquiryRepository inquiries, LeadService leadService,
                              WhatsAppProvider whatsapp, TokenGenerator tokens, AppProperties props,
                              LensPricing pricing, StaffAlerts staffAlerts, SignedLinks links,
                              PaymentProvider payments) {
        this.payments = payments;
        this.staffAlerts = staffAlerts;
        this.links = links;
        this.inquiries = inquiries;
        this.leadService = leadService;
        this.whatsapp = whatsapp;
        this.tokens = tokens;
        this.props = props;
        this.pricing = pricing;
    }

    @Transactional
    public UUID start(String phone, String lensType, boolean blueBlock) {
        String waId = PhoneNumbers.normalize(phone, props.whatsapp().defaultCountryCode());
        if (waId == null || waId.length() < 8) {
            throw ApiException.badRequest("BAD_NUMBER", "Enter a valid WhatsApp number.");
        }
        if (!"CLEAR".equals(lensType) && !"PHOTOCHROMATIC".equals(lensType)) {
            throw ApiException.badRequest("BAD_LENS_TYPE", "Choose a lens type first.");
        }
        LensInquiry q = new LensInquiry();
        q.setPhoneRaw(phone);
        q.setWaId(waId);
        q.setLensType(lensType);
        q.setBlueBlock(blueBlock);
        String raw = tokens.newToken();
        q.setVerifyTokenHash(tokens.hash(raw));
        inquiries.save(q);

        String link = props.frontendBaseUrl() + "/lens/verify/" + raw;
        String text = "Tap to verify your number and continue configuring your lenses:\n" + link;
        if (props.whatsapp().lensVerifyConfigured()) {
            try {
                whatsapp.sendTemplate(waId, props.whatsapp().lensVerifyTemplate(),
                        props.whatsapp().followUpTemplateLang(), List.of(link));
                return q.getId();
            } catch (Exception e) {
                // A template that is missing, paused or in the wrong locale used to drop the link
                // silently. Free-form only reaches someone who messaged us in the last 24h, so this
                // is a partial rescue -- but it beats sending nothing.
                log.warn("lens verify template {} failed for inquiry {}, falling back to text: {}",
                        props.whatsapp().lensVerifyTemplate(), q.getId(), e.getMessage());
            }
        }
        try {
            whatsapp.sendText(waId, text);
        } catch (Exception e) {
            log.warn("lens verification send failed for inquiry {}: {}", q.getId(), e.getMessage());
        }
        return q.getId();
    }

    @Transactional
    public boolean verify(String rawToken) {
        LensInquiry q = inquiries.findByVerifyTokenHash(tokens.hash(rawToken)).orElse(null);
        if (q == null) return false;
        if (!q.isPhoneVerified()) {
            q.setPhoneVerifiedAt(Instant.now());
            q.setStatus("VERIFIED");
            Lead lead = leadService.onWebOrder(q.getWaId(), null);
            if (lead != null) q.setLeadId(lead.getId());
            inquiries.save(q);
            log.info("lens inquiry {} verified", q.getId());
        }
        return true;
    }

    @Transactional(readOnly = true)
    public LensDtos.InquiryView status(UUID id) {
        return view(get(id));
    }

    @Transactional
    public LensDtos.InquiryView update(UUID id, LensDtos.UpdateDetails d) {
        LensInquiry q = requireVerified(get(id));
        if (d.customerName() != null) q.setCustomerName(d.customerName());
        if (d.age() != null) q.setAge(d.age());
        if (d.gender() != null) q.setGender(d.gender());
        if (d.sphRight() != null) q.setSphRight(d.sphRight());
        if (d.sphLeft() != null) q.setSphLeft(d.sphLeft());
        if (d.cylRight() != null) q.setCylRight(d.cylRight());
        if (d.cylLeft() != null) q.setCylLeft(d.cylLeft());
        if (d.axisRight() != null) q.setAxisRight(d.axisRight());
        if (d.axisLeft() != null) q.setAxisLeft(d.axisLeft());
        if (d.addPower() != null) q.setAddPower(d.addPower());
        if (d.lensStructure() != null) q.setLensStructure(d.lensStructure());
        q.setSpecialAxis(computeSpecialAxis(q));
        return view(inquiries.save(q));
    }

    @Transactional
    public LensDtos.InquiryView setDelivery(UUID id, LensDtos.Delivery d) {
        LensInquiry q = requireVerified(get(id));
        if (blank(d.address()) || blank(d.area())) {
            throw ApiException.badRequest("BAD_ADDRESS", "Enter the street address and the area or city.");
        }
        q.setDeliveryName(trim(d.name()));
        q.setDeliveryAddress(trim(d.address()));
        q.setDeliveryArea(trim(d.area()));
        q.setDeliveryLandmark(trim(d.landmark()));
        return view(inquiries.save(q));
    }

    @Transactional
    public LensDtos.InquiryView quote(UUID id) {
        LensInquiry q = requireVerified(get(id));
        q.setPriceMinor(pricing.quote(q));
        q.setStatus("PRICED");
        return view(inquiries.save(q));
    }

    @Transactional
    public LensDtos.InquiryView submit(UUID id) {
        LensInquiry q = requireVerified(get(id));
        // An order with nowhere to go is one the lab can't fulfil -- block it here rather
        // than discover it when someone tries to dispatch.
        if (!q.isWalkIn() && (blank(q.getDeliveryAddress()) || blank(q.getDeliveryArea()))) {
            throw ApiException.badRequest("NO_DELIVERY_ADDRESS",
                    "Add your delivery address before placing the order.");
        }
        if (q.getPriceMinor() == null) q.setPriceMinor(pricing.quote(q));
        q.setStatus("SUBMITTED");
        q.setFulfilment("ORDERED");
        inquiries.save(q);
        alertStaff(q);
        notifyCustomer(q, "we've got your lens order and we're on it");
        return view(q);
    }

    /** Send the customer to the gateway for an order they've already placed. The order stands
     *  whether or not they come back — an abandoned payment is still a lead the shop can chase,
     *  and they can always pay the courier instead. tx_ref is "LENS-<id>": the full id, not the
     *  short human ref, so the webhook can find the row without a lookup table. */
    @Transactional
    public LensDtos.PayResult startPayment(UUID id) {
        LensInquiry q = requireVerified(get(id));
        if (q.isPaid()) throw ApiException.badRequest("ALREADY_PAID", "This order is already paid.");
        if (q.getPriceMinor() == null) q.setPriceMinor(pricing.quote(q));
        var payment = payments.start(new PaymentProvider.PaymentRequest(
                TX_PREFIX + q.getId(), q.getPriceMinor(), q.getCurrency(),
                blank(q.getDeliveryName()) ? q.getCustomerName() : q.getDeliveryName(),
                null, q.getWaId(), props.frontendBaseUrl() + "/lens"));
        q.setPaymentRef(payment.providerRef());
        inquiries.save(q);
        return new LensDtos.PayResult(payment.checkoutUrl(), q.getPriceMinor(), q.getCurrency());
    }

    /** True when this gateway reference belongs to a lens order rather than a frame order. */
    public static boolean isLensRef(String providerRef) {
        return providerRef != null && providerRef.startsWith(TX_PREFIX);
    }

    /** Called from the payment webhook and the return page. Idempotent, and always re-verifies
     *  with the gateway — a callback on its own proves nothing. */
    @Transactional
    public void confirmPayment(String providerRef) {
        UUID id;
        try {
            id = UUID.fromString(providerRef.substring(TX_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            log.warn("lens payment ref {} is not one of ours", providerRef);
            return;
        }
        LensInquiry q = inquiries.findById(id).orElse(null);
        if (q == null || q.getPaidAt() != null) return; // unknown, or already handled
        if (!payments.verify(q.getPaymentRef() == null ? providerRef : q.getPaymentRef())) {
            log.warn("lens payment {} did not verify", providerRef);
            return;
        }
        q.setPaidAt(Instant.now());
        q.setPaymentMethod("ONLINE");
        inquiries.save(q);
        log.info("lens {} paid online", ref(q));
        notifyCustomer(q, "we've received your payment of "
                + OrderNotificationService.money(q.getPriceMinor(), q.getCurrency()));
    }

    /** Specskart POS: staff bills a walk-in customer directly, no WhatsApp verification —
     *  the staff member is standing in front of them. Reuses the LensInquiry pipeline so the
     *  same pricing/special-axis rules and sales reporting apply to every sale, web or counter. */
    @Transactional
    public LensDtos.InquiryView walkInSale(LensDtos.WalkInSale d) {
        // The Specskart POS app's offline queue replays a walk-in sale after a dropped
        // connection using the same clientReference — a retry must be a no-op, not a
        // second sale.
        if (d.clientReference() != null && !d.clientReference().isBlank()) {
            var existing = inquiries.findByClientReference(d.clientReference());
            if (existing.isPresent()) return view(existing.get());
        }
        if (!"CLEAR".equals(d.lensType()) && !"PHOTOCHROMATIC".equals(d.lensType())) {
            throw ApiException.badRequest("BAD_LENS_TYPE", "Choose a lens type first.");
        }
        LensInquiry q = new LensInquiry();
        q.setWalkIn(true);
        q.setCustomerName(d.customerName());
        q.setPhoneRaw(d.phone() == null ? "" : d.phone());
        q.setLensType(d.lensType());
        q.setBlueBlock(Boolean.TRUE.equals(d.blueBlock()));
        q.setAddPower(d.addPower());
        q.setLensStructure(d.lensStructure());
        q.setSpecialAxis(false); // no Rx captured at the counter yet — staff enters that separately if needed
        q.setClientReference(d.clientReference());
        String raw = tokens.newToken();
        q.setVerifyTokenHash(tokens.hash(raw));
        q.setPhoneVerifiedAt(Instant.now());
        q.setStatus("VERIFIED");
        q.setPriceMinor(pricing.quote(q));
        return completeSale(inquiries.save(q).getId(),
                new LensDtos.CompleteSale(d.paymentMethod(), d.soldBy(), d.shopName()));
    }

    /** Specskart POS: finish billing on a web order the customer already verified and
     *  submitted — staff collect payment, mark it SOLD. */
    @Transactional
    public LensDtos.InquiryView completeSale(UUID id, LensDtos.CompleteSale d) {
        LensInquiry q = requireVerified(get(id));
        if (q.getPriceMinor() == null) q.setPriceMinor(pricing.quote(q));
        // Paid online already — don't let a handover overwrite that with "CASH".
        if (q.getPaidAt() == null) q.setPaymentMethod(d.paymentMethod());
        q.setSoldBy(d.soldBy());
        q.setShopName(d.shopName());
        q.setStatus("SOLD");
        // Billing a web order finishes it however staff got here — the doorstep ladder, or
        // "mark as sold" straight off the list when the customer collected in person.
        // Otherwise a sold order would sit on the pending list forever.
        if (!q.isWalkIn()) q.setFulfilment("DELIVERED");
        inquiries.save(q);
        notifyCustomer(q, "your lenses have been delivered — enjoy them!");
        return view(q);
    }

    @Transactional(readOnly = true)
    public List<LensDtos.SaleView> pendingWebOrders() {
        return inquiries.findByFulfilmentNotOrderByCreatedAtAsc("DELIVERED").stream()
                .map(LensInquiryService::saleView).toList();
    }

    /** Staff moving a web order one rung along the doorstep ladder. Strictly one step at a
     *  time and forward only — a mis-tap shouldn't silently skip "out for delivery", and
     *  un-delivering an order is a conversation, not a button. */
    @Transactional
    public LensDtos.InquiryView advanceFulfilment(UUID id, LensDtos.AdvanceFulfilment d) {
        LensInquiry q = requireVerified(get(id));
        int at = FULFILMENT.indexOf(q.getFulfilment() == null ? "ORDERED" : q.getFulfilment());
        int to = FULFILMENT.indexOf(d.stage() == null ? "" : d.stage().toUpperCase(java.util.Locale.ROOT));
        if (to < 0) throw ApiException.badRequest("BAD_STAGE", "Unknown delivery stage.");
        if (to != at + 1) {
            throw ApiException.badRequest("BAD_STAGE_ORDER",
                    "This order is " + label(FULFILMENT.get(Math.max(at, 0))) + " — move it one step at a time.");
        }
        q.setFulfilment(FULFILMENT.get(to));
        inquiries.save(q);

        if ("DELIVERED".equals(q.getFulfilment())) {
            // Handed over is when it's paid for, cash on delivery or not — bill it here so the
            // day's sales report matches what actually left the shop. completeSale notifies.
            return completeSale(id, new LensDtos.CompleteSale(d.paymentMethod(), d.soldBy(), d.shopName()));
        }
        notifyCustomer(q, stageLine(q));
        return view(q);
    }

    /** What the customer is told at each rung. */
    private static String stageLine(LensInquiry q) {
        return switch (q.getFulfilment()) {
            case "PACKED" -> "your lenses are packed and ready to go";
            case "OUT_FOR_DELIVERY" -> blank(q.getDeliveryAddress())
                    ? "your lenses are out for delivery"
                    : "your lenses are out for delivery to " + q.getDeliveryAddress();
            default -> "there's an update on your lens order";
        };
    }

    /** "OUT_FOR_DELIVERY" -> "out for delivery" */
    static String label(String stage) {
        return stage.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    @Transactional(readOnly = true)
    public List<LensDtos.SaleView> salesOn(java.time.LocalDate day) {
        Instant from = day.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant to = from.plus(java.time.Duration.ofDays(1));
        return inquiries.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc("SOLD", from, to).stream()
                .map(LensInquiryService::saleView).toList();
    }

    @Transactional(readOnly = true)
    public LensDtos.DaySummary summaryOn(java.time.LocalDate day) {
        List<LensDtos.SaleView> sales = salesOn(day);
        long total = sales.stream().mapToLong(LensDtos.SaleView::priceMinor).sum();
        String currency = sales.isEmpty() ? "ZMW" : sales.get(0).currency();
        return new LensDtos.DaySummary(total, sales.size(), currency);
    }

    private static LensDtos.SaleView saleView(LensInquiry q) {
        return new LensDtos.SaleView(q.getId(), q.getCustomerName(), q.getLensType(), q.isBlueBlock(),
                q.getLensStructure(), q.isSpecialAxis(), q.getPriceMinor() == null ? 0 : q.getPriceMinor(),
                q.getCurrency(), q.getPaymentMethod(), q.getSoldBy(), q.getShopName(), q.isWalkIn(),
                q.getDeliveryName(), q.getDeliveryAddress(), q.getDeliveryArea(), q.getDeliveryLandmark(),
                q.getFulfilment(), q.isPaid(), q.getCreatedAt());
    }

    /** Keep the customer in the loop at the two moments that matter to them -- the order
     *  landing, and it going out. Reuses the approved order-update template (the only thing
     *  Meta delivers outside the 24h window); silent for a walk-in, who was handed the lens. */
    private void notifyCustomer(LensInquiry q, String line) {
        if (q.isWalkIn() || blank(q.getWaId())) return;
        String who = blank(q.getCustomerName()) ? "there" : q.getCustomerName().split(" ")[0];
        try {
            if (props.whatsapp().orderUpdateConfigured()) {
                whatsapp.sendTemplate(q.getWaId(), props.whatsapp().orderUpdateTemplate(),
                        props.whatsapp().followUpTemplateLang(),
                        List.of(who, line, ref(q), props.frontendBaseUrl() + "/lens"));
            } else {
                whatsapp.sendText(q.getWaId(), "Hi " + who + " — " + line + " (" + ref(q) + ").");
            }
        } catch (Exception e) {
            log.warn("lens {} customer notification failed: {}", ref(q), e.getMessage());
        }
    }

    private void alertStaff(LensInquiry q) {
        if (props.whatsapp().staffNumbers().isEmpty()) return;
        String base = props.whatsapp().absoluteAsset("/api/public/staff-docs/lens/" + q.getId());
        String pdfUrl = base == null ? null : base + ".pdf" + links.query("lens/" + q.getId(), StaffDocController.TTL);
        String price = q.getPriceMinor() == null ? "—" : OrderNotificationService.money(q.getPriceMinor(), q.getCurrency());
        String who = q.getCustomerName() == null || q.getCustomerName().isBlank() ? "+" + q.getWaId() : q.getCustomerName();
        List<String> failures = staffAlerts.send(staffLines(q), pdfUrl, ref(q) + ".pdf",
                List.of(ref(q), price, who, pdfUrl == null ? props.frontendBaseUrl() : pdfUrl), null);
        // No order timeline to hang these on, as there is for web orders -- at least say it out loud.
        for (String failure : failures) log.warn("lens {} staff alert to {}", ref(q), failure);
    }

    /** Everything the lens lab needs — drawn into the staff PDF and used as the WhatsApp caption. */
    public static List<String> staffLines(LensInquiry q) {
        List<String> out = new java.util.ArrayList<>();
        out.add("# 👓 New lens order " + ref(q));
        out.add("Placed: " + OrderNotificationService.STAFF_TIME.format(q.getCreatedAt()));
        out.add("");
        out.add("# Customer");
        out.add("Name: " + dash(q.getCustomerName()));
        out.add("WhatsApp: " + (q.getWaId() == null ? dash(q.getPhoneRaw()) : "+" + q.getWaId())
                + (q.isPhoneVerified() && !q.isWalkIn() ? " (verified)" : ""));
        if (q.getAge() != null || q.getGender() != null) {
            out.add("Age / gender: " + (q.getAge() == null ? "—" : q.getAge()) + " / " + dash(q.getGender()));
        }
        if (!q.isWalkIn()) {
            out.add("");
            out.add("# Deliver to");
            out.add("Name: " + dash(q.getDeliveryName() == null ? q.getCustomerName() : q.getDeliveryName()));
            out.add("Address: " + dash(q.getDeliveryAddress()));
            out.add("Area: " + dash(q.getDeliveryArea()));
            if (q.getDeliveryLandmark() != null && !q.getDeliveryLandmark().isBlank()) {
                out.add("Landmark: " + q.getDeliveryLandmark());
            }
        }
        out.add("");
        out.add("# Lens");
        out.add("Type: " + dash(q.getLensType()) + (q.isBlueBlock() ? " + blue block" : ""));
        out.add("Structure: " + dash(q.getLensStructure()));
        out.add("");
        out.add("# Prescription");
        out.add("` " + String.format("%-5s%-9s%-9s%s", "Eye", "SPH", "CYL", "AXIS"));
        out.add("` " + String.format("%-5s%-9s%-9s%s", "R", power(q.getSphRight()), power(q.getCylRight()), axis(q.getAxisRight())));
        out.add("` " + String.format("%-5s%-9s%-9s%s", "L", power(q.getSphLeft()), power(q.getCylLeft()), axis(q.getAxisLeft())));
        out.add("ADD: " + power(q.getAddPower()));
        if (q.isSpecialAxis()) out.add("⚠ SPECIAL AXIS — not a stock lens, needs a manual check");
        out.add("");
        out.add("Quoted price: " + (q.getPriceMinor() == null ? "—"
                : OrderNotificationService.money(q.getPriceMinor(), q.getCurrency())));
        return out;
    }

    /** Short human reference for a lens order (the id is a UUID). */
    public static String ref(LensInquiry q) {
        return "LENS-" + q.getId().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private static String power(java.math.BigDecimal v) {
        if (v == null) return "-";
        String s = v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        return v.signum() > 0 ? "+" + s : s;
    }

    private static String axis(Integer v) {
        return v == null ? "-" : v + "°";
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return blank(s) ? null : s.trim();
    }

    private static String dash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    /** The client's own rule: axis away from the 90°/180° bands (±10°) needs a special,
     *  non-stock lens. Only meaningful when that eye actually has a cylindrical correction. */
    private static boolean computeSpecialAxis(LensInquiry q) {
        return isSpecial(q.getCylRight(), q.getAxisRight()) || isSpecial(q.getCylLeft(), q.getAxisLeft());
    }

    private static boolean isSpecial(java.math.BigDecimal cyl, Integer axis) {
        if (cyl == null || cyl.signum() == 0 || axis == null) return false;
        int a = ((axis % 180) + 180) % 180;
        boolean nearNinety = Math.abs(a - 90) <= 10;
        boolean nearZeroOrOneEighty = a <= 10 || a >= 170;
        return !(nearNinety || nearZeroOrOneEighty);
    }

    private LensInquiry get(UUID id) {
        return inquiries.findById(id).orElseThrow(() -> ApiException.notFound("INQUIRY_NOT_FOUND", "No such inquiry."));
    }

    private LensInquiry requireVerified(LensInquiry q) {
        if (!q.isPhoneVerified()) {
            throw ApiException.badRequest("NOT_VERIFIED", "Please verify your WhatsApp number first.");
        }
        return q;
    }

    private static LensDtos.InquiryView view(LensInquiry q) {
        return new LensDtos.InquiryView(q.getId(), q.getStatus(), q.isPhoneVerified(),
                q.getLensType(), q.isBlueBlock(),
                q.getCustomerName(), q.getAge(), q.getGender(),
                q.getSphRight(), q.getSphLeft(), q.getCylRight(), q.getCylLeft(),
                q.getAxisRight(), q.getAxisLeft(), q.getAddPower(), q.getLensStructure(),
                q.isSpecialAxis(), q.getPriceMinor(), q.getCurrency(),
                q.getDeliveryName(), q.getDeliveryAddress(), q.getDeliveryArea(), q.getDeliveryLandmark(),
                q.getFulfilment(), q.isPaid());
    }
}
