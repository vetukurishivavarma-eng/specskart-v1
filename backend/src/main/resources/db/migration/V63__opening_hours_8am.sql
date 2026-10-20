-- The shop opens at 8:00, not 9:00 (its Google Maps listing, 2026-10-20).
update faqs set answer = 'Yes.
Weekdays: 8:00 - 17:00
Saturdays: 8:00 - 14:00
We are closed on Sundays and public holidays.', updated_at = now()
where title = 'Opening hours';

update faqs set answer = replace(replace(answer, 'Weekdays 9:00', 'Weekdays 8:00'), 'Saturdays 9:00', 'Saturdays 8:00'),
    updated_at = now()
where title = 'Where are you?';
