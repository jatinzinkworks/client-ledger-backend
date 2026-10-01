-- Service Catalog: billable services the firm offers.
CREATE TABLE catalog_services (
    id                       UUID           NOT NULL,
    service_name             VARCHAR(150)   NOT NULL,
    description              VARCHAR(1000),
    category                 VARCHAR(20)    NOT NULL,
    billing_frequency        VARCHAR(20)    NOT NULL,
    standard_fee             NUMERIC(12, 2) NOT NULL,
    gst_rate_percent         NUMERIC(5, 2)  NOT NULL DEFAULT 18.00,
    invoice_day_of_month     INTEGER,
    invoice_month_of_quarter VARCHAR(20),
    invoice_month            VARCHAR(20),
    used_by_companies        INTEGER        NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ    NOT NULL,
    updated_at               TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_catalog_services PRIMARY KEY (id),
    CONSTRAINT ck_catalog_services_name_not_blank
        CHECK (length(btrim(service_name)) > 0),
    CONSTRAINT ck_catalog_services_category
        CHECK (category IN ('COMPLIANCE', 'TAX', 'AUDIT', 'ACCOUNTING', 'ADVISORY')),
    CONSTRAINT ck_catalog_services_billing_frequency
        CHECK (billing_frequency IN ('ONE_OFF', 'MONTHLY', 'QUARTERLY', 'ANNUAL')),
    CONSTRAINT ck_catalog_services_standard_fee
        CHECK (standard_fee >= 0),
    CONSTRAINT ck_catalog_services_gst_rate
        CHECK (gst_rate_percent >= 0 AND gst_rate_percent <= 100),
    CONSTRAINT ck_catalog_services_day_of_month
        CHECK (invoice_day_of_month IS NULL OR invoice_day_of_month BETWEEN 1 AND 31),
    CONSTRAINT ck_catalog_services_month_of_quarter
        CHECK (invoice_month_of_quarter IS NULL
               OR invoice_month_of_quarter IN ('FIRST_MONTH', 'SECOND_MONTH', 'THIRD_MONTH')),
    CONSTRAINT ck_catalog_services_used_by_companies
        CHECK (used_by_companies >= 0),
    -- The invoice schedule columns are meaningful only for certain billing frequencies, so the
    -- database holds that relationship rather than trusting every writer to get it right.
    CONSTRAINT ck_catalog_services_schedule_matches_billing CHECK (
        (billing_frequency = 'ONE_OFF'
            AND invoice_day_of_month IS NULL
            AND invoice_month_of_quarter IS NULL
            AND invoice_month IS NULL)
        OR (billing_frequency = 'MONTHLY'
            AND invoice_day_of_month IS NOT NULL
            AND invoice_month_of_quarter IS NULL
            AND invoice_month IS NULL)
        OR (billing_frequency = 'QUARTERLY'
            AND invoice_day_of_month IS NOT NULL
            AND invoice_month_of_quarter IS NOT NULL
            AND invoice_month IS NULL)
        OR (billing_frequency = 'ANNUAL'
            AND invoice_day_of_month IS NOT NULL
            AND invoice_month_of_quarter IS NULL
            AND invoice_month IS NOT NULL))
);

-- Service names identify a catalog entry to staff, so they must not repeat under a different
-- capitalisation.
CREATE UNIQUE INDEX ux_catalog_services_name ON catalog_services (lower(service_name));

-- The catalog is browsed and filtered by category.
CREATE INDEX ix_catalog_services_category ON catalog_services (category);
