-- A staff (AGENT) login for the Specskart POS app -- separate from the admin web login,
-- same /api/auth/login the web admin uses. AGENT already has everything the POS app's
-- endpoints need (lens-pricing, lens-sales) per SecurityConfig's /api/admin/** rules.
insert into users (id, created_at, updated_at, email, password_hash, full_name, role, active)
values (
    gen_random_uuid(), now(), now(),
    'pos@specskart.local',
    '$2a$10$8lTSlJlct6QQlhWKDo/Am.7URHAt5CTZYV60IWSJUmIu9Fd.G7y06',
    'Specskart POS',
    'AGENT',
    true
);
