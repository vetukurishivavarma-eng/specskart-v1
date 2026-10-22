-- Prescriptions staff keep on a customer: a photo saved from the WhatsApp chat (WhatsApp deletes media
-- after ~30 days, so we keep our own copy) and/or the numbers typed from it.
-- file_id has no FK so a lead's hard delete can remove the file first, then the row.
create table lead_prescriptions (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    lead_id uuid not null references leads(id) on delete cascade,
    media_id varchar(100),
    file_id uuid,
    sph_right numeric(4,2),
    sph_left numeric(4,2),
    cyl_right numeric(4,2),
    cyl_left numeric(4,2),
    axis_right int,
    axis_left int,
    add_power numeric(4,2),
    pd varchar(16),
    saved_by varchar(255),
    unique (lead_id, media_id)
);
create index idx_lead_prescriptions_lead on lead_prescriptions(lead_id);
