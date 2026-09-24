-- The street address a customer is sent to collect their lenses. The shop already had a city
-- and a map pin; neither is something you can read out and walk to, and with delivery retired
-- "come and collect" is the whole fulfilment step.
alter table stores add column address varchar(500);
