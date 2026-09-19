package com.specskart.order;

import com.specskart.lens.LensInquiry;
import com.specskart.lens.LensInquiryRepository;
import com.specskart.lens.LensInquiryService;
import com.specskart.shared.ApiException;
import com.specskart.shared.SignedLinks;
import com.specskart.shared.SimplePdf;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

/**
 * The files a staff WhatsApp alert points at — Meta fetches them from here when it delivers the
 * message. Public (Meta has no login) but every URL carries an expiring HMAC over the resource:
 * prescriptions are health data, and an order number alone is already exposed by order tracking.
 */
@RestController
@RequestMapping("/api/public/staff-docs")
public class StaffDocController {

    public static final Duration TTL = Duration.ofHours(48);

    private final OrderRepository orders;
    private final PrescriptionFileRepository prescriptionFiles;
    private final LensInquiryRepository lensInquiries;
    private final OrderNotificationService notifications;
    private final SignedLinks links;
    private final com.specskart.membership.MembershipService memberships;

    public StaffDocController(OrderRepository orders, PrescriptionFileRepository prescriptionFiles,
                              LensInquiryRepository lensInquiries, OrderNotificationService notifications,
                              SignedLinks links, com.specskart.membership.MembershipService memberships) {
        this.memberships = memberships;
        this.orders = orders;
        this.prescriptionFiles = prescriptionFiles;
        this.lensInquiries = lensInquiries;
        this.notifications = notifications;
        this.links = links;
    }

    @GetMapping("/orders/{orderNo}.pdf")
    public ResponseEntity<byte[]> orderSlip(@PathVariable String orderNo, @RequestParam long exp, @RequestParam String sig) {
        check("orders/" + orderNo, exp, sig);
        return pdf(SimplePdf.render(notifications.staffLines(order(orderNo))), orderNo + ".pdf");
    }

    @GetMapping("/orders/{orderNo}/prescription")
    public ResponseEntity<byte[]> prescription(@PathVariable String orderNo, @RequestParam long exp, @RequestParam String sig) {
        check("orders/" + orderNo + "/prescription", exp, sig);
        Order o = order(orderNo);
        PrescriptionFile f = o.getPrescriptionFileId() == null ? null
                : prescriptionFiles.findById(o.getPrescriptionFileId()).orElse(null);
        if (f == null) throw ApiException.notFound("NO_PRESCRIPTION", "No prescription was uploaded for this order.");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(f.getBytes());
    }

    @GetMapping("/lens/{id}.pdf")
    public ResponseEntity<byte[]> lensSlip(@PathVariable UUID id, @RequestParam long exp, @RequestParam String sig) {
        check("lens/" + id, exp, sig);
        LensInquiry q = lensInquiries.findById(id)
                .orElseThrow(() -> ApiException.notFound("INQUIRY_NOT_FOUND", "No such lens order."));
        return pdf(SimplePdf.render(LensInquiryService.staffLines(q, memberships.discountPercentFor(q.getLeadId()))),
                LensInquiryService.ref(q) + ".pdf");
    }

    private Order order(String orderNo) {
        return orders.findByOrderNo(orderNo)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "No such order."));
    }

    private void check(String resource, long exp, String sig) {
        if (!links.valid(resource, exp, sig)) {
            throw ApiException.forbidden("LINK_EXPIRED", "This link is invalid or has expired.");
        }
    }

    private static ResponseEntity<byte[]> pdf(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
