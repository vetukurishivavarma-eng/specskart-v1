-- Staff-editable lens pricing, replacing the hardcoded LensPricing constants.
-- Seeded with the same values so nothing changes price until someone edits it
-- from the Specskart POS app (or admin).
create table lens_pricing_options (
    id uuid primary key,
    code varchar(32) not null unique,
    label varchar(64) not null,
    price_minor bigint not null,
    in_stock boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

insert into lens_pricing_options (id, code, label, price_minor, in_stock, created_at, updated_at) values
    (gen_random_uuid(), 'CLEAR', 'Clear lens', 25000, true, now(), now()),
    (gen_random_uuid(), 'PHOTOCHROMATIC', 'Photochromatic lens', 45000, true, now(), now()),
    (gen_random_uuid(), 'BLUE_BLOCK', 'Blue-light block add-on', 8000, true, now(), now()),
    (gen_random_uuid(), 'BIFOCAL', 'Bifocal add-on', 15000, true, now(), now()),
    (gen_random_uuid(), 'PROGRESSIVE', 'Progressive add-on', 35000, true, now(), now());
