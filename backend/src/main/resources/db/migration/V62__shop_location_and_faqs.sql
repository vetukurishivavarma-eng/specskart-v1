-- The shop's real location (2026-10-20). The only active shop still carried a test pin in
-- Mumbai, and that pin was the map link the bot sent every Zambian customer who asked.
alter table stores add column map_url varchar(500);

update stores set
    map_url = 'https://maps.app.goo.gl/4Y1xnLdVeKX95Ezh9',
    address = 'Radian Retail Park, Great North Road (opposite Chandwe Musonda Rd), Emmasdale, Lusaka'
where active = true;

-- customers were reading "Shop1, Lusaka"
update stores set name = 'Specskart Opticians' where name = 'Shop1';

update faqs set answer = 'We''re at Radian Retail Park, Great North Road (opposite Chandwe Musonda Rd), Emmasdale, Lusaka.
📍 https://maps.app.goo.gl/4Y1xnLdVeKX95Ezh9

Weekdays 9:00 - 17:00, Saturdays 9:00 - 14:00.', updated_at = now()
where title = 'Where are you?';

update faqs set answer = 'No, but you can arrange your own delivery from our shop at Radian Retail Park, Emmasdale.
📍 https://maps.app.goo.gl/4Y1xnLdVeKX95Ezh9', updated_at = now()
where title = 'Delivery';

-- "Is the promotion still on?" was the ad's most-asked question and the bot had no answer.
-- Admins edit or delete this from the POS app (More -> FAQs) when the sale ends.
insert into faqs (id, title, question, answer, sort_order, created_at, updated_at) values
    (gen_random_uuid(), 'Offers', 'Is the promotion still on?',
     'Yes, our 50% off frames sale is still on! Come in and choose yours. The eye check-up is free.', 5, now(), now());
