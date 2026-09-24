-- Which ad sent this shopper here. Captured on the /lens page from the landing URL
-- (fbclid / ttclid / gclid / utm_*) and parked on the inquiry, because the lead row it
-- belongs to is only created once the number verifies -- which happens in a different
-- request, often in WhatsApp's in-app browser where the original page's storage is gone.
alter table lens_inquiries add column attribution text;
