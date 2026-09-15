-- Shops get a map pin. An active pinned shop sells online: its shelf counts toward the website's
-- stock and web orders ship from the nearest pinned shop holding the frames. Checkout asks the
-- customer's browser for their location (with permission); that pin also goes on the staff alert
-- so the courier can find the door.
alter table stores add column latitude double precision;
alter table stores add column longitude double precision;

alter table orders add column delivery_lat double precision;
alter table orders add column delivery_lng double precision;
alter table orders add column fulfil_store_id uuid references stores(id) on delete set null;

-- a split order (no single shop had everything) records each line's shop
alter table order_items add column store_id uuid references stores(id) on delete set null;
