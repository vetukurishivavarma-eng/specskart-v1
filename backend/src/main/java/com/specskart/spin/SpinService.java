package com.specskart.spin;

import com.specskart.config.AppProperties;
import com.specskart.lead.Lead;
import com.specskart.lead.LeadRepository;
import com.specskart.lens.LensInquiryService;
import com.specskart.push.PushService;
import com.specskart.shared.ApiException;
import com.specskart.shared.PhoneNumbers;
import com.specskart.shared.SignedLinks;
import com.specskart.whatsapp.ChatLog;
import com.specskart.whatsapp.LeadNudges;
import com.specskart.whatsapp.ServiceWindow;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.UUID;

/**
 * Spin & Win (2026-10-21): every spin wins a prize that is collected at the shop within 2 weeks.
 *
 * One prize at a time (client, 2026-10-21): the spin page is only reachable from a signed link the
 * WhatsApp bot sends (so each spin belongs to a real WhatsApp number), a repeat spin while their prize
 * (or a friend code they joined with) is still in its week returns / refuses, and the lead row is
 * locked while spinning so two taps can't both spin. Once those 2 weeks are over they can spin again. The server picks the
 * prize -- the browser only animates the wheel to it -- and each slice's size on the wheel is its
 * real chance.
 */
@Service
public class SpinService {

    private static final Logger log = LoggerFactory.getLogger(SpinService.class);
    static final Duration VALID_FOR = Duration.ofDays(14); // client: 1 week, then 2 weeks (2026-10-21)
    static final Duration LINK_TTL = Duration.ofDays(14);
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no I,L,O,0,1
    private static final SecureRandom RNG = new SecureRandom();
    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH)
            .withZone(ZoneId.of("Africa/Lusaka"));

    /** The client's prizes (2026-10-21, same as V72); chances are a starting point, editable in the web admin. */
    static final List<Object[]> DEFAULT_PRIZES = List.of(
            new Object[]{"Free eye check for your family", 35},
            new Object[]{"K100 off photochromatic + blue block lenses", 25},
            new Object[]{"Free sunglasses with your first pair", 15},
            new Object[]{"Bring a friend: K100 off for you both", 15},
            new Object[]{"20% off your second frame", 10});

    public record Slice(String label, int weight) {}
    /** shareLink: set for a bring-a-friend prize until the friend has messaged us. */
    public record Result(String prize, String code, Instant expiresAt, boolean redeemed, int sliceIndex, String shareLink) {}
    public record Page(String firstName, List<Slice> slices, Result result) {}

    private final SpinRepository spins;
    private final SpinPrizeRepository prizes;
    private final LeadRepository leads;
    private final SignedLinks links;
    private final AppProperties props;
    private final WhatsAppProvider whatsapp;
    private final ChatLog chatLog;
    private final PushService push;
    private final LensInquiryService lens;
    private final ServiceWindow window;

    public SpinService(SpinRepository spins, SpinPrizeRepository prizes, LeadRepository leads, SignedLinks links,
                       AppProperties props, WhatsAppProvider whatsapp, ChatLog chatLog, PushService push,
                       LensInquiryService lens, ServiceWindow window) {
        this.window = window;
        this.spins = spins;
        this.prizes = prizes;
        this.leads = leads;
        this.links = links;
        this.props = props;
        this.whatsapp = whatsapp;
        this.chatLog = chatLog;
        this.push = push;
        this.lens = lens;
    }

    // ---- the bot's side -----------------------------------------------------------------------

    /** The personal link to this customer's spin. */
    public String link(Lead lead) {
        String resource = "spin/" + lead.getId();
        return props.frontendBaseUrl() + "/spin/" + lead.getId() + links.query(resource, LINK_TTL);
    }

    /** Their spin whose week isn't over yet (used or not): until it ends they can't spin again. */
    private java.util.Optional<Spin> activeSpin(UUID leadId) {
        return spins.findFirstByLeadIdAndExpiresAtAfterOrderByCreatedAtDesc(leadId, Instant.now());
    }

    /** A friend's code they joined with, still in its week. */
    private java.util.Optional<Spin> activeFriendCode(UUID leadId) {
        return spins.findFirstByFriendLeadIdAndExpiresAtAfterOrderByCreatedAtDesc(leadId, Instant.now());
    }

    private String friendCodeHeld(Spin f) {
        return "You already have a prize from your friend 🎁 *" + f.getPrizeLabel() + "*, code *" + f.getFriendCode()
                + "*, valid until " + UNTIL.format(f.getExpiresAt()) + ". You can spin after it expires.";
    }

    /** What the bot says to "spin": the link, or the prize they already hold. */
    public String offer(Lead lead) {
        var held = activeFriendCode(lead.getId());
        if (held.isPresent() && activeSpin(lead.getId()).isEmpty()) return friendCodeHeld(held.get());
        var done = activeSpin(lead.getId());
        if (done.isPresent()) {
            Spin s = done.get();
            if (s.getRedeemedAt() != null) {
                return "You've already used your prize (" + s.getPrizeLabel() + ") 🎁 Thank you! You can spin again after "
                        + UNTIL.format(s.getExpiresAt()) + ".";
            }
            if (waitingForFriend(s)) {
                return "You've already had your spin 🎁\nYou won: *" + s.getPrizeLabel() + "*\n\n" + friendSteps(s);
            }
            return "You've already had your spin 🎁\nYou won: *" + s.getPrizeLabel() + "*\nCode: *" + s.getCode()
                    + "* — show it at the shop by " + UNTIL.format(s.getExpiresAt()) + ".\n\n" + lens.shopLines();
        }
        return "🎁 *Spin & Win!* Every spin wins a prize.\n\nTap to spin:\n" + link(lead);
    }

    // ---- the spin page ------------------------------------------------------------------------

    @Transactional
    public Page page(UUID leadId, long exp, String sig) {
        Lead lead = authorised(leadId, exp, sig);
        List<SpinPrize> wheel = wheel();
        var own = activeSpin(leadId);
        if (own.isEmpty()) activeFriendCode(leadId).ifPresent(f -> { throw ApiException.conflict("HAS_PRIZE", friendCodeHeld(f)); });
        Result result = own.map(s -> result(s, wheel)).orElse(null);
        String first = lead.getName() == null || lead.getName().isBlank() ? null : lead.getName().trim().split("\\s+")[0];
        return new Page(first, wheel.stream().map(p -> new Slice(p.getLabel(), p.getWeight())).toList(), result);
    }

    /**
     * One spin per prize (valid 2 weeks). A second call (double tap, reload, second phone) while the prize
     * is still in its week returns that prize; a friend's code they hold blocks spinning until it ends.
     */
    @Transactional
    public Result spin(UUID leadId, long exp, String sig) {
        authorised(leadId, exp, sig);
        Lead lead = leads.lockById(leadId).orElseThrow(); // two taps at once: the second waits, then sees the first spin
        List<SpinPrize> wheel = wheel();
        var existing = activeSpin(leadId);
        if (existing.isPresent()) return result(existing.get(), wheel);
        var held = activeFriendCode(leadId);
        if (held.isPresent()) throw ApiException.conflict("HAS_PRIZE", friendCodeHeld(held.get()));

        SpinPrize won = pick(wheel, RNG.nextInt(wheel.stream().mapToInt(SpinPrize::getWeight).sum()));
        Spin s = new Spin();
        s.setLeadId(leadId);
        s.setPrizeLabel(won.getLabel());
        s.setCode(freshCode());
        s.setExpiresAt(Instant.now().plus(VALID_FOR));
        spins.saveAndFlush(s);
        tell(lead, s);
        return result(s, wheel);
    }

    /** Weighted pick: {@code roll} in [0, total weight). */
    static SpinPrize pick(List<SpinPrize> wheel, int roll) {
        for (SpinPrize p : wheel) {
            if (roll < p.getWeight()) return p;
            roll -= p.getWeight();
        }
        return wheel.get(wheel.size() - 1);
    }

    private void tell(Lead lead, Spin s) {
        String waId = lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
        String body = isFriendPrize(s.getPrizeLabel())
                ? "🎉 You won: *" + s.getPrizeLabel() + "*!\n\n" + friendSteps(s)
                : "🎉 You won: *" + s.getPrizeLabel() + "*!\n\nYour code: *" + s.getCode() + "*\n"
                + "Show it at the shop by *" + UNTIL.format(s.getExpiresAt()) + "* (valid for 2 weeks).\n\n" + lens.shopLines();
        try {
            whatsapp.sendButtons(waId, body, List.of(new WhatsAppProvider.Button(LeadNudges.BTN_BOOK, "Book my visit")));
            chatLog.sent(waId, body);
        } catch (Exception e) { // the page shows the code too, so a failed send loses nothing
            log.warn("spin prize message to lead {} failed: {}", lead.getId(), e.getMessage());
        }
        String who = lead.getName() != null && !lead.getName().isBlank() ? lead.getName() : "+" + waId;
        push.notifyStaff("🎁 " + who + " won " + s.getPrizeLabel(), "Code " + s.getCode() + " · valid until "
                + UNTIL.format(s.getExpiresAt()), "/chat/" + waId);
    }

    // ---- bring a friend -----------------------------------------------------------------------

    private static final Pattern CODE_IN_TEXT = Pattern.compile("(?i)\\bSPIN-([A-Z0-9]{5})\\b");

    /** A prize whose name mentions a friend unlocks only when a new customer messages us with the code. */
    static boolean isFriendPrize(String label) {
        // ponytail: matched by name; add a flag on spin_prizes if the client wants a friend prize without the word
        return label != null && label.toLowerCase(Locale.ROOT).contains("friend");
    }

    /** The SPIN-XXXXX code in a customer's message, if any. */
    public static String codeIn(String text) {
        if (text == null) return null;
        Matcher m = CODE_IN_TEXT.matcher(text);
        return m.find() ? "SPIN-" + m.group(1).toUpperCase(Locale.ROOT) : null;
    }

    /**
     * A customer messaged us with someone's spin code. For a bring-a-friend prize, a customer who is
     * new (in our system only since that spin) becomes the friend: they get their own code and the
     * winner's code starts working. Returns the bot's reply.
     */
    @Transactional
    public String claimFriend(Lead friend, String code) {
        Spin s = spins.findByCodeIgnoreCase(code).orElse(null);
        if (s == null) {
            return spins.findByFriendCodeIgnoreCase(code)
                    .filter(f -> friend.getId().equals(f.getFriendLeadId()))
                    .map(f -> "That's your code 🎁 *" + f.getFriendCode() + "* — show it at the shop by "
                            + UNTIL.format(f.getExpiresAt()) + ".\n\n" + lens.shopLines())
                    .orElse("We couldn't find the code " + code + ". Please check it with your friend.");
        }
        if (s.getLeadId().equals(friend.getId())) {
            return waitingForFriend(s) ? "That's your own code 🙂 " + friendSteps(s) : offer(friend);
        }
        if (!isFriendPrize(s.getPrizeLabel())) {
            return "That code belongs to another customer's prize, so it can't be shared. Type *spin* to win your own 🎁";
        }
        if (friend.getId().equals(s.getFriendLeadId())) {
            return "You've already joined 🎉 Your code: *" + s.getFriendCode() + "* — show it at the shop by "
                    + UNTIL.format(s.getExpiresAt()) + ".\n\n" + lens.shopLines();
        }
        if (s.getFriendLeadId() != null) return "Sorry, a friend has already used this code. Type *spin* to win your own prize 🎁";
        if (s.getExpiresAt().isBefore(Instant.now())) return "Sorry, this prize expired on " + UNTIL.format(s.getExpiresAt()) + ".";
        if (friend.getCreatedAt() != null && friend.getCreatedAt().isBefore(s.getCreatedAt())) {
            return "Thanks for messaging! This friend offer is for new customers only. Type *spin* to win your own prize 🎁";
        }
        // one prize at a time: someone already holding a code in its week can't take a friend's too
        var ownHeld = activeSpin(friend.getId());
        if (ownHeld.isPresent()) {
            return "You already have a prize code (*" + ownHeld.get().getCode() + "*), so you can't use a friend's code until it expires on "
                    + UNTIL.format(ownHeld.get().getExpiresAt()) + ".";
        }
        var friendHeld = activeFriendCode(friend.getId());
        if (friendHeld.isPresent()) {
            return "You already have a prize code (*" + friendHeld.get().getFriendCode() + "*), so you can't use another friend's code until it expires on "
                    + UNTIL.format(friendHeld.get().getExpiresAt()) + ".";
        }

        s.setFriendLeadId(friend.getId());
        s.setFriendCode(freshCode());
        try {
            spins.saveAndFlush(s);
        } catch (DataIntegrityViolationException raced) {
            return "Sorry, a friend has already used this code.";
        }
        Lead winner = leads.findById(s.getLeadId()).orElse(null);
        String winnerName = firstName(winner, "Your friend");
        String friendName = firstName(friend, "Your friend");
        if (winner != null) {
            String body = "🎉 " + friendName + " messaged us with your code — your prize is unlocked!\n\n*" + s.getPrizeLabel()
                    + "*\nYour code: *" + s.getCode() + "*\nUse it at the shop by *" + UNTIL.format(s.getExpiresAt()) + "*.\n\n" + lens.shopLines();
            try {
                whatsapp.sendButtons(waId(winner), body, List.of(new WhatsAppProvider.Button(LeadNudges.BTN_BOOK, "Book my visit")));
                chatLog.sent(waId(winner), body);
            } catch (Exception e) {
                log.warn("friend-unlocked message to lead {} failed: {}", winner.getId(), e.getMessage());
            }
        }
        push.notifyStaff("🤝 " + friendName + " joined with " + winnerName + "'s code",
                s.getCode() + " + " + s.getFriendCode() + " valid until " + UNTIL.format(s.getExpiresAt()), "/chat/" + waId(friend));
        return "Welcome to " + props.storeName() + "! 🎉 " + winnerName + " shared a prize with you:\n*" + s.getPrizeLabel()
                + "*\n\nYour code: *" + s.getFriendCode() + "*\nShow it at the shop by *" + UNTIL.format(s.getExpiresAt())
                + "*.\n\n" + lens.shopLines();
    }

    private boolean waitingForFriend(Spin s) {
        return isFriendPrize(s.getPrizeLabel()) && s.getFriendLeadId() == null;
    }

    /** What the winner forwards: our WhatsApp link with their code already typed in. */
    private String shareLink(Spin s) {
        String business = PhoneNumbers.normalize(props.businessWhatsappNumber(), props.whatsapp().defaultCountryCode());
        if (business == null) return null;
        return "https://wa.me/" + business + "?text="
                + URLEncoder.encode("Hi! My friend's code is " + s.getCode(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String friendSteps(Spin s) {
        String link = shareLink(s);
        return "To unlock it, a friend who is new to " + props.storeName() + " must message us with your code *" + s.getCode() + "*"
                + (link == null ? "." : ":\n" + link) + "\n\nForward this to them 👆 As soon as they message us, you both get a code"
                + " to use at the shop by *" + UNTIL.format(s.getExpiresAt()) + "*.";
    }

    private static String firstName(Lead l, String fallback) {
        return l == null || l.getName() == null || l.getName().isBlank() ? fallback : l.getName().trim().split("\\s+")[0];
    }

    /** "+260 ••• 9599": staff see only the last 4 digits, so at the till the customer has to say their number. */
    static String masked(String waId) {
        return waId == null || waId.length() < 7 ? waId : "+" + waId.substring(0, 3) + " ••• " + waId.substring(waId.length() - 4);
    }

    private static String waId(Lead l) {
        return l.getWhatsappWaId() != null ? l.getWhatsappWaId() : l.getWhatsappNumber();
    }

    // ---- staff --------------------------------------------------------------------------------

    public record SpinView(String code, String prize, String customer, String phone, Instant spunAt,
                           Instant expiresAt, Instant redeemedAt, String redeemedBy, String state) {}

    @Transactional(readOnly = true)
    public List<SpinView> recent() {
        return spins.findTop100ByOrderByCreatedAtDesc().stream()
                .flatMap(s -> s.getFriendCode() == null ? Stream.of(view(s)) : Stream.of(view(s), friendView(s)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SpinView lookup(String code) {
        String c = normalise(code);
        return spins.findByCodeIgnoreCase(c).map(this::view)
                .or(() -> spins.findByFriendCodeIgnoreCase(c).map(this::friendView))
                .orElseThrow(() -> ApiException.notFound("NO_CODE", "No prize with that code."));
    }

    static final Duration OTP_TTL = Duration.ofMinutes(10);
    static final int OTP_TRIES = 5;

    /** A prize code at the till: the winner's code or a friend's code on the same spin. */
    private record Holder(Spin spin, boolean friend, String code) {
        UUID leadId() { return friend ? spin.getFriendLeadId() : spin.getLeadId(); }
        Instant redeemedAt() { return friend ? spin.getFriendRedeemedAt() : spin.getRedeemedAt(); }
    }

    private Holder holder(String code) {
        String c = normalise(code);
        var asFriend = spins.findByFriendCodeIgnoreCase(c);
        if (asFriend.isPresent()) return new Holder(asFriend.get(), true, c);
        return new Holder(byCode(c), false, c);
    }

    /**
     * Still good to give, and the number staff typed is the customer's own WhatsApp. Returns their waId.
     * A wrong number says so without naming the right one.
     */
    private String checkAtTill(Holder h, String phone) {
        Spin s = h.spin();
        if (!h.friend() && waitingForFriend(s)) {
            throw ApiException.conflict("WAITING_FRIEND",
                    "Not unlocked yet: no new friend has messaged us with this code. It works once one does.");
        }
        if (h.redeemedAt() != null) {
            throw ApiException.conflict("ALREADY_USED", "This code was already used on " + UNTIL.format(h.redeemedAt()) + ".");
        }
        if (s.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.conflict("EXPIRED", "This code expired on " + UNTIL.format(s.getExpiresAt()) + ".");
        }
        Lead lead = leads.findById(h.leadId()).orElseThrow(() -> ApiException.notFound("NO_LEAD", "The customer for this code is gone."));
        String typed = PhoneNumbers.normalize(phone, props.whatsapp().defaultCountryCode());
        String theirs = PhoneNumbers.normalize(waId(lead), props.whatsapp().defaultCountryCode());
        if (typed == null || !typed.equals(theirs)) {
            throw ApiException.badRequest("WRONG_NUMBER", "This code was won on a different WhatsApp number. Check the number with the customer.");
        }
        return waId(lead);
    }

    /**
     * At the till, step 1: staff type the customer's WhatsApp number + prize code, and we WhatsApp the
     * customer a 6-digit verification code (valid 10 minutes). Free-form text, so only inside WhatsApp's
     * 24h window -- otherwise the customer sends us any message first.
     */
    @Transactional
    public String sendRedeemCode(String code, String phone) {
        Holder h = holder(code);
        String waId = checkAtTill(h, phone);
        if (!window.isOpen(waId)) {
            throw ApiException.conflict("WINDOW_CLOSED", "Ask the customer to send any message (like \"hi\") to our WhatsApp, "
                    + "then tap Send code again. WhatsApp only lets us message them within 24 hours of their last message.");
        }
        String otp = String.format("%06d", RNG.nextInt(1_000_000));
        Spin s = h.spin();
        s.setOtpCode(h.code());
        s.setOtpHash(otpHash(s, otp));
        s.setOtpExpiresAt(Instant.now().plus(OTP_TTL));
        s.setOtpTries(0);
        spins.save(s);
        whatsapp.sendText(waId, "🔐 Your verification code is *" + otp + "*\n\nGive it to our staff to collect your prize: "
                + s.getPrizeLabel() + ". It works for 10 minutes.\nNot at " + props.storeName() + " right now? Ignore this message.");
        // the chat shows that a code went out, never the code itself: staff can read the chat
        chatLog.sent(waId, "🔐 Sent a prize verification code for " + h.code());
        return masked(waId);
    }

    /** At the till, step 2: the code the customer got on WhatsApp. The prize code is then used, once. */
    @Transactional(noRollbackFor = ApiException.class) // a wrong try is counted even though it throws
    public SpinView redeem(String code, String phone, String otp, String staff) {
        Holder h = holder(code);
        checkAtTill(h, phone);
        Spin s = h.spin();
        if (s.getOtpHash() == null || !h.code().equals(s.getOtpCode()) || s.getOtpExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("SEND_CODE", "Tap Send code to WhatsApp the customer a new verification code.");
        }
        if (s.getOtpTries() >= OTP_TRIES) {
            throw ApiException.badRequest("TOO_MANY_TRIES", "Too many wrong tries. Tap Send code for a new verification code.");
        }
        if (otp == null || !otpHash(s, otp.trim()).equals(s.getOtpHash())) {
            s.setOtpTries(s.getOtpTries() + 1);
            spins.save(s);
            throw ApiException.badRequest("WRONG_CODE", "That verification code is wrong. Check the WhatsApp message with the customer.");
        }
        s.setOtpHash(null);
        s.setOtpCode(null);
        s.setOtpExpiresAt(null);
        if (h.friend()) {
            s.setFriendRedeemedAt(Instant.now());
            s.setFriendRedeemedBy(staff);
            return friendView(spins.save(s));
        }
        s.setRedeemedAt(Instant.now());
        s.setRedeemedBy(staff);
        return view(spins.save(s));
    }

    private static String otpHash(Spin s, String otp) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest((s.getId() + ":" + otp).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public List<SpinPrize> prizes() {
        wheel(); // seeds the defaults the first time
        return prizes.findAllByOrderBySortOrderAsc();
    }

    public record PrizeInput(String label, int weight, boolean active) {}

    /** Replace the wheel. Spins already won keep their prize text. */
    @Transactional
    public List<SpinPrize> savePrizes(List<PrizeInput> in) {
        if (in == null || in.stream().noneMatch(p -> p.active() && p.weight() > 0)) {
            throw ApiException.badRequest("NO_PRIZES", "Keep at least one prize switched on with a chance above 0.");
        }
        for (PrizeInput p : in) {
            if (p.label() == null || p.label().isBlank() || p.label().length() > 80) {
                throw ApiException.badRequest("BAD_PRIZE", "Each prize needs a name of up to 80 characters.");
            }
            if (p.weight() < 0) throw ApiException.badRequest("BAD_PRIZE", "A chance can't be negative.");
        }
        prizes.deleteAll();
        prizes.flush();
        int order = 0;
        for (PrizeInput p : in) {
            SpinPrize sp = new SpinPrize();
            sp.setLabel(p.label().trim());
            sp.setWeight(p.weight());
            sp.setActive(p.active());
            sp.setSortOrder(order++);
            prizes.save(sp);
        }
        return prizes.findAllByOrderBySortOrderAsc();
    }

    // ---- helpers ------------------------------------------------------------------------------

    private Lead authorised(UUID leadId, long exp, String sig) {
        if (!links.valid("spin/" + leadId, exp, sig)) {
            throw ApiException.forbidden("BAD_LINK", "This spin link has expired. Message us on WhatsApp \"spin\" for a new one.");
        }
        return leads.findById(leadId).orElseThrow(() -> ApiException.notFound("NO_LEAD", "We couldn't find you — message us on WhatsApp."));
    }

    /** The live wheel: active prizes with a chance above 0. Seeds the client's prizes if there are none. */
    private List<SpinPrize> wheel() {
        if (prizes.count() == 0) {
            int order = 0;
            for (Object[] d : DEFAULT_PRIZES) {
                SpinPrize p = new SpinPrize();
                p.setLabel((String) d[0]);
                p.setWeight((Integer) d[1]);
                p.setSortOrder(order++);
                prizes.save(p);
            }
        }
        return prizes.findByActiveTrueOrderBySortOrderAsc().stream().filter(p -> p.getWeight() > 0).toList();
    }

    private Result result(Spin s, List<SpinPrize> wheel) {
        int idx = 0;
        for (int i = 0; i < wheel.size(); i++) if (wheel.get(i).getLabel().equals(s.getPrizeLabel())) idx = i;
        return new Result(s.getPrizeLabel(), s.getCode(), s.getExpiresAt(), s.getRedeemedAt() != null, idx,
                waitingForFriend(s) ? shareLink(s) : null);
    }

    private static String normalise(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return c.startsWith("SPIN-") ? c : "SPIN-" + c;
    }

    private Spin byCode(String code) {
        return spins.findByCodeIgnoreCase(normalise(code)).orElseThrow(() -> ApiException.notFound("NO_CODE", "No prize with that code."));
    }

    private SpinView view(Spin s) {
        Lead l = leads.findById(s.getLeadId()).orElse(null);
        String state = s.getRedeemedAt() != null ? "USED" : s.getExpiresAt().isBefore(Instant.now()) ? "EXPIRED"
                : waitingForFriend(s) ? "WAITING_FRIEND" : "VALID";
        return new SpinView(s.getCode(), s.getPrizeLabel(), l == null ? null : l.getName(), l == null ? null : masked(waId(l)),
                s.getCreatedAt(), s.getExpiresAt(), s.getRedeemedAt(), s.getRedeemedBy(), state);
    }

    /** The friend's own code, shown like any other prize. */
    private SpinView friendView(Spin s) {
        Lead f = leads.findById(s.getFriendLeadId()).orElse(null);
        Lead w = leads.findById(s.getLeadId()).orElse(null);
        String state = s.getFriendRedeemedAt() != null ? "USED" : s.getExpiresAt().isBefore(Instant.now()) ? "EXPIRED" : "VALID";
        return new SpinView(s.getFriendCode(), s.getPrizeLabel() + " (friend of " + firstName(w, "a winner") + ")",
                f == null ? null : f.getName(), f == null ? null : masked(waId(f)),
                s.getUpdatedAt(), s.getExpiresAt(), s.getFriendRedeemedAt(), s.getFriendRedeemedBy(), state);
    }

    private String freshCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder("SPIN-");
            for (int i = 0; i < 5; i++) sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
            code = sb.toString();
        } while (spins.existsByCode(code) || spins.existsByFriendCode(code));
        return code;
    }
}
