-- Clients: WhatsApp messaging has been removed, so whether a client agreed to receive WhatsApp
-- messages is no longer recorded.
ALTER TABLE clients
    DROP COLUMN whatsapp_opt_in;
