package com.specskart.shared;

/**
 * Normalizes a customer-typed phone number to the digits-only, country-code-first
 * form WhatsApp's Cloud API needs as a recipient id (wa_id). A web checkout field
 * takes whatever the customer types — usually local format with a trunk "0", not
 * the E.164 the API expects — and sending to the wrong digits fails silently
 * (Meta accepts the request, the message just never arrives). No delivery-time
 * error, so this bug reads as "customers only get it sometimes".
 */
public final class PhoneNumbers {

    private PhoneNumbers() {}

    /** @param defaultCountryCode digits only, e.g. "260" for Zambia */
    public static String normalize(String raw, String defaultCountryCode) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;

        boolean hadPlus = raw.trim().startsWith("+");
        if (digits.startsWith("00")) digits = digits.substring(2);

        if (hadPlus || digits.startsWith(defaultCountryCode)) return digits;   // already has a country code
        if (digits.startsWith("0")) return defaultCountryCode + digits.substring(1); // strip the trunk "0"
        if (digits.length() <= 9) return defaultCountryCode + digits;         // bare local number, no trunk 0
        return digits;                                                        // looks international already
    }
}
