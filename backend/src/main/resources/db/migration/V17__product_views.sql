-- Abandoned-browse recovery: a known lead (came in via a lead-linked cart token) looks at a
-- product and doesn't buy -> one WhatsApp nudge for that specific product a few hours later.
create table product_views (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    lead_id uuid not null references leads(id) on delete cascade,
    product_id uuid not null references products(id) on delete cascade,
    viewed_at timestamp with time zone not null,
    notified_at timestamp with time zone,
    constraint uq_product_view unique (lead_id, product_id)
);
create index idx_product_view_pending on product_views (notified_at, viewed_at);
