-- V67 wrote "--" where a dash was meant; customers see these texts as-is.
update faqs set answer = replace(answer, 'We don''t deliver -- every order', 'We don''t deliver — every order')
where title = 'Delivery';

update store_config set payment_note = 'Pay at the shop when you collect — cash or mobile money.'
where payment_note = 'Pay at the shop when you collect -- cash or mobile money.';
