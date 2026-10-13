-- Ledger entries: the Service Catalog totals what each service was billed in a financial year
-- across every client, filtering on due date alone, which the client and company indexes cannot
-- serve because they lead with their own column.
CREATE INDEX ix_ledger_entries_due_date ON ledger_entries (due_date);
