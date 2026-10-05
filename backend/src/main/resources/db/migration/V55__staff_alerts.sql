-- Every staff alert (new web order / new lens order) per staff number, so one that Meta rejects
-- or never delivers is retried instead of lost. The rendered message is stored with the row so a
-- retry hours later needs nothing but this table.
create table staff_alerts (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    kind varchar(16) not null,            -- ORDER | LENS
    ref_id uuid not null,                 -- orders.id / lens_inquiries.id
    ref_no varchar(40) not null,          -- what staff see, e.g. SK-1234 / LENS-ABCD1234
    recipient varchar(32) not null,
    status varchar(16) not null,          -- PENDING | SENT | DELIVERED | GAVE_UP
    attempts int not null,
    last_path varchar(16),                -- TEMPLATE | PLAIN, so a retry tries the other one
    wamid varchar(128),
    last_error varchar(500),
    next_attempt_at timestamp with time zone,
    body varchar(8000) not null,
    pdf_url varchar(1000),
    pdf_name varchar(120),
    template_params varchar(2000),
    rx_url varchar(1000),
    rx_name varchar(120),
    rx_image boolean not null
);
create unique index uq_staff_alert on staff_alerts (kind, ref_id, recipient);
create index idx_staff_alert_due on staff_alerts (status, next_attempt_at);
create index idx_staff_alert_wamid on staff_alerts (wamid);
