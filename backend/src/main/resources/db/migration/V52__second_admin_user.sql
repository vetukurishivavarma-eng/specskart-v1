-- A second ADMIN login for the owner (web admin + Specskart POS), alongside admin@specskart.local.
insert into users (id, created_at, updated_at, email, password_hash, full_name, role, active)
values (
    gen_random_uuid(), now(), now(),
    'shiva@specskart.local',
    '$2a$10$3Lo471R6Y3QRF8r8rpCtJO.Sfd0EDdsLaVy3uIrebxY5CxX0HwcvC',
    'Shiva',
    'ADMIN',
    true
)
on conflict (email) do nothing;
