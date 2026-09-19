-- "Specskart Care": a paid annual membership that takes a standing percentage off every lens
-- order. One row per purchase attempt; a row only counts once paid_at is set, so an abandoned
-- checkout leaves a harmless record rather than a free membership.
--
-- No plans table: there is exactly one plan and its price and discount live in code
-- (MembershipService), next to the kill switch that turns the whole feature off.
create table memberships (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    lead_id uuid not null references leads(id) on delete cascade,
    price_minor bigint not null,
    currency varchar(8) not null default 'ZMW',
    discount_percent int not null,
    payment_ref varchar(255),
    paid_at timestamp with time zone,
    expires_at timestamp with time zone
);

-- The membership lookup runs on every lens quote, so it wants an index.
create index idx_membership_lead on memberships (lead_id);
