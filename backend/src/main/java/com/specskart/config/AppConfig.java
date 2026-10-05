package com.specskart.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    @Bean
    RestClient restClient() {
        return RestClient.create();
    }

    // Off on a second instance sharing the same DB (SPECSKART_SCHEDULING_ENABLED=false),
    // so follow-up/cart/stock jobs don't run twice and double-message customers.
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "specskart.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
