-- Clients: an optional date of birth, held for the client only, not their companies.
ALTER TABLE clients
    ADD COLUMN birthday DATE;
