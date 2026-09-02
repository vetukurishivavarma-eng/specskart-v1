package com.specskart.attribution;

import com.specskart.campaign.UtmData;

import java.util.Map;

/** Shared helpers for LeadSourceProvider implementations. */
final class ProviderSupport {

    private ProviderSupport() {}

    static String str(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    static UtmData utm(Map<String, Object> raw) {
        return new UtmData(str(raw, "utm_source"), str(raw, "utm_medium"),
                str(raw, "utm_campaign"), str(raw, "utm_content"), str(raw, "utm_term"));
    }
}
