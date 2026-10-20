-- Chat -> shop visit (LeadNudges): a booked eye-test day + its morning reminder, one follow-up
-- after a quote went quiet, and staff reminders for a customer left waiting.
alter table leads add column visit_on date;
alter table leads add column visit_reminded_on date;
alter table leads add column quote_follow_up_due_at timestamp with time zone;
alter table leads add column quote_followed_up_at timestamp with time zone;
alter table leads add column awaiting_staff_since timestamp with time zone;
alter table leads add column staff_nudged_at timestamp with time zone;
alter table leads add column staff_nudges int not null default 0;
create index idx_leads_visit_on on leads (visit_on);
create index idx_leads_quote_follow_up_due_at on leads (quote_follow_up_due_at);
create index idx_leads_awaiting_staff_since on leads (awaiting_staff_since);
