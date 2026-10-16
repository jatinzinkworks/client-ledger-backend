-- Service Catalog: allow the CERTIFICATION category.
--
-- V3 limited category to the original five practice areas. A check constraint cannot be altered in
-- place, so it is dropped and recreated with the new value.
ALTER TABLE catalog_services
    DROP CONSTRAINT ck_catalog_services_category;

ALTER TABLE catalog_services
    ADD CONSTRAINT ck_catalog_services_category
        CHECK (category IN ('COMPLIANCE', 'TAX', 'AUDIT', 'ACCOUNTING', 'ADVISORY', 'CERTIFICATION'));
