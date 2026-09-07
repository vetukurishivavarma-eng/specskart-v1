-- Countdown drops / limited editions.
alter table products add column drops_at timestamp with time zone;
alter table products add column limited_edition boolean not null default false;
