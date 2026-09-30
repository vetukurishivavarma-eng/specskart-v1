-- Frames are added from the POS app with photo, code (SKU), brand, price and shape.
-- Brand is the one of those the catalogue didn't have yet.
alter table products add column brand varchar(80);
