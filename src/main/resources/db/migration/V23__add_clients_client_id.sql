-- Clients: the firm's own identifier for each client, as PSC-00001, given when the client is added and
-- never changed afterwards. The UUID id stays the primary key; client_id is what people use to refer to a
-- client.
ALTER TABLE clients
    ADD COLUMN client_id VARCHAR(9);

-- Clients added before client ids existed are numbered in the order they were added: PSC-00001, PSC-00002, ...
UPDATE clients c
SET client_id = numbered.client_id
FROM (SELECT id, 'PSC-' || lpad(row_number() OVER (ORDER BY created_at, id)::text, 5, '0') AS client_id
      FROM clients) numbered
WHERE c.id = numbered.id;

ALTER TABLE clients
    ALTER COLUMN client_id SET NOT NULL,
    ADD CONSTRAINT ck_clients_client_id_shape
        CHECK (client_id ~ '^PSC-[0-9]{5}$');

-- A client id identifies one client. Clients are also looked up by it, and the next free one is read as
-- the highest held, which this index serves: every id has the same width, so the highest sorts last.
CREATE UNIQUE INDEX ux_clients_client_id ON clients (client_id);
