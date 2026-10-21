-- What each sent WhatsApp said, by Meta's message id: a reaction only names that id.
create table wa_sent_messages (
    wa_message_id varchar(255) primary key,
    to_wa_id varchar(32),
    body text,
    created_at timestamp with time zone not null default now()
);
