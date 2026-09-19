-- Post-purchase follow-up for lens customers. Everything after the sale was frames-only:
-- PostPurchaseJob reads orders, and the eye-test recall query only looked at delivered orders,
-- so someone who bought lenses -- the whole of the funnel the client actually has switched on --
-- heard nothing after "delivered". This column is the once-only marker, the same job
-- orders.followed_up_at does for the frame storefront.
alter table lens_inquiries add column post_purchase_at timestamp with time zone;
