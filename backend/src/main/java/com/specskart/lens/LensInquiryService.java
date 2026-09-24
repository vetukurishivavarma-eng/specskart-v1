package com.specskart.lens;

import com.specskart.ads.AdEventService;
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
import com.specskart.membership.MembershipService;
import com.specskart.shared.TrackOption;
import com.specskart.shared.TrackUpdate;
import com.specskart.payment.PaymentProvider;
import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.pos.InventoryService;
import com.specskart.pos.Store;
import com.specskart.pos.StoreRepository;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** tx_ref prefix that marks a gateway reference as a lens order, not a frame order. */
    private static final String TX_PREFIX = "LENS-";

    /** The collection ladder, in order. Staff move an order one rung at a time; the last rung
     *  also bills it, because the customer pays when they collect. Nothing is delivered -- the
     *  shop is the pickup point -- but the last rung keeps the name DELIVERED so every existing
     *  row, query and report reads the same. */
    static final List<String> FULFILMENT = List.of("ORDERED", "READY", "DELIVERED");

    private final LensInquiryRepository inquiries;
    private final LeadService leadService;
    private final WhatsAppProvider whatsapp;
    private final TokenGenerator tokens;
    private final AppProperties props;
    private final LensPricing pricing;
    private final StaffAlerts staffAlerts;
    private final SignedLinks links;
    private final PaymentProvider payments;
    private final MembershipService memberships;
    private final InventoryService inventory;
    private final ProductRepository products;
    private final StoreRepository stores;
    private final AdEventService adEvents;

    public LensInquiryService(LensInquiryRepository inquiries, LeadService leadService,
                              WhatsAppProvider whatsapp, TokenGenerator tokens, AppProperties props,
                              LensPricing pricing, StaffAlerts staffAlerts, SignedLinks links,
                              PaymentProvider payments, MembershipService memberships,
                              InventoryService inventory, ProductRepository products, StoreRepository stores,
                              AdEventService adEvents) {
        this.adEvents = adEvents;
        this.inventory = inventory;
        this.products = products;
        this.stores = stores;
        this.memberships = memberships;
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
    public UUID start(String phone, String lensType, boolean blueBlock,
                      Map<String, Object> attribution) {
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
        // Parked on the inquiry rather than used now: for a new number the lead is only created
        // once they verify, which is a separate request from WhatsApp's in-app browser, where
        // this page's storage no longer exists.
        if (attribution != null) q.setAttribution(new java.util.HashMap<>(attribution));

        // A number that has already proved it owns this WhatsApp account does not prove it
        // again. Sending a returning customer away to find a message and come back is the
        // biggest drop-off in this funnel, and we already know who they are.
        //
        // Worth being clear about what this trades away: verification stopped someone typing
        // a number they do not own. A number that verified once is now trusted on sight. The
        // exposure is narrow -- every update about the order still goes only to that WhatsApp
        // number, so the real owner sees anything placed in their name -- but it is a real
        // relaxation, made deliberately.
        // The token is minted either way: verify_token_hash is NOT NULL, and a row that skips
        // verification still needs one. It simply never gets sent anywhere.
        String raw = tokens.newToken();
        q.setVerifyTokenHash(tokens.hash(raw));

        if (inquiries.existsByWaIdAndPhoneVerifiedAtIsNotNull(waId)) {
            q.setPhoneVerifiedAt(Instant.now());
            q.setStatus("VERIFIED");
            Lead lead = leadService.onWebOrder(waId, null, q.getAttribution());
            if (lead != null) q.setLeadId(lead.getId());
            inquiries.save(q);
            adEvents.lead(q);
            log.info("lens inquiry {} skipped verification -- {} is already known", q.getId(), waId);
            return q.getId();
        }

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
            Lead lead = leadService.onWebOrder(q.getWaId(), null, q.getAttribution());
            if (lead != null) q.setLeadId(lead.getId());
            inquiries.save(q);
            adEvents.lead(q);
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
    public LensDtos.InquiryView quote(UUID id) {
        LensInquiry q = requireVerified(get(id));
        q.setPriceMinor(pricing.quote(q));
        q.setStatus("PRICED");
        return view(inquiries.save(q));
    }

    @Transactional
    public LensDtos.InquiryView submit(UUID id) {
        LensInquiry q = requireVerified(get(id));
        if ("CANCELLED".equals(q.getStatus())) {
            throw ApiException.badRequest("CANCELLED", "This order was cancelled — start a new one.");
        }
        if (q.getPriceMinor() == null) q.setPriceMinor(pricing.quote(q));
        q.setStatus("SUBMITTED");
        q.setFulfilment("ORDERED");
        // Placing an order never blocks on stock and never moves it -- the shelf is only touched
        // when the customer collects. The lab still needs to know now whether it has to order
        // blanks in, so flag the shortfall without deducting.
        Product pair = lensBlank(q);
        UUID shop = pair == null ? null : lensShopId(null, pair);
        if (shop != null) q.setBackorder(inventory.quantityOf(shop, pair.getId()) < 1);
        inquiries.save(q);
        adEvents.purchase(q);
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
                q.getCustomerName(),
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
        inquiries.save(q);
        if (d.storeId() != null) takeLensStock(q, d.storeId());
        return completeSale(inquiries.save(q).getId(),
                new LensDtos.CompleteSale(d.paymentMethod(), d.soldBy(), d.shopName()));
    }

    /** Specskart POS: finish billing on a web order the customer already verified and
     *  submitted — staff collect payment, mark it SOLD. */
    @Transactional
    public LensDtos.InquiryView completeSale(UUID id, LensDtos.CompleteSale d) {
        LensInquiry q = requireVerified(get(id));
        if (q.getPriceMinor() == null) q.setPriceMinor(pricing.quote(q));
        // Handing the pair over is what moves the shelf -- a web order sitting in the queue has
        // not consumed anything yet. Idempotent, and a no-op for a walk-in that named its own shop.
        takeLensStock(q, null);
        // Paid online already — don't let a handover overwrite that with "CASH".
        if (q.getPaidAt() == null) q.setPaymentMethod(d.paymentMethod());
        q.setSoldBy(d.soldBy());
        // keep the shop the pair came from (takeLensStock) unless staff name another
        if (d.shopName() != null) q.setShopName(d.shopName());
        q.setStatus("SOLD");
        // Billing a web order finishes it however staff got here — the doorstep ladder, or
        // "mark as sold" straight off the list when the customer collected in person.
        // Otherwise a sold order would sit on the pending list forever.
        if (!q.isWalkIn()) q.setFulfilment("DELIVERED");
        inquiries.save(q);
        notifyCustomer(q, "your lenses have been collected — enjoy them!");
        return view(q);
    }

    @Transactional(readOnly = true)
    public List<LensDtos.SaleView> pendingWebOrders() {
        return pendingWebOrders(false);
    }

    /**
     * The doorstep queue, or what has already left it.
     *
     * Delivering an order used to make it vanish from the app with nowhere to look it up:
     * the Orders screen is `fulfilment <> 'DELIVERED'`, and the Reports screen filters on
     * `status = 'SOLD'` for one day, which is a different field answering a different
     * question. Staff asking "did that one go out?" had neither.
     */
    @Transactional(readOnly = true)
    public List<LensDtos.SaleView> pendingWebOrders(boolean delivered) {
        List<LensInquiry> rows = delivered
                ? inquiries.findByFulfilmentOrderByCreatedAtDesc("DELIVERED")
                : inquiries.findByFulfilmentNotInOrderByCreatedAtAsc(List.of("DELIVERED", "CANCELLED"));
        return rows.stream().map(LensInquiryService::saleView).toList();
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
            // Handed over is when it's paid for — bill it here so the day's sales report matches
            // what actually left the shop. completeSale takes the stock and notifies.
            return completeSale(id, new LensDtos.CompleteSale(d.paymentMethod(), d.soldBy(), d.shopName()));
        }
        notifyCustomer(q, stageLine(q));
        return view(q);
    }

    /**
     * Staff call off a web order that won't go ahead (customer changed their mind, wrong Rx).
     * Not once it's delivered: that's a refund conversation, not a cancel. Its pair of lens
     * blanks goes back on the shelf it came from.
     * ponytail: an online payment is refunded by hand from the Flutterwave dashboard; the
     * customer is told it's coming. Wire the gateway's refund API if cancels get common.
     */
    @Transactional
    public LensDtos.InquiryView cancel(UUID id) {
        LensInquiry q = get(id);
        if (q.getFulfilment() == null || "DELIVERED".equals(q.getFulfilment()) || "CANCELLED".equals(q.getFulfilment())) {
            throw ApiException.badRequest("NOT_CANCELLABLE", "Only an open web order can be cancelled.");
        }
        q.setFulfilment("CANCELLED");
        q.setStatus("CANCELLED");
        if (q.getStockStoreId() != null) {
            products.findBySkuIgnoreCase("LENS-" + q.getLensType()).ifPresent(lens -> inventory.adjust(
                    q.getStockStoreId(), lens.getId(), 1, "REFUND", ref(q), "Lens order cancelled", null));
        }
        inquiries.save(q);
        notifyCustomer(q, "your lens order has been cancelled"
                + (q.getPaidAt() != null ? " — your online payment will be refunded" : ""));
        return view(q);
    }

    /**
     * Which shop the customer collects from. The pair is only taken off a shelf at handover, so
     * before that there is no stockStoreId and the shop is resolved the same way submit() does.
     * ponytail: with one shop those always agree. If a second shop is ever added, pin the
     * collection shop onto the inquiry at submit rather than re-deriving it here.
     */
    private Store collectionShop(LensInquiry q) {
        if (q.getStockStoreId() != null) return stores.findById(q.getStockStoreId()).orElse(null);
        Product lens = lensBlank(q);
        UUID id = lens == null ? null : lensShopId(null, lens);
        return id == null ? null : stores.findById(id).orElse(null);
    }

    /** Where to collect, as a line a customer can act on: name, street, and a tappable map. */
    static String collectionLine(Store shop) {
        if (shop == null) return "our shop";
        StringBuilder sb = new StringBuilder(shop.getName());
        if (!blank(shop.getAddress())) sb.append(", ").append(shop.getAddress());
        else if (!blank(shop.getCity())) sb.append(", ").append(shop.getCity());
        String map = mapsLink(shop);
        if (map != null) sb.append("\n📍 ").append(map);
        return sb.toString();
    }

    /**
     * A Google Maps link for the shop. The pin is exact and wins; failing that the address is
     * handed over as a search, which is still better than a name nobody can find. No API key
     * and no billing — these are plain public URLs.
     */
    static String mapsLink(Store shop) {
        if (shop.getLatitude() != null && shop.getLongitude() != null) {
            return "https://www.google.com/maps/search/?api=1&query="
                    + shop.getLatitude() + "," + shop.getLongitude();
        }
        String where = blank(shop.getAddress()) ? shop.getCity() : shop.getAddress();
        if (blank(where)) return null;
        return "https://www.google.com/maps/search/?api=1&query="
                + java.net.URLEncoder.encode(shop.getName() + " " + where, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** What the customer is told at each rung. */
    private String stageLine(LensInquiry q) {
        return switch (q.getFulfilment()) {
            case "READY" -> "your lenses are ready! Collect them at:\n" + collectionLine(collectionShop(q))
                    + "\n\nCome in yourself or send someone to pick them up — just quote " + ref(q) + ".";
            default -> "there's an update on your lens order";
        };
    }

    /**
     * The lead's most recent web lens order, phrased for the chatbot's "track my order" reply.
     * Empty until the order actually exists — a half-finished form has no fulfilment stage.
     */
    @Transactional(readOnly = true)
    public Optional<TrackUpdate> latestForLead(UUID leadId) {
        return inquiries.findTop1ByLeadIdAndWalkInFalseOrderByCreatedAtDesc(leadId)
                .filter(q -> q.getFulfilment() != null)
                .map(q -> new TrackUpdate(q.getCreatedAt(), "👓 Your lens order\n" + trackLine(q)));
    }

    /** Every lens order this lead has placed, as rows they can pick from. */
    @Transactional(readOnly = true)
    public List<TrackOption> optionsForLead(UUID leadId) {
        return inquiries.findByLeadIdAndWalkInFalseOrderByCreatedAtDesc(leadId).stream()
                .filter(q -> q.getFulfilment() != null)
                .map(q -> new TrackOption(
                        "TRACK:L:" + q.getId(),
                        q.getCreatedAt(),
                        ref(q),
                        "Lenses — " + trackTitle(q),
                        "👓 Your lens order\n" + trackLine(q)))
                .toList();
    }

    /** {@link #trackLine} shortened to fit a list row's second line. */
    private static String trackTitle(LensInquiry q) {
        return switch (q.getFulfilment()) {
            case "ORDERED" -> "Being made";
            case "READY" -> "Ready to collect";
            case "DELIVERED" -> "Collected";
            case "CANCELLED" -> "Cancelled";
            default -> "In progress";
        };
    }

    /** Where the order is right now, in the customer's words. */
    private String trackLine(LensInquiry q) {
        return switch (q.getFulfilment()) {
            case "ORDERED" -> "We have your prescription and your lenses are being made.";
            case "READY" -> "Ready to collect at:\n" + collectionLine(collectionShop(q))
                    + "\nCome in, or send someone to pick them up.";
            case "DELIVERED" -> "Collected 🎉 Anything not right with them? Just reply here.";
            case "CANCELLED" -> "Cancelled. Questions about it? Just reply here.";
            default -> "We're on it.";
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
                // {{4}} is the template's "track your order" link. It used to point at
                // /lens -- the configurator -- so a customer tapping it was handed a blank
                // form to order a second pair. This is their own order's status page.
                whatsapp.sendTemplate(q.getWaId(), props.whatsapp().orderUpdateTemplate(),
                        props.whatsapp().followUpTemplateLang(),
                        List.of(who, line, ref(q), props.frontendBaseUrl() + "/lens/track/" + q.getId()));
            } else {
                whatsapp.sendText(q.getWaId(), "Hi " + who + " — " + line + " (" + ref(q) + ").");
            }
        } catch (Exception e) {
            log.warn("lens {} customer notification failed: {}", ref(q), e.getMessage());
        }
    }

    /**
     * Takes one pair of this order's lens type off a shop's shelf, once -- at handover, not when
     * the order is placed. A counter sale passes its own shop; a web order takes whatever
     * {@link #lensShopId} resolves to. No stock still completes the sale: it is flagged a
     * backorder and the shelf goes negative, so the POS shows the shortfall.
     */
    private void takeLensStock(LensInquiry q, UUID storeId) {
        if (q.getStockStoreId() != null) return;
        Product lens = lensBlank(q);
        if (lens == null) return;
        storeId = lensShopId(storeId, lens);
        if (storeId == null) return; // no shop set up yet
        q.setBackorder(inventory.quantityOf(storeId, lens.getId()) < 1);
        inventory.adjust(storeId, lens.getId(), -1, "SALE", ref(q), "Lens order", null);
        q.setStockStoreId(storeId);
        stores.findById(storeId).ifPresent(s -> q.setShopName(s.getName()));
    }

    private Product lensBlank(LensInquiry q) {
        return products.findBySkuIgnoreCase("LENS-" + q.getLensType()).orElse(null);
    }

    /**
     * Whose shelf a web order's pair comes off. The client runs a single shop, so that is the
     * answer whenever exactly one is active -- including when it was never pinned on the map,
     * which {@code allocate} requires and which is the reason this fallback exists.
     */
    private UUID lensShopId(UUID storeId, Product lens) {
        if (storeId != null) return storeId;
        var allocation = inventory.allocate(Map.of(lens.getId(), 1), null, null, null);
        if (allocation != null) return allocation.primary().getId();
        var active = stores.findByActiveTrue();
        return active.size() == 1 ? active.get(0).getId() : null;
    }

    private void alertStaff(LensInquiry q) {
        if (props.whatsapp().staffNumbers().isEmpty()) return;
        String base = props.whatsapp().absoluteAsset("/api/public/staff-docs/lens/" + q.getId());
        String pdfUrl = base == null ? null : base + ".pdf" + links.query("lens/" + q.getId(), StaffDocController.TTL);
        String price = q.getPriceMinor() == null ? "—" : OrderNotificationService.money(q.getPriceMinor(), q.getCurrency());
        String who = q.getCustomerName() == null || q.getCustomerName().isBlank() ? "+" + q.getWaId() : q.getCustomerName();
        // {{4}} is the staff member's next action, not a copy of the slip: the PDF already rides
        // the template as its document header, so repeating its link here bought nothing. This
        // one opens the POS app on this order, where the doorstep ladder is.
        String appLink = props.whatsapp().absoluteAsset("/api/public/open/lens/" + q.getId());
        List<String> failures = staffAlerts.send(staffLines(q, memberships.discountPercentFor(q.getLeadId())),
                pdfUrl, ref(q) + ".pdf",
                List.of(ref(q), price, who, appLink == null ? props.frontendBaseUrl() : appLink),
                appLink, null);
        // No order timeline to hang these on, as there is for web orders -- at least say it out loud.
        for (String failure : failures) log.warn("lens {} staff alert to {}", ref(q), failure);
    }

    /** Everything the lens lab needs — drawn into the staff PDF and used as the WhatsApp caption. */
    public static List<String> staffLines(LensInquiry q) {
        return staffLines(q, 0);
    }

    /**
     * @param memberPercent the Specskart Care discount already baked into the quoted price, or 0.
     *                      Spelled out on the slip because otherwise the counter sees a price that
     *                      looks wrong and "corrects" it.
     */
    public static List<String> staffLines(LensInquiry q, int memberPercent) {
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
        if (!q.isWalkIn()) out.add("Collection: customer picks up at the shop");
        if (q.getShopName() != null) out.add("Make at: " + q.getShopName());
        if (q.isBackorder()) out.add("⚠ BACKORDER — no shop had this lens in stock, order blanks in");
        out.add("");
        out.add("Quoted price: " + (q.getPriceMinor() == null ? "—"
                : OrderNotificationService.money(q.getPriceMinor(), q.getCurrency())));
        if (memberPercent > 0) {
            out.add("⭐ " + com.specskart.membership.MembershipService.NAME + " member — "
                    + memberPercent + "% already taken off. This price is correct.");
        }
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

    private LensDtos.InquiryView view(LensInquiry q) {
        Store shop = collectionShop(q);
        return new LensDtos.InquiryView(q.getId(), q.getStatus(), q.isPhoneVerified(),
                q.getLensType(), q.isBlueBlock(),
                q.getCustomerName(), q.getAge(), q.getGender(),
                q.getSphRight(), q.getSphLeft(), q.getCylRight(), q.getCylLeft(),
                q.getAxisRight(), q.getAxisLeft(), q.getAddPower(), q.getLensStructure(),
                q.isSpecialAxis(), q.getPriceMinor(), q.getCurrency(),
                q.getFulfilment(), q.isPaid(),
                shop == null ? null : shop.getName(),
                shop == null ? null : shop.getAddress(),
                shop == null ? null : mapsLink(shop));
    }
}
