-- Global Settings: identity of the firm, printed on invoices and Excel exports.
CREATE TABLE firm_details (
    id                   UUID         NOT NULL,
    firm_name            VARCHAR(200) NOT NULL,
    gstin                VARCHAR(15)  NOT NULL,
    firm_registration_no VARCHAR(50),
    email                VARCHAR(254),
    phone                VARCHAR(30),
    address              VARCHAR(500) NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_firm_details PRIMARY KEY (id),
    CONSTRAINT ck_firm_details_firm_name_not_blank
        CHECK (length(btrim(firm_name)) > 0),
    CONSTRAINT ck_firm_details_address_not_blank
        CHECK (length(btrim(address)) > 0),
    CONSTRAINT ck_firm_details_gstin_format
        CHECK (gstin ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$')
);

-- Firm details are a singleton global setting, matching payment_terms. A unique index over a
-- constant expression lets the database hold that invariant, so two concurrent creates cannot
-- both succeed.
CREATE UNIQUE INDEX ux_firm_details_singleton ON firm_details ((TRUE));
