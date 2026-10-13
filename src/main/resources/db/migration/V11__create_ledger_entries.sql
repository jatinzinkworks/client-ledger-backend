-- Company services: record whether a ONE_OFF service has been posted to the ledger, so it is
-- billed once only. Recurring services move their next_due_date on instead, and leave this false.
ALTER TABLE company_services
    ADD COLUMN ledger_posted BOOLEAN NOT NULL DEFAULT FALSE;

-- Amounts a company owes for the services delivered to it, one row per service per due date.
-- Each row is a snapshot: the fee, discount and GST rate are copied from the service as it stood
-- when the entry was posted, so later changes to the catalog or the assignment do not alter it.
CREATE TABLE ledger_entries (
    id                 UUID          NOT NULL,
    client_id          UUID          NOT NULL,
    company_id         UUID          NOT NULL,
    company_service_id UUID          NOT NULL,
    catalog_service_id UUID          NOT NULL,
    service_name       VARCHAR(150)  NOT NULL,
    service_type       VARCHAR(20)   NOT NULL,
    due_date           DATE          NOT NULL,
    entry_date         DATE          NOT NULL,
    service_fee        NUMERIC(12, 2) NOT NULL,
    discount_percent   NUMERIC(5, 2) NOT NULL,
    discount_amount    NUMERIC(12, 2) NOT NULL,
    taxable_amount     NUMERIC(12, 2) NOT NULL,
    gst_rate_percent   NUMERIC(5, 2) NOT NULL,
    gst_amount         NUMERIC(14, 2) NOT NULL,
    total_amount       NUMERIC(14, 2) NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    -- No ON DELETE action on any key: a ledger entry is a financial record, so nothing it refers
    -- to may disappear from under it.
    CONSTRAINT fk_ledger_entries_client
        FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_ledger_entries_company
        FOREIGN KEY (company_id) REFERENCES client_companies (id),
    CONSTRAINT fk_ledger_entries_company_service
        FOREIGN KEY (company_service_id) REFERENCES company_services (id),
    CONSTRAINT fk_ledger_entries_catalog_service
        FOREIGN KEY (catalog_service_id) REFERENCES catalog_services (id),
    -- A service is billed once for each date it falls due, however often posting is requested.
    CONSTRAINT uq_ledger_entries_company_service_due_date UNIQUE (company_service_id, due_date),
    CONSTRAINT ck_ledger_entries_service_type
        CHECK (service_type IN ('ONE_OFF', 'MONTHLY', 'QUARTERLY', 'ANNUAL')),
    CONSTRAINT ck_ledger_entries_discount_percent
        CHECK (discount_percent >= 0 AND discount_percent <= 100),
    CONSTRAINT ck_ledger_entries_gst_rate_percent
        CHECK (gst_rate_percent >= 0 AND gst_rate_percent <= 100),
    CONSTRAINT ck_ledger_entries_amounts
        CHECK (service_fee >= 0 AND discount_amount >= 0 AND gst_amount >= 0
            AND taxable_amount = service_fee - discount_amount
            AND total_amount = taxable_amount + gst_amount)
);

-- A client's ledger is read across all of their companies, in date order.
CREATE INDEX ix_ledger_entries_client_entry_date ON ledger_entries (client_id, entry_date);

-- A company's ledger is read in due date order; also backs the foreign key to the company.
CREATE INDEX ix_ledger_entries_company_due_date ON ledger_entries (company_id, due_date);

-- Backs the foreign key to the catalog, so deleting a catalog service does not scan this table.
CREATE INDEX ix_ledger_entries_catalog_service ON ledger_entries (catalog_service_id);
