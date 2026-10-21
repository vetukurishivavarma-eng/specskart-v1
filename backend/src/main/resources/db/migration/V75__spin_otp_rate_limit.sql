-- Verification codes at the till: one a minute, at most 5 per prize.
alter table spins add column otp_sent_at timestamp with time zone;
alter table spins add column otp_sends int not null default 0;
