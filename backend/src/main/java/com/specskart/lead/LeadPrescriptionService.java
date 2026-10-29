package com.specskart.lead;

import com.specskart.config.AppProperties;
import com.specskart.lens.LensInquiry;
import com.specskart.lens.LensInquiryRepository;
import com.specskart.lens.LensInquiryService;
import com.specskart.order.PrescriptionFile;
import com.specskart.order.PrescriptionFileRepository;
import com.specskart.shared.ApiException;
import com.specskart.shared.PhoneNumbers;
import com.specskart.whatsapp.WhatsAppMessageRepository;
import com.specskart.whatsapp.WhatsAppProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every prescription we hold for one customer, newest first, so staff never scroll back through a chat:
 * online lens orders, lens sales at the counter (matched by WhatsApp number -- the counter never linked
 * them to the lead), and chat photos staff saved, with the numbers typed from them.
 */
@Service
public class LeadPrescriptionService {

    private final LeadPrescriptionRepository saved;
    private final PrescriptionFileRepository files;
    private final LensInquiryRepository inquiries;
    private final WhatsAppMessageRepository messages;
    private final WhatsAppProvider provider;
    private final LeadRepository leads;
    private final AppProperties props;
    private final WalkInService walkIns;

    public LeadPrescriptionService(LeadPrescriptionRepository saved, PrescriptionFileRepository files,
                                   LensInquiryRepository inquiries, WhatsAppMessageRepository messages,
                                   WhatsAppProvider provider, LeadRepository leads, AppProperties props,
                                   @org.springframework.context.annotation.Lazy WalkInService walkIns) {
        this.walkIns = walkIns;
        this.leads = leads;
        this.props = props;
        this.saved = saved;
        this.files = files;
        this.inquiries = inquiries;
        this.messages = messages;
        this.provider = provider;
    }

    public record Numbers(BigDecimal sphRight, BigDecimal sphLeft, BigDecimal cylRight, BigDecimal cylLeft,
                          Integer axisRight, Integer axisLeft, BigDecimal addPower, String pd,
                          String patientName, Boolean renameCustomer) {}

    @Transactional(readOnly = true)
    public List<Map<String, Object>> forLead(Lead lead) {
        return list(lead, waId(lead));
    }

    /** At the counter: a number typed on the bill -> that customer's prescriptions, lead or not. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forPhone(String phone) {
        String waId = PhoneNumbers.normalize(phone, props.whatsapp().defaultCountryCode());
        if (waId == null || waId.length() < 8) return List.of();
        return leads.findByWhatsappWaId(waId).map(this::forLead).orElseGet(() -> list(null, waId));
    }

    private List<Map<String, Object>> list(Lead lead, String waId) {
        UUID leadId = lead == null ? null : lead.getId();
        List<Map<String, Object>> out = new ArrayList<>();
        for (LeadPrescription p : leadId == null ? List.<LeadPrescription>of() : saved.findByLeadIdOrderByCreatedAtDesc(leadId)) {
            Map<String, Object> m = rx(p.getId(), "SAVED", p.getCreatedAt(), p.getSphRight(), p.getSphLeft(),
                    p.getCylRight(), p.getCylLeft(), p.getAxisRight(), p.getAxisLeft(), p.getAddPower(), p.getPd());
            m.put("hasPhoto", p.getFileId() != null);
            m.put("savedBy", p.getSavedBy());
            m.put("leadId", p.getLeadId());
            m.put("customerName", lead.getName());
            m.put("patientName", p.getPatientName());
            out.add(m);
        }
        // never pass null: Spring Data turns it into "lead_id is null" and matches every leadless sale
        for (LensInquiry q : inquiries.findByLeadIdOrWaIdOrderByCreatedAtDesc(
                leadId == null ? new UUID(0, 0) : leadId, waId == null ? "-" : waId)) {
            if (q.getSphRight() == null && q.getSphLeft() == null && q.getCylRight() == null && q.getCylLeft() == null) {
                continue; // a lens order abandoned before the prescription step
            }
            Map<String, Object> m = rx(q.getId(), q.isWalkIn() ? "SHOP" : "ONLINE", q.getCreatedAt(), q.getSphRight(),
                    q.getSphLeft(), q.getCylRight(), q.getCylLeft(), q.getAxisRight(), q.getAxisLeft(), q.getAddPower(), q.getPd());
            m.put("ref", LensInquiryService.ref(q));
            m.put("shopName", q.getShopName());
            m.put("lensStructure", q.getLensStructure());
            m.put("hasPhoto", false);
            m.put("patientName", q.getCustomerName());
            out.add(m);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> (Instant) m.get("at")).reversed());
        return out;
    }

    /** Copy a photo the customer sent in this chat into our own storage. Saving it twice is a no-op. */
    @Transactional
    public LeadPrescription savePhoto(Lead lead, String mediaId, String savedBy) {
        var existing = saved.findByLeadIdAndMediaId(lead.getId(), mediaId);
        if (existing.isPresent()) return existing.get();
        if (!messages.existsByLeadIdAndMediaId(lead.getId(), mediaId)) {
            throw ApiException.notFound("NO_MEDIA", "That photo isn't in this chat.");
        }
        WhatsAppProvider.Media media;
        try {
            media = provider.downloadMedia(mediaId);
        } catch (Exception e) {
            throw ApiException.notFound("MEDIA_GONE", "WhatsApp no longer has this photo, so it can't be saved.");
        }
        // ponytail: photos only; allow application/pdf here (and an open-file button in the app) if customers send PDFs
        if (media.mimeType() == null || !media.mimeType().startsWith("image/")) {
            throw ApiException.badRequest("NOT_A_PHOTO", "Only photos can be saved as a prescription.");
        }
        if (media.bytes().length > 10_000_000) {
            throw ApiException.badRequest("TOO_BIG", "That photo is too large to save.");
        }
        PrescriptionFile f = new PrescriptionFile();
        f.setContentType(media.mimeType());
        f.setBytes(media.bytes());
        f = files.save(f);
        LeadPrescription p = new LeadPrescription();
        p.setLeadId(lead.getId());
        p.setMediaId(mediaId);
        p.setFileId(f.getId());
        p.setSavedBy(savedBy);
        return saved.save(p);
    }

    /** A prescription staff add by hand (paper Rx at the shop): finds the customer by number, or saves
     *  them as a walk-in. Photo and numbers follow via uploadPhoto / setNumbers. */
    @Transactional
    public LeadPrescription create(String phone, String name, String savedBy) {
        String waId = PhoneNumbers.normalize(phone == null ? "" : phone, props.whatsapp().defaultCountryCode());
        if (waId == null || waId.length() < 8) {
            throw ApiException.badRequest("BAD_NUMBER", "Enter the customer's phone number.");
        }
        Lead lead = leads.findByWhatsappWaId(waId).orElse(null);
        if (lead == null) {
            walkIns.manual(null, name, phone);
            lead = leads.findByWhatsappWaId(waId).orElseThrow();
        }
        LeadPrescription p = new LeadPrescription();
        p.setLeadId(lead.getId());
        p.setSavedBy(savedBy);
        return saved.save(p);
    }

    /** A photo taken or picked on the till for a prescription added by hand (replaces any earlier one). */
    @Transactional
    public void uploadPhoto(UUID leadId, UUID id, byte[] bytes, String contentType) {
        LeadPrescription p = get(leadId, id);
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ApiException.badRequest("NOT_A_PHOTO", "Only photos can be saved as a prescription.");
        }
        if (p.getFileId() != null) files.deleteById(p.getFileId());
        PrescriptionFile f = new PrescriptionFile();
        f.setContentType(contentType);
        f.setBytes(bytes);
        p.setFileId(files.save(f).getId());
        saved.save(p);
    }

    /** The numbers typed from a saved photo (or typed again to fix them). */
    @Transactional
    public void setNumbers(UUID leadId, UUID id, Numbers n) {
        LeadPrescription p = get(leadId, id);
        p.setSphRight(power(n.sphRight(), 30, "SPH"));
        p.setSphLeft(power(n.sphLeft(), 30, "SPH"));
        p.setCylRight(power(n.cylRight(), 10, "CYL"));
        p.setCylLeft(power(n.cylLeft(), 10, "CYL"));
        p.setAxisRight(axis(n.axisRight()));
        p.setAxisLeft(axis(n.axisLeft()));
        BigDecimal add = power(n.addPower(), 4, "Add");
        if (add != null && add.signum() < 0) throw ApiException.badRequest("BAD_RX", "Add can't be negative.");
        p.setAddPower(add);
        String pd = n.pd() == null || n.pd().isBlank() ? null : n.pd().trim();
        if (pd != null && pd.length() > 16) throw ApiException.badRequest("BAD_RX", "PD is too long.");
        p.setPd(pd);
        // older POS builds send no name: keep what's there
        if (n.patientName() != null) {
            String name = n.patientName().isBlank() ? null : n.patientName().trim();
            if (name != null && name.length() > 120) throw ApiException.badRequest("BAD_NAME", "Name is too long.");
            p.setPatientName(name);
            // the customer's own prescription: its name beats the WhatsApp profile name (often a nickname).
            // Off for a family member's, so the parent isn't renamed to the child.
            if (name != null && Boolean.TRUE.equals(n.renameCustomer())) {
                Lead lead = leads.findById(leadId).orElseThrow();
                lead.setName(name);
                leads.save(lead);
            }
        }
        saved.save(p);
    }

    @Transactional(readOnly = true)
    public PrescriptionFile photo(UUID leadId, UUID id) {
        LeadPrescription p = get(leadId, id);
        if (p.getFileId() == null) throw ApiException.notFound("NO_PHOTO", "No photo saved for this prescription.");
        return files.findById(p.getFileId()).orElseThrow(() -> ApiException.notFound("NO_PHOTO", "Photo not found."));
    }

    @Transactional
    public void delete(UUID leadId, UUID id) {
        LeadPrescription p = get(leadId, id);
        if (p.getFileId() != null) files.deleteById(p.getFileId());
        saved.delete(p);
    }

    private LeadPrescription get(UUID leadId, UUID id) {
        return saved.findById(id).filter(p -> p.getLeadId().equals(leadId))
                .orElseThrow(() -> ApiException.notFound("NO_PRESCRIPTION", "Prescription not found."));
    }

    private static BigDecimal power(BigDecimal v, int max, String what) {
        if (v == null) return null;
        if (v.abs().compareTo(BigDecimal.valueOf(max)) > 0) {
            throw ApiException.badRequest("BAD_RX", what + " must be between -" + max + " and +" + max + ".");
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static Integer axis(Integer v) {
        if (v != null && (v < 0 || v > 180)) throw ApiException.badRequest("BAD_RX", "Axis must be 0 to 180.");
        return v;
    }

    private static Map<String, Object> rx(UUID id, String kind, Instant at, BigDecimal sphRight,
                                          BigDecimal sphLeft, BigDecimal cylRight, BigDecimal cylLeft,
                                          Integer axisRight, Integer axisLeft, BigDecimal addPower, String pd) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("kind", kind); m.put("at", at);
        m.put("sphRight", sphRight); m.put("cylRight", cylRight); m.put("axisRight", axisRight);
        m.put("sphLeft", sphLeft); m.put("cylLeft", cylLeft); m.put("axisLeft", axisLeft);
        m.put("addPower", addPower); m.put("pd", pd);
        return m;
    }

    private static String waId(Lead lead) {
        String id = lead.getWhatsappWaId() != null ? lead.getWhatsappWaId() : lead.getWhatsappNumber();
        return id == null ? null : id.replaceAll("[^0-9]", "");
    }
}
