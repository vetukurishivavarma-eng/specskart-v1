package com.specskart.payment;

import com.specskart.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Flutterwave Standard checkout (cards + MTN / Airtel mobile money).
 * tx_ref = our order number; that is the provider reference we verify later.
 */
@Component
@ConditionalOnProperty(name = "specskart.payments.provider", havingValue = "flutterwave")
public class FlutterwaveProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(FlutterwaveProvider.class);

    private final RestClient http;
    private final AppProperties props;

    public FlutterwaveProvider(RestClient http, AppProperties props) {
        this.http = http;
        this.props = props;
        if (fw().secretKey() == null || fw().secretKey().isBlank()) {
            throw new IllegalStateException("payments.provider=flutterwave requires FLW_SECRET_KEY");
        }
    }

    private AppProperties.Flutterwave fw() {
        return props.payments().flutterwave();
    }

    @Override
    public String name() {
        return "flutterwave";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Payment start(PaymentRequest r) {
        BigDecimal amount = new BigDecimal(r.amountMinor()).movePointLeft(2); // minor units -> major
        Map<String, Object> body = Map.of(
                "tx_ref", r.orderNo(),
                "amount", amount.toPlainString(),
                "currency", r.currency(),
                "redirect_url", r.redirectUrl(),
                "customer", Map.of(
                        "email", r.customerEmail() != null ? r.customerEmail() : "orders@specskart.local",
                        "name", r.customerName(),
                        "phonenumber", r.customerPhone()),
                "customizations", Map.of("title", "Specskart", "description", "Order " + r.orderNo()));

        Map<String, Object> res = http.post()
                .uri(fw().baseUrl() + "/v3/payments")
                .header("Authorization", "Bearer " + fw().secretKey())
                .body(body)
                .retrieve()
                .body(Map.class);

        Map<String, Object> data = res == null ? null : (Map<String, Object>) res.get("data");
        if (data == null || data.get("link") == null) {
            throw new IllegalStateException("Flutterwave did not return a checkout link: " + res);
        }
        return new Payment(r.orderNo(), String.valueOf(data.get("link")));
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean verify(String providerRef) {
        try {
            Map<String, Object> res = http.get()
                    .uri(fw().baseUrl() + "/v3/transactions/verify_by_reference?tx_ref=" + providerRef)
                    .header("Authorization", "Bearer " + fw().secretKey())
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = res == null ? null : (Map<String, Object>) res.get("data");
            boolean ok = data != null && "successful".equalsIgnoreCase(String.valueOf(data.get("status")));
            if (!ok) log.warn("Flutterwave verify {} -> {}", providerRef, res);
            return ok;
        } catch (Exception e) {
            log.error("Flutterwave verify {} failed: {}", providerRef, e.getMessage());
            return false;
        }
    }
}
