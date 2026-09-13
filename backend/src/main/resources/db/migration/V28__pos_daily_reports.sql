-- A frozen end-of-day Z-report. Figures could be recomputed from pos_sales, but a snapshot is
-- what reconciles against cash actually counted that night -- it can't drift when an old sale
-- is later voided, and it survives the sales table being archived one day.
create table daily_reports (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    store_id uuid not null references stores(id) on delete cascade,
    report_date varchar(10) not null, -- YYYY-MM-DD, local calendar date
    sale_count int not null default 0,
    gross_total_minor bigint not null default 0,
    cash_total_minor bigint not null default 0,
    card_total_minor bigint not null default 0,
    mobile_total_minor bigint not null default 0,
    top_items varchar(4000) not null default '[]', -- [{name, quantity, total}], best sellers first
    finalized boolean not null default false,
    unique (store_id, report_date)
);
create index idx_daily_report_store on daily_reports (store_id, report_date);
