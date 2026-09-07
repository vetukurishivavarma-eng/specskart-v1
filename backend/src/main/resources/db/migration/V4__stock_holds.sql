-- Movie-ticket style inventory holds. `products.stock_qty` now means "available to sell
-- right now": adding to a cart atomically decrements it and stamps the cart line with a
-- hold expiry; an expired hold (or a removed line) puts the unit back.

alter table cart_items add column held_until timestamp with time zone;
create index idx_cart_item_held on cart_items (held_until);
