-- Star ratings, captured with zero extra friction: the post-purchase WhatsApp already asks
-- "how was it", a bare 1-5 reply now gets recorded against every product in that order.
-- Numbers only, no free-text comment — nothing to moderate.
create table reviews (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    product_id uuid not null references products(id) on delete cascade,
    lead_id uuid not null references leads(id) on delete cascade,
    order_id uuid not null references orders(id) on delete cascade,
    rating int not null,
    constraint uq_review unique (order_id, product_id)
);
create index idx_review_product on reviews (product_id);
