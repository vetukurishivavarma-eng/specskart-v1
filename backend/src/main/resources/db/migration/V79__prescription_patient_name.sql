-- Whose prescription it is, as written on it: a parent often brings a child's on their own number,
-- so this is per prescription, not the customer's (lead's) name.
alter table lead_prescriptions add column patient_name varchar(120);
