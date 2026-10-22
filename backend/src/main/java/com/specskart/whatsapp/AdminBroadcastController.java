package com.specskart.whatsapp;

import com.specskart.config.AppProperties;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.specskart.shared.ApiException;

import java.util.List;
import java.util.UUID;

/** Admin: send a one-off WhatsApp broadcast (new arrivals, a sale) to a lead segment.
 *  Always requires an approved template and is capped per call — see BroadcastService. */
@RestController
@RequestMapping("/api/admin/whatsapp/broadcast")
public class AdminBroadcastController {

    /** freeText: the template's words as plain text, sent instead (free) to customers whose chat is open. */
    public record Request(String faceShape, Boolean excludeConverted, Boolean optedInOnly, String templateName,
                          String headerImageUrl, List<String> bodyParams, Integer limit, String freeText,
                          List<UUID> leadIds, Boolean localOnly) {}
    public record PreviewResponse(int eligible) {}

    private final BroadcastService broadcast;
    private final AppProperties props;

    public AdminBroadcastController(BroadcastService broadcast, AppProperties props) {
        this.broadcast = broadcast;
        this.props = props;
    }

    /** Count how many leads a filter would reach — call this before send(). */
    @PostMapping("/preview")
    public PreviewResponse preview(@RequestBody Request req) {
        return new PreviewResponse(broadcast.previewCount(filterOf(req), req.templateName()));
    }

    @PostMapping("/send")
    public BroadcastService.SendResult send(@RequestBody Request req) {
        return broadcast.send(filterOf(req), req.templateName(), props.whatsapp().followUpTemplateLang(),
                req.headerImageUrl(), req.bodyParams() == null ? List.of() : req.bodyParams(),
                req.limit() == null ? 200 : req.limit(), req.freeText());
    }

    /** leadIds: only these customers (picked in the app); null = everyone. */
    public record TextRequest(String text, Boolean excludeConverted, Integer limit, List<UUID> leadIds, Boolean localOnly) {}

    /** A message staff type: goes only to customers who messaged in the last 24h (WhatsApp's rule; free). */
    @PostMapping("/text/preview")
    public PreviewResponse previewText(@RequestBody TextRequest req) {
        return new PreviewResponse(broadcast.previewText(textFilter(req), req.text()));
    }

    @PostMapping("/text/send")
    public BroadcastService.SendResult sendText(@RequestBody TextRequest req) {
        return broadcast.sendText(textFilter(req), req.text(), req.limit() == null ? 200 : req.limit());
    }

    private BroadcastService.Filter textFilter(TextRequest req) {
        return new BroadcastService.Filter(null, Boolean.TRUE.equals(req.excludeConverted()), false, picked(req.leadIds()),
                Boolean.TRUE.equals(req.localOnly()) ? props.whatsapp().defaultCountryCode() : null);
    }

    private BroadcastService.Filter filterOf(Request req) {
        return new BroadcastService.Filter(
                req.faceShape() == null || req.faceShape().isBlank() ? null : req.faceShape(),
                Boolean.TRUE.equals(req.excludeConverted()),
                Boolean.TRUE.equals(req.optedInOnly()),
                picked(req.leadIds()),
                Boolean.TRUE.equals(req.localOnly()) ? props.whatsapp().defaultCountryCode() : null);
    }

    /** Picked customers are capped like a send, so a bad client can't pass the whole lead base. */
    private static List<UUID> picked(List<UUID> ids) {
        if (ids == null) return null;
        if (ids.isEmpty()) throw ApiException.badRequest("PICK_CUSTOMERS", "Pick at least one customer.");
        if (ids.size() > 200) throw ApiException.badRequest("TOO_MANY_PICKED", "Pick 200 customers or fewer.");
        return ids;
    }
}
