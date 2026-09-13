-- An ADMIN test account for the Specskart POS app -- separate from pos@specskart.local
-- (AGENT), so staff-management and other admin-only screens can be tested. Same pattern as
-- V24, needed for the same reason: no known-working admin credentials exist to call
-- AdminUserController's create endpoint with.
insert into users (id, created_at, updated_at, email, password_hash, full_name, role, active)
values (
    gen_random_uuid(), now(), now(),
    'pos-admin@specskart.local',
    '$2a$10$IgVTKkm3VWZ4VVnbXWdTxuGDtNFHJmJbveUWF1trzpTrhGX3OZjGW',
    'Specskart POS Admin',
    'ADMIN',
    true
);
