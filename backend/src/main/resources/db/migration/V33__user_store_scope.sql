-- Each shop's staff login is scoped to that one shop: null means unscoped (every current
-- ADMIN account, plus any future non-shop role) and sees every store; set means that user
-- can only touch that store's data. Enforced in application code (CurrentUser.assertStoreAccess),
-- not a DB constraint, since scoping rules vary by endpoint (e.g. suppliers stay shared).
alter table users add column store_id uuid references stores(id);
create index idx_users_store on users (store_id);
