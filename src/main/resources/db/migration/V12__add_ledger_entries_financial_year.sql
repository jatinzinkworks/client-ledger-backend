-- Ledger entries: record the Indian financial year (1 April to 31 March) each entry falls in,
-- taken from its due date and labelled the way it is written on returns, such as '2026-27'.
--
-- The application works it out when an entry is posted. The UPDATE below fills it in for entries
-- that already exist, using the same rule, so the column can be NOT NULL.
ALTER TABLE ledger_entries
    ADD COLUMN financial_year VARCHAR(7);

UPDATE ledger_entries le
SET financial_year = fy.start_year || '-' || lpad(((fy.start_year + 1) % 100)::text, 2, '0')
FROM (
    SELECT id,
           CASE WHEN extract(month FROM due_date) >= 4
                THEN extract(year FROM due_date)::int
                ELSE extract(year FROM due_date)::int - 1
           END AS start_year
    FROM ledger_entries
) AS fy
WHERE fy.id = le.id;

ALTER TABLE ledger_entries
    ALTER COLUMN financial_year SET NOT NULL,
    ADD CONSTRAINT ck_ledger_entries_financial_year
        CHECK (financial_year ~ '^[0-9]{4}-[0-9]{2}$');

-- Ledgers are now listed by due date range, so the client's index leads with due date rather than
-- entry date, matching the company's.
DROP INDEX ix_ledger_entries_client_entry_date;
CREATE INDEX ix_ledger_entries_client_due_date ON ledger_entries (client_id, due_date);
