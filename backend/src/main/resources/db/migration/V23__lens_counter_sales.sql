-- Specskart POS app: staff bill a lens order at the counter, either finishing a web-originated
-- inquiry (status SUBMITTED, the customer already verified over WhatsApp) or a fresh walk-in
-- who never touched the website at all. Reuses lens_inquiries rather than a parallel sales
-- table -- a walk-in is just an inquiry staff verified in person instead of by WhatsApp link.
alter table lens_inquiries add column walk_in boolean not null default false;
alter table lens_inquiries add column payment_method varchar(20);
alter table lens_inquiries add column sold_by varchar(120);
alter table lens_inquiries add column shop_name varchar(120);
