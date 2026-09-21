-- Lens blanks are counted per shop, like frames: Lusaka may hold photochromatic while
-- Mumbai doesn't. Each lens type is a `products` row with kind LENS so purchases,
-- transfers, adjustments, bulk upload (by SKU) and the movement history all work unchanged.
-- One unit = one pair. DRAFT keeps them off the website and the frame till, which both
-- only show ACTIVE products; AdminCatalogService refuses to change that.
insert into products (id, created_at, updated_at, slug, name, price_minor, status, kind, sku)
select gen_random_uuid(), now(), now(), 'lens-clear', 'Lens blank - Clear (pair)',
       coalesce((select price_minor from lens_pricing_options where code = 'CLEAR'), 0), 'DRAFT', 'LENS', 'LENS-CLEAR';
insert into products (id, created_at, updated_at, slug, name, price_minor, status, kind, sku)
select gen_random_uuid(), now(), now(), 'lens-photochromatic', 'Lens blank - Photochromatic (pair)',
       coalesce((select price_minor from lens_pricing_options where code = 'PHOTOCHROMATIC'), 0), 'DRAFT', 'LENS', 'LENS-PHOTOCHROMATIC';

-- Which shop's shelf a lens order took its pair from (also makes the deduction once-only),
-- and whether that shelf was empty at the time, i.e. the lab has to order blanks in.
alter table lens_inquiries add column stock_store_id uuid references stores(id);
alter table lens_inquiries add column backorder boolean not null default false;
