-- Let a rating hang off a lens sale, not just a frame order line.
--
-- A lens sale has no catalogue product: lenses are priced from lens_pricing_options, so there
-- is no products row to point at and a "1-5" reply from a lens customer had nowhere to land.
-- Rather than a parallel table, reviews becomes "a rating from a lead about one purchase" and
-- exactly one of (product_id + order_id) or lens_inquiry_id is set.
--
-- The frame path is unchanged: uq_review still stops a second rating on the same order line,
-- and product rating averages explicitly skip null product_id (see ReviewRepository).
alter table reviews alter column product_id drop not null;
alter table reviews alter column order_id drop not null;

alter table reviews add column lens_inquiry_id uuid references lens_inquiries(id) on delete cascade;

-- One rating per lens sale. Postgres treats nulls as distinct, so every frame review (which
-- leaves this null) is still allowed through.
alter table reviews add constraint uq_review_lens unique (lens_inquiry_id);
