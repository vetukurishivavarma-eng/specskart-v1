-- One-off: no known-working password exists for the pos-admin@specskart.local test account
-- (created in V31 with a hash nobody kept the plaintext for), and it's needed to assign
-- existing staff to their shops through the app's own API rather than hand-editing rows.
-- Resets it to a known temp password (Specskart-Temp-9214!); change it from the app after.
update users
set password_hash = '$2a$10$k1yn.f0EHvL39Pdy/ztEt.E9oeJ09G9npqeOBFynyd6NxiTtmuw5m'
where email = 'pos-admin@specskart.local';
