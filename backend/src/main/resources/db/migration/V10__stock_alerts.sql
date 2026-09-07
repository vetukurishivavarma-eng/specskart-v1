-- Back-in-stock alerts: a shopper leaves a WhatsApp number on a sold-out (or not-yet-dropped)
-- product and gets one message when it becomes buyable.
create table stock_alerts (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    product_id uuid not null references products(id) on delete cascade,
    wa_id varchar(40) not null,
    notified_at timestamp with time zone,
    constraint uq_stock_alert unique (product_id, wa_id)
);
create index idx_stock_alert_pending on stock_alerts (notified_at);
