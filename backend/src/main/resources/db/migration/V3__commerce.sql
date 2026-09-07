-- Phase 1 e-commerce: catalog, promo codes, carts, orders. Money is stored in
-- minor units (ngwee) as bigint. All customer-facing flow is automated; the admin
-- portal manages products / orders / promos / storefront copy.

create table products (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    slug varchar(80) not null unique,
    name varchar(200) not null,
    description varchar(4000),
    frame_category_code varchar(40),
    material varchar(60),
    colour varchar(60),
    gender varchar(20) not null default 'UNISEX',
    price_minor bigint not null,
    compare_at_minor bigint,
    currency varchar(3) not null default 'ZMW',
    stock_qty int not null default 0,
    lensable boolean not null default false,
    status varchar(20) not null default 'DRAFT',
    featured boolean not null default false
);
create index idx_product_status on products (status);
create index idx_product_category on products (frame_category_code);

create table product_images (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    product_id uuid not null references products (id) on delete cascade,
    url varchar(1024) not null,
    alt varchar(255),
    sort int not null default 0
);
create index idx_product_image_product on product_images (product_id);

create table promo_codes (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    code varchar(40) not null unique,
    discount_type varchar(10) not null default 'PERCENT',
    discount_value int not null,
    min_subtotal_minor bigint not null default 0,
    max_redemptions int,
    redeemed_count int not null default 0,
    expires_at timestamp with time zone,
    auto_issue boolean not null default false,
    active boolean not null default true
);

create table carts (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    token varchar(64) not null unique,
    lead_id uuid references leads (id) on delete set null,
    promo_code varchar(40),
    nudged_at timestamp with time zone,
    ordered_at timestamp with time zone
);
create index idx_cart_lead on carts (lead_id);
create index idx_cart_updated on carts (updated_at);

create table cart_items (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    cart_id uuid not null references carts (id) on delete cascade,
    product_id uuid not null references products (id),
    qty int not null,
    unit_price_minor bigint not null,
    constraint uq_cart_product unique (cart_id, product_id)
);

create table orders (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    order_no varchar(20) not null unique,
    lead_id uuid references leads (id) on delete set null,
    status varchar(20) not null,
    customer_name varchar(200) not null,
    customer_phone varchar(30) not null,
    customer_email varchar(200),
    ship_address varchar(1000) not null,
    ship_city varchar(120) not null,
    subtotal_minor bigint not null,
    discount_minor bigint not null default 0,
    shipping_minor bigint not null default 0,
    total_minor bigint not null,
    currency varchar(3) not null default 'ZMW',
    promo_code varchar(40),
    payment_provider varchar(20),
    payment_ref varchar(120),
    paid_at timestamp with time zone
);
create index idx_order_lead on orders (lead_id);
create index idx_order_status on orders (status);

create table order_items (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    order_id uuid not null references orders (id) on delete cascade,
    product_id uuid,
    product_name varchar(200) not null,
    product_slug varchar(80),
    qty int not null,
    unit_price_minor bigint not null
);
create index idx_order_item_order on order_items (order_id);

create table order_events (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    order_id uuid not null references orders (id) on delete cascade,
    status varchar(20) not null,
    note varchar(500)
);
create index idx_order_event_order on order_events (order_id);

create table store_config (
    id int primary key,
    hero_title varchar(200),
    hero_subtitle varchar(500),
    hero_image_url varchar(1024),
    shipping_fee_minor bigint not null default 0,
    free_shipping_over_minor bigint,
    delivery_eta varchar(120) not null default '2-4 working days',
    currency varchar(3) not null default 'ZMW'
);
