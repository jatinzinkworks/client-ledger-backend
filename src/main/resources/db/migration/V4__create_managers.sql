-- Manager Catalog: staff who can be assigned to client work.
CREATE TABLE managers (
    id            UUID         NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    mobile_number VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_managers PRIMARY KEY (id),
    CONSTRAINT ck_managers_first_name_not_blank
        CHECK (length(btrim(first_name)) > 0),
    CONSTRAINT ck_managers_last_name_not_blank
        CHECK (length(btrim(last_name)) > 0),
    CONSTRAINT ck_managers_role
        CHECK (role IN ('SENIOR_MANAGER', 'MANAGER', 'ASSOCIATE')),
    CONSTRAINT ck_managers_email_shape
        CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+[.][^@[:space:]]+$'),
    CONSTRAINT ck_managers_mobile_number_not_blank
        CHECK (length(btrim(mobile_number)) > 0)
);

-- Email identifies a person, so it must not repeat under a different capitalisation.
CREATE UNIQUE INDEX ux_managers_email ON managers (lower(email));
