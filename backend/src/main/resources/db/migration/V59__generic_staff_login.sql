-- The V58 staff login becomes a generic shop login, no person's name on it. Same password.
update users set email = 'staff@specskart.local', full_name = 'Staff', updated_at = now()
where id = 'a6cafd7d-bb44-4134-91a9-fc0a1b91fe54'
  and not exists (select 1 from users where email = 'staff@specskart.local');
