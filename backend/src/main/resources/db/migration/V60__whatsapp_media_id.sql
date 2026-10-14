-- Inbound WhatsApp photos/files: Meta's media id, so staff can view them in the POS chat.
alter table whatsapp_messages add column media_id varchar(128);
