package com.specskart.attribution;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AttributionResolver {

    private final List<LeadSourceProvider> providers;

    public AttributionResolver(List<LeadSourceProvider> providers) {
        this.providers = providers;
    }

    /** Picks the first provider (by @Order) that claims the raw payload. */
    public AttributionContext resolve(Map<String, Object> raw) {
        Map<String, Object> safe = raw == null ? Map.of() : raw;
        return providers.stream()
                .filter(p -> p.supports(safe))
                .findFirst()
                .orElse(providers.get(providers.size() - 1))
                .extract(safe);
    }
}
