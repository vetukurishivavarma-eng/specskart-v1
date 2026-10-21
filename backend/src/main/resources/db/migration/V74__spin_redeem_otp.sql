-- Redeeming a prize at the till: staff type the customer's WhatsApp number + prize code, the
-- customer gets a 6-digit verification code on WhatsApp (stored hashed, 10 minutes, 5 tries).
alter table spins add column otp_code varchar(20);
alter table spins add column otp_hash varchar(64);
alter table spins add column otp_expires_at timestamp with time zone;
alter table spins add column otp_tries int not null default 0;
