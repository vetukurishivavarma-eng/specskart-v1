-- Staff chat like WhatsApp: a message can quote another (by its WhatsApp id), and delivery
-- statuses (sent / delivered / read / failed) are looked up by WhatsApp id for every send.
alter table whatsapp_messages add column reply_to_wa_id varchar(255);
create index idx_whatsapp_messages_wa_id on whatsapp_messages(wa_message_id);
