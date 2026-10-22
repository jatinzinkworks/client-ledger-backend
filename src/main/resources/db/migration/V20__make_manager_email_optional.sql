-- A manager's email is optional. The shape check passes a NULL, and the unique index on lower(email)
-- lets any number of managers go without one.
ALTER TABLE managers ALTER COLUMN email DROP NOT NULL;
