-- Virtual try-on: a transparent-PNG render of the frame, overlaid on the shopper's
-- selfie in the browser. Stored as a product_image_files blob; this column holds the
-- serving path (/api/public/product-images/{fileId}).
alter table products add column try_on_image_url varchar(1024);
