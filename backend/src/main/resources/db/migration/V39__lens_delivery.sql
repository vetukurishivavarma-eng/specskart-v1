-- Where the finished lens actually goes. The funnel priced and submitted orders with no
-- address at all, so the lab had nothing to deliver against; collected on the /lens page
-- between the quote and Buy now, and required before an order can be submitted.
alter table lens_inquiries add column delivery_name varchar(255);
alter table lens_inquiries add column delivery_address varchar(1000);
alter table lens_inquiries add column delivery_area varchar(255);
alter table lens_inquiries add column delivery_landmark varchar(255);
