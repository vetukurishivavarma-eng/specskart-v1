package com.specskart.catalog;

import com.specskart.config.AppProperties;
import com.specskart.whatsapp.WhatsAppProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Every 5 min: for each unfired "notify me" request whose product is now buyable
 * (ACTIVE, in stock, past its drop time), send one WhatsApp and mark it done.
 */
@Component
class StockAlertJob {

    private static final Logger log = LoggerFactory.getLogger(StockAlertJob.class);

    private final StockAlertRepository alerts;
    private final ProductRepository products;
    private final WhatsAppProvider whatsapp;
    private final AppProperties props;

    StockAlertJob(StockAlertRepository alerts, ProductRepository products, WhatsAppProvider whatsapp, AppProperties props) {
        this.alerts = alerts;
        this.products = products;
        this.whatsapp = whatsapp;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @Transactional
    public void run() {
        for (StockAlert alert : alerts.findByNotifiedAtIsNull()) {
            Product p = products.findById(alert.getProductId()).orElse(null);
            if (p == null) { alerts.delete(alert); continue; }
            if (!p.isActive() || !p.inStock() || p.isUpcoming()) continue;

            String msg = "Back in stock 👓\n*" + p.getName() + "* — "
                    + money(p.getPriceMinor(), p.getCurrency())
                    + "\n" + props.frontendBaseUrl() + "/store/" + p.getSlug();
            try {
                // ponytail: plain text — only delivers inside the 24h window. Swap to an approved
                // template (like the follow-up one) if alerts routinely land outside it.
                whatsapp.sendText(alert.getWaId(), msg);
                alert.setNotifiedAt(Instant.now());
                alerts.save(alert);
                log.info("back-in-stock alert sent for {}", p.getSlug());
            } catch (Exception e) {
                log.warn("back-in-stock alert failed for {}: {}", p.getSlug(), e.getMessage());
            }
        }
    }

    private static String money(long minor, String currency) {
        String n = String.format("%,.2f", minor / 100.0);
        return "ZMW".equals(currency) ? "K" + n : currency + " " + n;
    }
}
