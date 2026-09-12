-- Let a customer upload a photo of their prescription at checkout instead of needing an
-- eye test first (or waiting for a manual WhatsApp back-and-forth) — a real friction point
-- for anyone buying prescription lenses.
create table prescription_files (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    content_type varchar(100) not null,
    bytes bytea not null
);
alter table carts add column prescription_file_id uuid references prescription_files(id);
alter table orders add column prescription_file_id uuid references prescription_files(id);
