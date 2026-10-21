-- Client picked two more prizes (2026-10-21): bring a friend + 20% off a second frame.
-- Weights out of 100; the owner can change them in web admin -> Spin & Win.
insert into spin_prizes (id, created_at, updated_at, label, weight, active, sort_order)
select gen_random_uuid(), now(), now(), v.label, v.weight, true, v.sort_order
from (values
    ('Free eye check for your family', 35, 0),
    ('K100 off photochromatic + blue block lenses', 25, 1),
    ('Free sunglasses with your first pair', 15, 2),
    ('Bring a friend: K100 off for you both', 15, 3),
    ('20% off your second frame', 10, 4)
) as v(label, weight, sort_order)
where not exists (select 1 from spin_prizes p where p.label = v.label);

update spin_prizes set weight = 35 where label = 'Free eye check for your family';
update spin_prizes set weight = 25 where label = 'K100 off photochromatic + blue block lenses';
update spin_prizes set weight = 15 where label = 'Free sunglasses with your first pair';
