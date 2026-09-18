-- Online payment for web lens orders. Until now the lens funnel had no gateway hop at all:
-- an order was only ever paid when staff picked CASH/CARD/MOBILE at handover, so a customer
-- who wanted to pay up front couldn't. Reuses the same Flutterwave provider the frame
-- storefront already goes through; tx_ref is "LENS-<inquiry id>".
alter table lens_inquiries add column payment_ref varchar(255);
alter table lens_inquiries add column paid_at timestamp with time zone;
