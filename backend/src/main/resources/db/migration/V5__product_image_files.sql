-- Store uploaded product photos in the database (Render's free tier has no persistent
-- disk and no object storage). The bytes live in their own table so listing products
-- never drags image data along; product_images.url just points at the serving endpoint.

create table product_image_files (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    content_type varchar(100) not null,
    bytes bytea not null
);

alter table product_images add column file_id uuid;
