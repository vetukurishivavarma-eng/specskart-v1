-- Who changed what, and when. Written at the key mutation points (sales, stock, prices,
-- purchases, transfers, staff, shops) rather than via a generic interceptor -- see
-- AuditLogService for the exact list. store_id is nullable for actions that aren't
-- shop-specific (e.g. creating a staff account, publishing an app release).
create table audit_log (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    entity_type varchar(40) not null,
    entity_id varchar(64),
    action varchar(40) not null,
    actor_id uuid references users(id),
    actor_name varchar(200) not null default '',
    store_id uuid references stores(id),
    summary varchar(500) not null default ''
);
create index idx_audit_log_created on audit_log (created_at desc);
create index idx_audit_log_store on audit_log (store_id, created_at desc);
create index idx_audit_log_entity on audit_log (entity_type, created_at desc);
