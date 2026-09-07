package com.specskart.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Default provider. No real gateway: it hands back a checkout URL that points at our own
 * mock-pay page, and {@link #verify} succeeds for any reference it issued. Lets the whole
 * checkout → paid → WhatsApp flow run end to end without Flutterwave keys.
 */
@Component
@ConditionalOnProperty(name = "specskart.payments.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentProvider.class);
    private final Set<String> issued = new HashSet<>();

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public synchronized Payment start(PaymentRequest r) {
        String ref = "mock-" + r.orderNo();
        issued.add(ref);
        log.info("[MOCK-PAY] order {} amount {} {} -> ref {}", r.orderNo(), r.amountMinor(), r.currency(), ref);
        String url = r.redirectUrl() + (r.redirectUrl().contains("?") ? "&" : "?") + "mockPaid=1";
        return new Payment(ref, url);
    }

    @Override
    public synchronized boolean verify(String providerRef) {
        return providerRef != null && (issued.contains(providerRef) || providerRef.startsWith("mock-"));
    }
}
