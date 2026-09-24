-- Shop pickup, not doorstep delivery: the client runs one shop and hands every lens over at
-- the counter. The four-rung doorstep ladder collapses to ORDERED -> READY -> DELIVERED,
-- where DELIVERED now means "collected" (kept under the old name so the queries, the reports
-- and every existing row carry on unchanged).
update lens_inquiries set fulfilment = 'READY' where fulfilment in ('PACKED', 'OUT_FOR_DELIVERY');

-- The delivery_* columns stay for the orders that were placed while delivery existed; nothing
-- writes them any more.
