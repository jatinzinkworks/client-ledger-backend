-- Clients: discount granted to the client, and to each of their companies.
--
-- A company discount defaults to the client discount when the caller does not set one. That copy
-- is made by the application at write time, so each column always holds the rate in force.
-- Existing rows have no discount, hence the zero default.
ALTER TABLE clients
    ADD COLUMN discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    ADD CONSTRAINT ck_clients_discount_percent
        CHECK (discount_percent >= 0 AND discount_percent <= 100);

ALTER TABLE client_companies
    ADD COLUMN discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    ADD CONSTRAINT ck_client_companies_discount_percent
        CHECK (discount_percent >= 0 AND discount_percent <= 100);
