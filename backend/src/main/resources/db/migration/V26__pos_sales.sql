-- Specskart POS, phase 1 continued: the actual till sale. Line items are relational (not a
-- JSON blob) so reporting is plain SQL; a client-supplied idempotency key means a queued
-- offline sale replayed after a lost response can't be recorded twice.
create table receipt_counters (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    store_id uuid not null references stores(id) on delete cascade,
    sale_day varchar(8) not null, -- local date as YYYYMMDD
    sequence int not null default 0,
    unique (store_id, sale_day)
);

create table pos_sales (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    store_id uuid not null references stores(id) on delete cascade,
    receipt_number varchar(40) not null unique,
    sale_type varchar(20) not null default 'SALE', -- SALE | REFUND
    status varchar(20) not null default 'COMPLETED', -- COMPLETED | VOIDED
    subtotal_minor bigint not null default 0,
    discount_minor bigint not null default 0,
    total_minor bigint not null default 0,
    cashier_id uuid references users(id) on delete set null,
    cashier_name varchar(200) not null default '',
    customer_name varchar(200),
    customer_phone varchar(40),
    notes varchar(500) not null default '',
    client_reference varchar(80) unique,
    reverses_id uuid references pos_sales(id) on delete set null,
    voided_at timestamp with time zone,
    void_reason varchar(200)
);
create index idx_pos_sale_store on pos_sales (store_id, created_at);

create table pos_sale_items (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    sale_id uuid not null references pos_sales(id) on delete cascade,
    product_id uuid references products(id) on delete set null,
    product_name varchar(200) not null, -- denormalised: survives the product being renamed
    sku varchar(64) not null default '',
    quantity int not null,
    unit_price_minor bigint not null,
    discount_minor bigint not null default 0,
    line_total_minor bigint not null
);
create index idx_pos_sale_item_sale on pos_sale_items (sale_id);

create table pos_payments (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    sale_id uuid not null references pos_sales(id) on delete cascade,
    method varchar(20) not null, -- CASH | CARD | MOBILE
    amount_minor bigint not null,
    reference varchar(80)
);
create index idx_pos_payment_sale on pos_payments (sale_id);

create table stock_movements (
    id uuid primary key,
    created_at timestamp with time zone not null,
    store_id uuid not null references stores(id) on delete cascade,
    product_id uuid not null references products(id) on delete cascade,
    movement_type varchar(20) not null, -- PURCHASE | SALE | ADJUSTMENT | TRANSFER_IN | TRANSFER_OUT | REFUND
    quantity int not null, -- signed: negative for sales/transfers out
    balance int not null,
    reference varchar(80),
    note varchar(200),
    user_id uuid references users(id) on delete set null
);
create index idx_stock_movement_store on stock_movements (store_id, created_at);
