-- "Yes." answered the FAQ's old question ("Do you work on Saturdays?"); under the bot's
-- "We're closed right now" line it reads as a contradiction. Only touches the V63 text, so an
-- answer an admin has since edited in the POS app is left alone.
update faqs set answer = 'Weekdays: 8:00 - 17:00
Saturdays: 8:00 - 14:00
We are closed on Sundays and public holidays.', question = 'When are you open?', updated_at = now()
where title = 'Opening hours' and answer = 'Yes.
Weekdays: 8:00 - 17:00
Saturdays: 8:00 - 14:00
We are closed on Sundays and public holidays.';
