-- Doorstep delivery ladder for web lens orders: ORDERED -> PACKED -> OUT_FOR_DELIVERY ->
-- DELIVERED. Kept separate from `status`, which is the billing state (and which the sales
-- reports and the walk-in counter flow both key off SOLD) -- a lens can be packed and out
-- for delivery while still unpaid, and cash-on-delivery makes that the normal case.
-- Null for a walk-in: nothing is delivered, the customer is at the counter.
alter table lens_inquiries add column fulfilment varchar(32);

update lens_inquiries set fulfilment = 'ORDERED' where walk_in = false and status = 'SUBMITTED';
update lens_inquiries set fulfilment = 'DELIVERED' where walk_in = false and status = 'SOLD';

create index idx_lens_inquiry_fulfilment on lens_inquiries (fulfilment, created_at);
