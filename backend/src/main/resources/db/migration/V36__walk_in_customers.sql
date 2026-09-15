-- Walk-in customers: staff show a QR in the POS app, the customer scans it and sends the
-- prefilled WhatsApp message. That inbound message proves they own the number (no OTP template
-- needed, and it opens a free 24h service window) and its wording is their explicit opt-in to
-- offers. A STOP reply clears marketing_opt_in_at again.
create table walk_in_verifications (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    code varchar(12) not null unique,
    store_id uuid references stores(id) on delete set null,
    staff_user_id uuid,
    customer_name varchar(200),
    lead_id uuid references leads(id) on delete set null,
    verified_at timestamp with time zone
);

alter table leads add column marketing_opt_in_at timestamp with time zone;
alter table leads add column home_store_id uuid references stores(id) on delete set null;
