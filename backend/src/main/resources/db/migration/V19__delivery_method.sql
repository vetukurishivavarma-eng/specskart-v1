-- Bus-station pickup as an alternative to door delivery — the standard cheap way to reach
-- customers outside Lusaka in Zambia (intercity bus parcel services), not just a door address.
alter table orders add column delivery_method varchar(16) not null default 'DOOR';
alter table orders add column pickup_point varchar(200);
