-- The client's own price list ("Specs Kart Prices 2025 - Special Prices") replaces the
-- placeholder base + add-on model. LensPricing picks one row by lens type, blue-block,
-- bifocal/progressive and the SPH / CYL / Add band; every price stays editable from the
-- Specskart POS app (More -> Lens pricing, admins only).
delete from lens_pricing_options;
alter table lens_pricing_options add column sort_order int not null default 0;

insert into lens_pricing_options (id, code, label, price_minor, in_stock, sort_order, created_at, updated_at) values
    (gen_random_uuid(), 'SV_CLEAR',    'Clear Single Vision · SPH Plano–±4.00',                32000, true,  1, now(), now()),
    (gen_random_uuid(), 'SV_CLEAR_BB', 'Clear BB Single Vision · SPH Plano–±4.00',             36000, true,  2, now(), now()),
    (gen_random_uuid(), 'PG_4',        'Colormatic · SPH Plano–±4.00',                         45000, true,  3, now(), now()),
    (gen_random_uuid(), 'PG_8',        'Colormatic · SPH ±4.25–±8.00',                         63000, true,  4, now(), now()),
    (gen_random_uuid(), 'PG_10',       'Colormatic · SPH ±8.25–±10.00',                        99900, true,  5, now(), now()),
    (gen_random_uuid(), 'PG_BB_4',     'Colormatic BB · SPH Plano–±4.00',                      63000, true,  6, now(), now()),
    (gen_random_uuid(), 'PG_BB_8',     'Colormatic BB · SPH ±4.25–±8.00',                      99900, true,  7, now(), now()),
    (gen_random_uuid(), 'PG_BB_10',    'Colormatic BB · SPH ±8.25–±10.00',                    117000, true,  8, now(), now()),
    (gen_random_uuid(), 'BF_2',        'Colormatic BF · SPH Plano–±2.00, Add +1–+3',           63000, true,  9, now(), now()),
    (gen_random_uuid(), 'BF_3',        'Colormatic BF · SPH ±2.25–±3.00, Add +1–+3',           81000, true, 10, now(), now()),
    (gen_random_uuid(), 'BF_BB_2',     'Colormatic BF/BB · SPH Plano–±2.00, Add +1–+3',        95000, true, 11, now(), now()),
    (gen_random_uuid(), 'BF_BB_3',     'Colormatic BF/BB · SPH ±2.25–±3.00, Add +1–+3',       131000, true, 12, now(), now()),
    (gen_random_uuid(), 'PROG',        'Colormatic Prog · SPH Plano–±3.00, Add +1–+3',        113000, true, 13, now(), now()),
    (gen_random_uuid(), 'PROG_BB_2',   'Colormatic Prog BB · SPH Plano–±2.00, Add +1–+2',     135000, true, 14, now(), now()),
    (gen_random_uuid(), 'PROG_BB_3',   'Colormatic Prog BB · SPH ±2.25–±3.00, Add +1–+3',     153000, true, 15, now(), now()),
    (gen_random_uuid(), 'CYL_EXTRA',   'Cyl ±2.25–±4.00 — added to the base price',            20000, true, 16, now(), now()),
    (gen_random_uuid(), 'RX_BF',       'RX D B/F PG HMC Cylinder (15–21 days)',               150000, true, 17, now(), now()),
    (gen_random_uuid(), 'RX_BF_BB',    'RX D B/F PG BB Cylinder (15–21 days)',                180000, true, 18, now(), now()),
    (gen_random_uuid(), 'RX_PROG',     'RX PROG PG HMC Cylinder (15–21 days)',                230000, true, 19, now(), now()),
    (gen_random_uuid(), 'RX_PROG_BB',  'RX PROG PG BB Cylinder (15–21 days)',                 270000, true, 20, now(), now());
