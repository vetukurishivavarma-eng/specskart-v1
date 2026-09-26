package com.specskart.whatsapp;

import com.specskart.shared.ApiException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** The WhatsApp FAQ list, edited from the POS app. Writes are admin-only (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/faqs")
public class AdminFaqController {

    public record FaqView(UUID id, String title, String question, String answer, int sortOrder) {}
    public record FaqRequest(String title, String question, String answer, Integer sortOrder) {}

    private final FaqRepository faqs;

    public AdminFaqController(FaqRepository faqs) {
        this.faqs = faqs;
    }

    @GetMapping
    public List<FaqView> list() {
        return faqs.findAllByOrderBySortOrderAscCreatedAtAsc().stream().map(AdminFaqController::view).toList();
    }

    @PostMapping
    public FaqView create(@RequestBody FaqRequest req) {
        Faq f = new Faq();
        // New ones go to the bottom unless told otherwise.
        f.setSortOrder(faqs.findAll().stream().mapToInt(Faq::getSortOrder).max().orElse(0) + 10);
        return view(faqs.save(apply(f, req)));
    }

    @PatchMapping("/{id}")
    public FaqView update(@PathVariable UUID id, @RequestBody FaqRequest req) {
        return view(faqs.save(apply(find(id), req)));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        faqs.delete(find(id));
    }

    private Faq find(UUID id) {
        return faqs.findById(id).orElseThrow(() -> ApiException.notFound("FAQ_NOT_FOUND", "No such FAQ."));
    }

    private static Faq apply(Faq f, FaqRequest req) {
        if (req.title() != null) f.setTitle(req.title().trim());
        if (req.question() != null) f.setQuestion(req.question().trim());
        if (req.answer() != null) f.setAnswer(req.answer().trim());
        if (req.sortOrder() != null) f.setSortOrder(req.sortOrder());
        check(f.getTitle(), 24, "Title");
        check(f.getQuestion(), 72, "Question");
        check(f.getAnswer(), 1000, "Answer");
        return f;
    }

    // WhatsApp rejects the whole list on an overlong row, so the limits are enforced here, not trimmed.
    private static void check(String v, int max, String field) {
        if (v == null || v.isBlank()) throw ApiException.badRequest("FAQ_INVALID", field + " is required.");
        if (v.length() > max) throw ApiException.badRequest("FAQ_INVALID", field + " must be " + max + " characters or fewer.");
    }

    private static FaqView view(Faq f) {
        return new FaqView(f.getId(), f.getTitle(), f.getQuestion(), f.getAnswer(), f.getSortOrder());
    }
}
