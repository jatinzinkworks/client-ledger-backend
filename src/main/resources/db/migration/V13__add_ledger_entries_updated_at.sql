-- Ledger entries: record when an entry was last changed, now that its rates can be corrected.
--
-- Existing entries have never been changed since they were posted, so they take their created_at.
ALTER TABLE ledger_entries
    ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE ledger_entries
SET updated_at = created_at;

ALTER TABLE ledger_entries
    ALTER COLUMN updated_at SET NOT NULL;
