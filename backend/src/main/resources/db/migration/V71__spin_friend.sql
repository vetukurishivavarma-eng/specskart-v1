-- Bring-a-friend prize: the winner's code only works once a NEW customer messages us with it;
-- that friend gets their own code, linked to the winner's spin.
alter table spins add column friend_lead_id uuid unique references leads(id) on delete set null;
alter table spins add column friend_code varchar(20) unique;
alter table spins add column friend_redeemed_at timestamp with time zone;
alter table spins add column friend_redeemed_by varchar(255);
