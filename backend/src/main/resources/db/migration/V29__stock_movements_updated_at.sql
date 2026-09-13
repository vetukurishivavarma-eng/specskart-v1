-- V26 gave every other pos_* table both created_at/updated_at (matching BaseEntity, which
-- every JPA entity in this codebase extends) but missed updated_at on stock_movements --
-- caught by Hibernate's schema validation against the real Postgres schema in prod (the
-- mock/H2 test profile uses ddl-auto=create-drop, which builds its schema straight from the
-- entities and so can never catch a Flyway migration that's out of sync with them).
alter table stock_movements add column updated_at timestamp with time zone not null default now();
