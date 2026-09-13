-- Specskart POS, phase 2: suppliers/purchase invoices (how stock enters a shop) and
-- transfers (how it moves between shops).
create table suppliers (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    name varchar(200) not null,
    contact_name varchar(200) not null default '',
    phone varchar(40) not null default '',
    email varchar(200) not null default '',
    address varchar(400) not null default '',
    notes varchar(500) not null default '',
    active boolean not null default true,
    unique (name)
);

-- A delivery from a supplier, priced. Posting one puts stock on the shelf (stock_movements
-- PURCHASE rows) and records the money owed in the same transaction.
create table supplier_invoices (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    supplier_id uuid not null references suppliers(id) on delete restrict,
    store_id uuid not null references stores(id) on delete cascade,
    invoice_number varchar(80) not null,
    invoice_date date not null,
    due_date date,
    subtotal_minor bigint not null default 0,
    other_charges_minor bigint not null default 0,
    total_minor bigint not null default 0,
    amount_paid_minor bigint not null default 0,
    status varchar(20) not null default 'UNPAID', -- UNPAID | PARTIAL | PAID
    notes varchar(500) not null default '',
    created_by_id uuid references users(id) on delete set null,
    created_by_name varchar(200) not null default '',
    unique (supplier_id, invoice_number)
);
create index idx_supplier_invoice_status on supplier_invoices (status);
create index idx_supplier_invoice_store on supplier_invoices (store_id, invoice_date);

create table supplier_invoice_items (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    invoice_id uuid not null references supplier_invoices(id) on delete cascade,
    product_id uuid references products(id) on delete set null,
    product_name varchar(200) not null,
    sku varchar(64) not null default '',
    quantity int not null,
    unit_cost_minor bigint not null,
    line_total_minor bigint not null
);
create index idx_supplier_invoice_item_invoice on supplier_invoice_items (invoice_id);

create table supplier_payments (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    invoice_id uuid not null references supplier_invoices(id) on delete cascade,
    amount_minor bigint not null,
    method varchar(20) not null default 'CASH', -- CASH | BANK_TRANSFER | MOBILE | CHEQUE | CARD | OTHER
    reference varchar(80) not null default '',
    note varchar(200) not null default '',
    paid_at timestamp with time zone not null,
    user_id uuid references users(id) on delete set null,
    user_name varchar(200) not null default ''
);
create index idx_supplier_payment_invoice on supplier_payments (invoice_id);

create table transfers (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    reference varchar(40) not null unique,
    from_store_id uuid references stores(id) on delete set null,
    to_store_id uuid references stores(id) on delete set null,
    status varchar(20) not null default 'PENDING', -- PENDING | IN_TRANSIT | COMPLETED | CANCELLED
    notes varchar(500) not null default '',
    source_transfer_id uuid references transfers(id) on delete set null
);
create index idx_transfer_source on transfers (source_transfer_id);

create table transfer_items (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    transfer_id uuid not null references transfers(id) on delete cascade,
    product_id uuid not null references products(id) on delete cascade,
    quantity int not null
);
create index idx_transfer_item_transfer on transfer_items (transfer_id);
