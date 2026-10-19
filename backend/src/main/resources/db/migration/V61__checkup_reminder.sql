-- Eye-checkup reminder sequence (EyeCheckupReminderService): every 2 days, up to 4 touches.
alter table leads add column checkup_started_at timestamp with time zone;
alter table leads add column checkup_step int not null default 0;
alter table leads add column checkup_next_at timestamp with time zone;
create index idx_leads_checkup_next_at on leads (checkup_next_at);
