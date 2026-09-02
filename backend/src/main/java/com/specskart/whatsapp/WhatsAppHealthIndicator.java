package com.specskart.whatsapp;

import com.specskart.config.AppProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppHealthIndicator implements HealthIndicator {

    private final WhatsAppProvider provider;
    private final AppProperties props;

    public WhatsAppHealthIndicator(WhatsAppProvider provider, AppProperties props) {
        this.provider = provider;
        this.props = props;
    }

    @Override
    public Health health() {
        boolean configured = "mock".equals(props.whatsapp().provider())
                || (props.whatsapp().accessToken() != null && !props.whatsapp().accessToken().isBlank());
        Health.Builder b = configured ? Health.up() : Health.outOfService();
        return b.withDetail("mode", provider.mode())
                .withDetail("provider", props.whatsapp().provider())
                .build();
    }
}
