-- How many times a till may tap "Later" before the update becomes compulsory. Server-side
-- policy so a release that must not be postponed can say so without shipping an app to
-- enforce it. Zero means compulsory from the first prompt.
alter table app_releases add column grace_count integer not null default 2;
