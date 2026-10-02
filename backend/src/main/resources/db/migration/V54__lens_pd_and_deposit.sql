-- Counter lens orders now go to the lab like web orders, so the lab needs the PD, and a
-- customer can leave a deposit and pay the balance at pickup.
-- pd: free text so both "62" and a dual "31.5/30.5" fit.
-- paid_minor: money taken so far. NULL on older rows = derive from status/paid_at as before.
alter table lens_inquiries add column pd varchar(16);
alter table lens_inquiries add column paid_minor bigint;
