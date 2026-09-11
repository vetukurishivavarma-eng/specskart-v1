-- Data fix: web-checkout leads created before this release stored whatever the
-- customer typed as the WhatsApp wa_id verbatim. A 10-digit Zambian mobile typed
-- in local format ("0977123456") is not a valid wa_id (missing the 260 country
-- code, still carrying the trunk "0") — WhatsApp sends to it are accepted by Meta
-- but never delivered, silently. Re-normalize the ones that match that exact
-- shape; anything else is left alone.
update leads
set whatsapp_wa_id = '260' || substring(whatsapp_wa_id from 2)
where whatsapp_wa_id ~ '^0[0-9]{9}$';
