package com.specskart.attribution;

import java.util.Map;

/**
 * Isolated per-platform attribution adapter. Raw input is provider-shaped key/values
 * (query params, WhatsApp referral object, lead-form payload).
 */
public interface LeadSourceProvider {
    boolean supports(Map<String, Object> raw);
    AttributionContext extract(Map<String, Object> raw);
}
