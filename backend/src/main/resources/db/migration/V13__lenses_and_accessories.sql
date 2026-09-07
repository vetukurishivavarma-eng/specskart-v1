-- Prescription-lens options at checkout + accessory products for "complete the look".

alter table products add column kind varchar(20) not null default 'FRAME';  -- FRAME | ACCESSORY

alter table carts add column lens_type varchar(30);
alter table carts add column lens_add_minor bigint not null default 0;
alter table carts add column rx_json text;

alter table orders add column lens_type varchar(30);
alter table orders add column lens_add_minor bigint not null default 0;
alter table orders add column rx_json text;
