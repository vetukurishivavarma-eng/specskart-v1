-- Post-purchase engagement: a one-off personalised WhatsApp a few days after delivery
-- (thanks + track link + a discount to come back). Null until sent.
alter table orders add column followed_up_at timestamp with time zone;
