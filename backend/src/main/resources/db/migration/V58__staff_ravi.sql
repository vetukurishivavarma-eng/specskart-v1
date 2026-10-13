-- A STAFF (AGENT) login for Ravi: runs the till, no cost price / leads / staff / pricing admin.
-- Portable SQL (no `on conflict`) so FlywayMigrationTest can run it on H2.
insert into users (id, created_at, updated_at, email, password_hash, full_name, role, active)
select 'a6cafd7d-bb44-4134-91a9-fc0a1b91fe54', now(), now(),
       'ravi@specskart.local',
       '$2a$10$m.rptY4rK9gBPopXjwfdXuf3YE4AGmxvscedQEn7kQ/Ak6QyK7KWa',
       'Ravi', 'AGENT', true
where not exists (select 1 from users where email = 'ravi@specskart.local');
