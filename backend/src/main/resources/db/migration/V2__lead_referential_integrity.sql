-- V1 declared no foreign keys, so deleting a lead left orphan rows behind and
-- removing one lead meant seven manual DELETEs. Clean any existing orphans,
-- then add proper constraints. Also adds the `archived_at` column the CRM uses
-- to soft-hide a lead without losing its history.

-- Hard children: gone with the lead.
DELETE FROM lead_notes            WHERE lead_id NOT IN (SELECT id FROM leads);
DELETE FROM lead_events           WHERE lead_id IS NOT NULL AND lead_id NOT IN (SELECT id FROM leads);
DELETE FROM whatsapp_messages     WHERE lead_id NOT IN (SELECT id FROM leads);
DELETE FROM frame_finder_sessions WHERE lead_id NOT IN (SELECT id FROM leads);
DELETE FROM face_analyses         WHERE lead_id NOT IN (SELECT id FROM leads);
DELETE FROM consent_records       WHERE lead_id NOT IN (SELECT id FROM leads);

ALTER TABLE lead_notes            ADD CONSTRAINT fk_note_lead    FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE;
ALTER TABLE lead_events           ADD CONSTRAINT fk_event_lead   FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE;
ALTER TABLE whatsapp_messages     ADD CONSTRAINT fk_wamsg_lead   FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE;
ALTER TABLE frame_finder_sessions ADD CONSTRAINT fk_ffs_lead     FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE;
ALTER TABLE face_analyses         ADD CONSTRAINT fk_face_lead    FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE;
ALTER TABLE consent_records       ADD CONSTRAINT fk_consent_lead FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE;

-- Soft references: keep the row, drop the dangling pointer.
UPDATE leads                SET campaign_id        = NULL WHERE campaign_id        IS NOT NULL AND campaign_id        NOT IN (SELECT id FROM campaigns);
UPDATE leads                SET assigned_to_user_id = NULL WHERE assigned_to_user_id IS NOT NULL AND assigned_to_user_id NOT IN (SELECT id FROM users);
UPDATE lead_events          SET campaign_id        = NULL WHERE campaign_id        IS NOT NULL AND campaign_id        NOT IN (SELECT id FROM campaigns);
UPDATE frame_finder_sessions SET source_campaign_id = NULL WHERE source_campaign_id IS NOT NULL AND source_campaign_id NOT IN (SELECT id FROM campaigns);

ALTER TABLE leads                ADD CONSTRAINT fk_lead_campaign  FOREIGN KEY (campaign_id)         REFERENCES campaigns(id) ON DELETE SET NULL;
ALTER TABLE leads                ADD CONSTRAINT fk_lead_assignee  FOREIGN KEY (assigned_to_user_id) REFERENCES users(id)     ON DELETE SET NULL;
ALTER TABLE lead_events          ADD CONSTRAINT fk_event_campaign FOREIGN KEY (campaign_id)         REFERENCES campaigns(id) ON DELETE SET NULL;
ALTER TABLE frame_finder_sessions ADD CONSTRAINT fk_ffs_campaign  FOREIGN KEY (source_campaign_id)  REFERENCES campaigns(id) ON DELETE SET NULL;

-- Soft archive for the CRM.
ALTER TABLE leads ADD COLUMN archived_at timestamp with time zone;
CREATE INDEX idx_lead_archived ON leads (archived_at);
