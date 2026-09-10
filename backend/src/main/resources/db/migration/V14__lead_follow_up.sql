-- Automated WhatsApp nurture sequence: walk a cold lead through a few timed
-- product touches until they buy or opt out. State lives on the lead.
--   follow_up_state: null = never enrolled, ACTIVE = in sequence,
--                    CONVERTED / OPTED_OUT / DONE = terminal (job skips it)
alter table leads add column follow_up_state   varchar(16);
alter table leads add column follow_up_step    int not null default 0;
alter table leads add column follow_up_next_at timestamp with time zone;

create index idx_lead_followup_due on leads (follow_up_state, follow_up_next_at);
