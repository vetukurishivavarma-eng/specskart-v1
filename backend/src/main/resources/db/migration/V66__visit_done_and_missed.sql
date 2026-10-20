-- Eye-test bookings page (POS -> Bookings): staff tap "Check-up done"; the same-day nudge and the
-- next-morning "we missed you" are each sent once and skipped for anyone marked done.
alter table leads add column visited_on date;
alter table leads add column visit_nudged_on date;
alter table leads add column visit_missed_on date;
