-- While a staff member is chatting with a customer from the business number, the bot stays
-- quiet so it doesn't answer the customer's replies with its menu. Null / past = bot answers.
alter table leads add column agent_chat_until timestamp with time zone;
