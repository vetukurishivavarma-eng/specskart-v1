-- Per-lead promo codes: the funnel issues a personal, time-limited discount right
-- after face analysis. Shared codes keep lead_id null.
alter table promo_codes add column lead_id uuid;
create index idx_promo_lead on promo_codes (lead_id);
