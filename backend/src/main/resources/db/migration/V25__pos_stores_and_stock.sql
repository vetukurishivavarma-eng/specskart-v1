-- Specskart POS, phase 1: multi-shop stock + pricing. Reuses the existing `products` table
-- (frames) rather than a parallel catalog -- SKU/barcode are new columns on it. Stock and
-- price become per-store; `products.stock_qty`/`price_minor` stay as the online-store
-- defaults (a store with no override row falls back to them).
create table stores (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    name varchar(200) not null,
    code varchar(20) not null unique,
    city varchar(100) not null default '',
    active boolean not null default true
);

alter table products add column sku varchar(64);
alter table products add column barcode varchar(64);
-- Plain unique constraint: both Postgres and H2 treat multiple NULLs as distinct, so this
-- already allows any number of SKU-less products while still forbidding duplicate SKUs.
alter table products add constraint uq_product_sku unique (sku);
create index idx_product_barcode on products (barcode);

create table product_inventory (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    store_id uuid not null references stores(id) on delete cascade,
    product_id uuid not null references products(id) on delete cascade,
    quantity int not null default 0,
    reorder_level int not null default 3,
    unique (store_id, product_id)
);
create index idx_inventory_store on product_inventory (store_id);

-- Per-store price override; absent means the product's own price_minor applies.
create table product_store_prices (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    store_id uuid not null references stores(id) on delete cascade,
    product_id uuid not null references products(id) on delete cascade,
    price_minor bigint not null,
    unique (store_id, product_id)
);

-- One till, one account, one device -- same rule NG POS uses, so a password can't run on
-- four phones at once and every sale is attributable to a person, not "whoever holds this".
create table device_sessions (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    user_id uuid not null references users(id) on delete cascade,
    device_id varchar(200) not null,
    device_name varchar(200) not null default '',
    platform varchar(20) not null default 'android',
    app_version varchar(20),
    last_seen_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    revoked_by_id uuid,
    revoked_reason varchar(200)
);
create index idx_device_session_user on device_sessions (user_id);
create index idx_device_session_active on device_sessions (user_id, revoked_at);
