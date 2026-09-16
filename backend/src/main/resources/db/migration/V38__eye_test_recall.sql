-- Yearly eye-test recall: when we last nudged this customer to get their eyes re-checked.
-- Null = never nudged. Re-used for the repeat nudge a year later, so one column is enough.
-- "timestamp with time zone", not TIMESTAMPTZ -- the migration test runs these against H2.
ALTER TABLE leads ADD COLUMN IF NOT EXISTS eye_test_recalled_at timestamp with time zone;

CREATE INDEX IF NOT EXISTS idx_leads_eye_test_recalled_at ON leads (eye_test_recalled_at);
