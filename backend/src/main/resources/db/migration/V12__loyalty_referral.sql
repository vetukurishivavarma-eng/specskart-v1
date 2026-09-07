-- Loyalty points + referrals. Points are earned on paid orders and on referring a
-- friend, and can be redeemed as a checkout discount. No customer accounts — a
-- shopper sees their balance + referral code on the order-tracking page and in
-- WhatsApp messages.

alter table leads add column points int not null default 0;
alter table leads add column referral_code varchar(20);
create index idx_lead_referral_code on leads (referral_code);

alter table orders add column referred_by_lead_id uuid;
alter table orders add column points_earned int not null default 0;
alter table orders add column points_redeemed int not null default 0;
alter table orders add column referral_credited boolean not null default false;
