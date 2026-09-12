-- New client requirement: a lens-only funnel (frames temporarily hidden everywhere, code
-- kept). Ad -> WhatsApp -> "Explore lens" -> site: pick lens type + blue block -> verify
-- WhatsApp number via a magic link -> fill prescription -> hardcoded price -> buy now.
-- The verified number + lens type + blue block are saved (and followed up on) the moment
-- verification succeeds, even if the shopper abandons before finishing the Rx form.
create table lens_inquiries (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,

    lead_id uuid references leads(id) on delete set null,
    verify_token_hash varchar(64) not null,
    phone_raw varchar(40) not null,
    wa_id varchar(40),
    phone_verified_at timestamp with time zone,

    lens_type varchar(20),                 -- CLEAR | PHOTOCHROMATIC
    blue_block boolean not null default false,

    customer_name varchar(200),
    age int,
    gender varchar(20),

    sph_right numeric(4,2),
    sph_left numeric(4,2),
    cyl_right numeric(4,2),
    cyl_left numeric(4,2),
    axis_right int,
    axis_left int,
    add_power numeric(4,2),
    lens_structure varchar(20),            -- BIFOCAL | PROGRESSIVE, only when add_power > 0
    special_axis boolean not null default false,

    price_minor bigint,
    currency varchar(8) not null default 'ZMW',
    status varchar(20) not null default 'DRAFT',   -- DRAFT | VERIFIED | PRICED | SUBMITTED
    nudged_at timestamp with time zone
);
create unique index uq_lens_inquiry_token on lens_inquiries (verify_token_hash);
create index idx_lens_inquiry_followup on lens_inquiries (status, nudged_at, created_at);
