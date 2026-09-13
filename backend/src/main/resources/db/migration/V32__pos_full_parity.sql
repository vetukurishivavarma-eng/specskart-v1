-- Remaining NG POS feature parity: cost price (for profit analytics), refunds (distinct from
-- a full void), and app releases (forced-update gate for the Specskart POS app).
alter table products add column cost_price_minor bigint not null default 0;

-- pos_sales already models a refund via sale_type/reverses_id (see V26) -- no schema change
-- needed there, just the service logic (SaleService.refund()).

create table app_releases (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    platform varchar(20) not null default 'android',
    version varchar(20) not null,
    build_number int not null,
    minimum_build int not null default 0,
    download_url varchar(500) not null,
    notes varchar(1000) not null default '',
    mandatory boolean not null default false,
    active boolean not null default true,
    published_at timestamp with time zone not null,
    unique (platform, build_number)
);
create index idx_app_release_active on app_releases (platform, active, build_number);
