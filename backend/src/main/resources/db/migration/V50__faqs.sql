-- WhatsApp FAQ answers, edited by admins from the POS app. Seeded with the client's first ten.
-- title is the list-row label (Meta caps it at 24 chars), question the row's description.
create table faqs (
    id uuid primary key,
    title varchar(24) not null,
    question varchar(72) not null,
    answer varchar(1000) not null,
    sort_order integer not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

insert into faqs (id, title, question, answer, sort_order, created_at, updated_at) values
    (gen_random_uuid(), 'Where are you?', 'Where''s your location?',
     'Our main clinic is at Emmasdale, Lusaka.', 10, now(), now()),
    (gen_random_uuid(), 'Prices', 'What''s the price / how much are these?',
     'Send us your prescription here and we''ll give you an exact price.', 20, now(), now()),
    (gen_random_uuid(), 'Insurance', 'Do you take insurance?',
     'Yes, we are with Prudential, Onelife and ZISC insurances.', 30, now(), now()),
    (gen_random_uuid(), 'NHIMA', 'Do you take NHIMA?',
     'NHIMA doesn''t cover glasses, so we don''t take NHIMA.', 40, now(), now()),
    (gen_random_uuid(), 'Opening hours', 'Do you work on Saturdays?',
     'Yes.
Weekdays: 9:00 - 17:00
Saturdays: 9:00 - 14:00
We are closed on Sundays and public holidays.', 50, now(), now()),
    (gen_random_uuid(), 'Eye check-up', 'How much is an eye check-up?',
     'It''s free right now! Normally a comprehensive eye check-up is K250.', 60, now(), now()),
    (gen_random_uuid(), 'Frame prices', 'How much are your frames?',
     'Frames start at K300 and go up to K3,000.', 70, now(), now()),
    (gen_random_uuid(), 'Lens prices', 'How much are your lenses?',
     'Lenses start from K200 and go above K5,000, depending on your prescription, your preferences and the brand you choose.', 80, now(), now()),
    (gen_random_uuid(), 'Contact lenses', 'Do you stock contact lenses?',
     'We don''t stock them, but we can order them for you. Send us your prescription.', 90, now(), now()),
    (gen_random_uuid(), 'Delivery', 'Do you deliver?',
     'No, but you can arrange your own delivery from our main clinic at Emmasdale.', 100, now(), now());
