-- Clients: people the firm works for, each looked after by one manager.
CREATE TABLE clients (
    id         UUID         NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    phone      VARCHAR(20)  NOT NULL,
    email      VARCHAR(254) NOT NULL,
    manager_id UUID         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_clients PRIMARY KEY (id),
    -- No ON DELETE action: a manager who still looks after clients must not disappear from under
    -- them.
    CONSTRAINT fk_clients_manager FOREIGN KEY (manager_id) REFERENCES managers (id),
    CONSTRAINT ck_clients_first_name_not_blank
        CHECK (length(btrim(first_name)) > 0),
    CONSTRAINT ck_clients_last_name_not_blank
        CHECK (length(btrim(last_name)) > 0),
    CONSTRAINT ck_clients_phone_not_blank
        CHECK (length(btrim(phone)) > 0),
    CONSTRAINT ck_clients_email_shape
        CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+[.][^@[:space:]]+$')
);

-- Clients are looked up by the manager assigned to them.
CREATE INDEX ix_clients_manager_id ON clients (manager_id);

-- Companies a client owns or acts for. They exist only as part of their client.
CREATE TABLE client_companies (
    id           UUID         NOT NULL,
    client_id    UUID         NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    company_type VARCHAR(20)  NOT NULL,
    address      VARCHAR(500) NOT NULL,
    CONSTRAINT pk_client_companies PRIMARY KEY (id),
    CONSTRAINT fk_client_companies_client
        FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE CASCADE,
    CONSTRAINT ck_client_companies_name_not_blank
        CHECK (length(btrim(company_name)) > 0),
    CONSTRAINT ck_client_companies_address_not_blank
        CHECK (length(btrim(address)) > 0),
    CONSTRAINT ck_client_companies_type
        CHECK (company_type IN ('PRIVATE_LIMITED', 'PUBLIC_LIMITED', 'LLP', 'PARTNERSHIP',
                                'PROPRIETORSHIP', 'TRUST', 'HUF', 'INDIVIDUAL'))
);

-- Companies are always loaded through their client.
CREATE INDEX ix_client_companies_client_id ON client_companies (client_id);
