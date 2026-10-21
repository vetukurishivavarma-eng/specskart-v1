-- 2026-10-21: no delivery any more -- every website order is collected at the shop.
-- Each update only touches text nobody has edited since, so a client edit is kept.

update faqs set answer = 'We don''t deliver -- every order is collected at our shop at Radian Retail Park, Great North Road, Emmasdale, Lusaka. We''ll message you here on WhatsApp as soon as it''s ready.
📍 https://maps.app.goo.gl/4Y1xnLdVeKX95Ezh9', updated_at = now()
where title = 'Delivery' and answer like 'No, but you can arrange your own delivery%';

update store_config set payment_note = 'Pay at the shop when you collect -- cash or mobile money.'
where payment_note = 'Pay by card, MTN / Airtel Money, or cash on delivery.';

update store_config set guarantee_note = 'Collect at our Lusaka shop · 30-day fit guarantee · free frame adjustments.'
where guarantee_note = 'Delivered across Zambia · 30-day fit guarantee · free frame adjustments.';
