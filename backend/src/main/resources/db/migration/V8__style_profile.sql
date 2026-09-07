-- Optional style quiz taken after face analysis: re-weights frame recommendations
-- toward the shopper's taste. All nullable, additive.
alter table leads add column style_vibe varchar(20);
alter table leads add column style_colour varchar(20);
alter table leads add column style_budget varchar(20);
alter table leads add column style_screen_hours varchar(20);
