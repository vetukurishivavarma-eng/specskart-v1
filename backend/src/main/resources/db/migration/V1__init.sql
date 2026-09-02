-- Specskart v1 — Phase 1 schema (PostgreSQL / prod profile).
-- Dev & mock profiles use Hibernate create-drop and never run this file.

create table users (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    full_name varchar(255) not null,
    role varchar(20) not null,
    active boolean not null default true
);

create table campaigns (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    name varchar(255) not null,
    platform varchar(20) not null,
    external_campaign_id varchar(255),
    start_date date,
    end_date date,
    budget double precision,
    destination varchar(255),
    utm_source varchar(255),
    utm_medium varchar(255),
    utm_campaign varchar(255),
    utm_content varchar(255),
    utm_term varchar(255),
    status varchar(20) not null,
    metadata text
);
create index idx_campaign_ext on campaigns (external_campaign_id);
create index idx_campaign_utm on campaigns (utm_campaign);

create table leads (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    whatsapp_wa_id varchar(255) unique,
    whatsapp_number varchar(255),
    country_code varchar(10),
    name varchar(255),
    acquisition_source varchar(20) not null,
    campaign_id uuid,
    ad_id varchar(255),
    ad_set_id varchar(255),
    click_id varchar(255),
    referrer varchar(1024),
    landing_page varchar(1024),
    utm_source varchar(255),
    utm_medium varchar(255),
    utm_campaign varchar(255),
    utm_content varchar(255),
    utm_term varchar(255),
    provider_metadata text,
    status varchar(40) not null,
    first_contact_at timestamp with time zone,
    last_contact_at timestamp with time zone,
    assigned_to_user_id uuid,
    face_shape varchar(40),
    face_confidence double precision,
    recommended_frame_categories text
);
create index idx_lead_wa on leads (whatsapp_number);
create index idx_lead_status on leads (status);
create index idx_lead_campaign on leads (campaign_id);

create table lead_notes (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    lead_id uuid not null,
    body varchar(4000) not null,
    author_email varchar(255)
);
create index idx_note_lead on lead_notes (lead_id);

create table lead_events (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    lead_id uuid,
    session_id varchar(255),
    event_type varchar(60) not null,
    source varchar(60),
    campaign_id uuid,
    metadata text
);
create index idx_event_lead on lead_events (lead_id);
create index idx_event_type on lead_events (event_type);

create table whatsapp_messages (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    lead_id uuid not null,
    direction varchar(10) not null,
    wa_message_id varchar(255),
    message_type varchar(30),
    body varchar(4000),
    status varchar(20)
);
create index idx_wamsg_lead on whatsapp_messages (lead_id);

create table webhook_events (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    dedupe_key varchar(255) not null unique,
    kind varchar(60)
);

create table frame_finder_sessions (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    lead_id uuid not null,
    token_hash varchar(128) not null unique,
    expires_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    last_accessed_at timestamp with time zone,
    status varchar(20) not null,
    source_campaign_id uuid
);

create table face_analyses (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    lead_id uuid not null,
    session_id uuid,
    predicted_face_shape varchar(40) not null,
    confidence_score double precision not null,
    recommendation_summary varchar(2000),
    recommended_frame_categories text,
    geometry_data text,
    image_storage_reference varchar(255),
    consent_given boolean not null,
    analysis_version varchar(20) not null
);
create index idx_face_lead on face_analyses (lead_id);

create table face_shapes (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    code varchar(40) not null unique,
    display_name varchar(255) not null,
    description varchar(2000),
    active boolean not null default true
);

create table frame_categories (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    code varchar(40) not null unique,
    display_name varchar(255) not null,
    description varchar(2000),
    image_url varchar(1024),
    active boolean not null default true
);

create table face_shape_frame_recommendations (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    face_shape_id uuid not null,
    frame_category_id uuid not null,
    stance varchar(20) not null,
    priority int not null,
    recommendation_reason varchar(1000),
    gender_neutral boolean not null default true,
    active boolean not null default true,
    notes varchar(1000),
    constraint uq_face_frame unique (face_shape_id, frame_category_id)
);

create table consent_records (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    lead_id uuid not null,
    type varchar(30) not null,
    status varchar(20) not null,
    source varchar(30) not null,
    policy_version varchar(40)
);
create index idx_consent_lead on consent_records (lead_id);
