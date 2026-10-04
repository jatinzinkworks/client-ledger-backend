-- Services from the Service Catalog delivered to a client's company. A company can take many
-- services, and the same service more than once (for example, a monthly filing each month).
CREATE TABLE company_services (
    id                 UUID          NOT NULL,
    company_id         UUID          NOT NULL,
    catalog_service_id UUID          NOT NULL,
    delivery_date      DATE          NOT NULL,
    discount_percent   NUMERIC(5, 2) NOT NULL,
    comment            VARCHAR(1000),
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_company_services PRIMARY KEY (id),
    -- No ON DELETE action on either key: services delivered are a billing record, so neither the
    -- company nor the catalog service may disappear from under them.
    CONSTRAINT fk_company_services_company
        FOREIGN KEY (company_id) REFERENCES client_companies (id),
    CONSTRAINT fk_company_services_catalog_service
        FOREIGN KEY (catalog_service_id) REFERENCES catalog_services (id),
    CONSTRAINT ck_company_services_discount_percent
        CHECK (discount_percent >= 0 AND discount_percent <= 100)
);

-- Services are listed per company, and a company is checked for an existing assignment of a
-- given service when keeping the catalog's subscriber count.
CREATE INDEX ix_company_services_company_service ON company_services (company_id, catalog_service_id);

-- Backs the foreign key to the catalog, so deleting a catalog service does not scan this table.
CREATE INDEX ix_company_services_catalog_service ON company_services (catalog_service_id);
