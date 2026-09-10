-- Zambia conversion boosters:
--   * cash / pay-on-delivery is the norm for African e-commerce — surface it at checkout
--   * "first order, free delivery" as an acquisition hook
--   * reassurance copy shown on the storefront + checkout
alter table store_config add column first_order_free_shipping boolean not null default false;
alter table store_config add column payment_note   varchar(300) not null
    default 'Pay by card, MTN / Airtel Money, or cash on delivery.';
alter table store_config add column guarantee_note varchar(300) not null
    default 'Delivered across Zambia · 30-day fit guarantee · free frame adjustments.';
