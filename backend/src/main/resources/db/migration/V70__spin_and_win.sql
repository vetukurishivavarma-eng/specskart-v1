-- Spin & Win: one spin per WhatsApp customer (unique lead_id), prize redeemed at the shop.
create table spin_prizes (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    label varchar(80) not null,
    weight int not null check (weight >= 0),
    active boolean not null default true,
    sort_order int not null default 0
);

create table spins (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    lead_id uuid not null unique references leads(id) on delete cascade,
    prize_label varchar(80) not null,
    code varchar(20) not null unique,
    expires_at timestamp with time zone not null,
    redeemed_at timestamp with time zone,
    redeemed_by varchar(255)
);
