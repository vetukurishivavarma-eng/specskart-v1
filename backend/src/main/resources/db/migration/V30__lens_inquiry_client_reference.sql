-- Walk-in lens sales had no idempotency key, unlike pos_sales.client_reference -- a retry
-- after a dropped connection (the Specskart POS app's offline queue) could double-sell.
alter table lens_inquiries add column client_reference varchar(80);
alter table lens_inquiries add constraint uq_lens_inquiry_client_reference unique (client_reference);
