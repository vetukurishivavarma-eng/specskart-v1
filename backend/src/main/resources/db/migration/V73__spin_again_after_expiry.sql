-- Client rule (2026-10-21): one prize at a time, not one spin ever. A customer (or a friend who
-- joined with a code) can spin again once that prize's week is over -- enforced in SpinService.
alter table spins drop constraint if exists spins_lead_id_key;
alter table spins drop constraint if exists spins_friend_lead_id_key;
create index if not exists spins_lead_id_idx on spins (lead_id);
create index if not exists spins_friend_lead_id_idx on spins (friend_lead_id);
