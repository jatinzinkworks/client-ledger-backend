-- Client companies: a company's discount becomes optional.
--
-- NULL now means the company has no discount of its own and takes its client's current discount,
-- worked out when it is read, so a later change to the client's discount reaches it. Existing
-- rows keep the value copied onto them when they were written: whether that value was inherited
-- or chosen was never recorded, so it is kept as the company's own. The check constraint already
-- accepts NULL.
ALTER TABLE client_companies
    ALTER COLUMN discount_percent DROP NOT NULL,
    ALTER COLUMN discount_percent DROP DEFAULT;
