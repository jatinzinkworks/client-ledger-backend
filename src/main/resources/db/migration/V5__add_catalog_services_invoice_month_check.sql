-- Service Catalog: constrain invoice_month to the InvoiceMonth enum.
--
-- V3 constrained category, billing_frequency and invoice_month_of_quarter but left invoice_month
-- unchecked, so the column would accept any 20 character string written outside the application.
-- This closes that gap; it is a separate migration because V3 has already been applied.
ALTER TABLE catalog_services
    ADD CONSTRAINT ck_catalog_services_invoice_month
        CHECK (invoice_month IS NULL
               OR invoice_month IN ('JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE',
                                    'JULY', 'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER',
                                    'DECEMBER'));
