-- One row per staff phone that can receive push notifications (Firebase Cloud Messaging token).
-- Staff order alerts go here as real phone notifications -- free, unlike WhatsApp templates.
create table push_tokens (
    id uuid primary key,
    token varchar(4096) not null unique,
    user_email varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
