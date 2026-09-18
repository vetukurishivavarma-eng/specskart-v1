package com.specskart.order;

import com.specskart.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Flutterwave charge webhook. Authenticated by the verif-hash header, then re-verified with the API. */
@RestController
@RequestMapping("/api/webhooks/payment")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final CheckoutService checkout;
    private final com.specskart.lens.LensInquiryService lensInquiries;
    private final AppProperties props;

    public PaymentWebhookController(CheckoutService checkout,
                                    com.specskart.lens.LensInquiryService lensInquiries,
                                    AppProperties props) {
        this.checkout = checkout;
        this.lensInquiries = lensInquiries;
        this.props = props;
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> receive(@RequestHeader(value = "verif-hash", required = false) String hash,
                                        @RequestBody Map<String, Object> body) {
        String expected = props.payments().flutterwave() == null ? null
                : props.payments().flutterwave().secretHash();
        if (expected != null && !expected.isBlank() && !expected.equals(hash)) {
            log.warn("payment webhook rejected: bad verif-hash");
            return ResponseEntity.status(401).build();
        }
        Object dataObj = body.get("data");
        String txRef = dataObj instanceof Map<?, ?> data ? String.valueOf(((Map<String, Object>) data).get("tx_ref")) : null;
        if (txRef == null || "null".equals(txRef)) {
            log.warn("payment webhook: no tx_ref in {}", body.keySet());
            return ResponseEntity.ok().build();
        }
        try {
            // One gateway, two kinds of order. A lens tx_ref is prefixed so it never reaches
            // CheckoutService, which would only fail to find an Order for it.
            if (com.specskart.lens.LensInquiryService.isLensRef(txRef)) lensInquiries.confirmPayment(txRef);
            else checkout.confirmPayment(txRef);
        } catch (Exception e) {
            log.error("payment webhook confirm {} failed: {}", txRef, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
