-- Clients: whether the client has agreed to receive WhatsApp messages from the firm, such as payment
-- reminders. WhatsApp's business policy requires that consent before messaging anyone, so existing
-- clients start opted out until it is recorded for them.
ALTER TABLE clients
    ADD COLUMN whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE;
