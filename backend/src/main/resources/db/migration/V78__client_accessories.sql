-- The client's counter add-ons (2026-10-23): a pouch is free with every pair, extras are sold.
-- Live website/till items (ACTIVE, kind ACCESSORY) -- they show as cart add-ons and in the bot's
-- accessories answer once a shop has them on its shelf (stock is set in the POS app).
insert into products (id, created_at, updated_at, slug, name, price_minor, status, kind, sku, description)
select gen_random_uuid(), now(), now(), v.slug, v.name, v.price, 'ACTIVE', 'ACCESSORY', v.sku, v.description
from (values
    ('lens-cleaning-kit', 'Lens cleaning kit', 8000, 'ACC-CLEAN-KIT', 'Keeps your lenses clear and smudge-free.'),
    ('extra-pouch', 'Extra pouch', 2000, 'ACC-POUCH', 'A spare soft pouch. One comes free with every pair.'),
    ('reading-glasses', 'Reading glasses (readers)', 10000, 'ACC-READERS', 'Ready-made reading glasses.')
) as v(slug, name, price, sku, description)
where not exists (select 1 from products p where p.slug = v.slug or p.sku = v.sku);
