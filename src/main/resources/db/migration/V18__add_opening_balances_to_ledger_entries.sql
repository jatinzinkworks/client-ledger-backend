-- Ledger entries: hold the balance a company was taken on with, brought over from before the firm
-- kept its ledger here.
--
-- An OPENING_BALANCE has no service figures and no payment details. Its total is the balance
-- brought over: positive when the company owes it, negative when it starts in credit, so adding up
-- a ledger's totals still gives the balance owed. Its due_date is the date the balance stood at.
ALTER TABLE ledger_entries
    DROP CONSTRAINT ck_ledger_entries_entry_type,
    ADD CONSTRAINT ck_ledger_entries_entry_type
        CHECK (entry_type IN ('SERVICE_CHARGE', 'PAYMENT', 'OPENING_BALANCE')),
    -- Replaces the shape check from V14, adding the third shape.
    DROP CONSTRAINT ck_ledger_entries_shape,
    ADD CONSTRAINT ck_ledger_entries_shape CHECK (
        (entry_type = 'SERVICE_CHARGE'
            AND company_service_id IS NOT NULL AND catalog_service_id IS NOT NULL
            AND service_name IS NOT NULL AND service_type IS NOT NULL
            AND service_fee IS NOT NULL AND discount_percent IS NOT NULL AND discount_amount IS NOT NULL
            AND taxable_amount IS NOT NULL AND gst_rate_percent IS NOT NULL AND gst_amount IS NOT NULL
            AND payment_method IS NULL AND payment_reference IS NULL AND comment IS NULL
            AND service_fee >= 0 AND discount_amount >= 0 AND gst_amount >= 0
            AND taxable_amount = service_fee - discount_amount
            AND total_amount = taxable_amount + gst_amount)
        OR
        (entry_type = 'PAYMENT'
            AND company_service_id IS NULL AND catalog_service_id IS NULL
            AND service_name IS NULL AND service_type IS NULL
            AND service_fee IS NULL AND discount_percent IS NULL AND discount_amount IS NULL
            AND taxable_amount IS NULL AND gst_rate_percent IS NULL AND gst_amount IS NULL
            AND payment_method IS NOT NULL
            AND total_amount < 0)
        OR
        (entry_type = 'OPENING_BALANCE'
            AND company_service_id IS NULL AND catalog_service_id IS NULL
            AND service_name IS NULL AND service_type IS NULL
            AND service_fee IS NULL AND discount_percent IS NULL AND discount_amount IS NULL
            AND taxable_amount IS NULL AND gst_rate_percent IS NULL AND gst_amount IS NULL
            AND payment_method IS NULL AND payment_reference IS NULL AND comment IS NULL
            AND total_amount <> 0)
    );

-- A company is taken on once, so it has at most one opening balance.
CREATE UNIQUE INDEX uq_ledger_entries_opening_balance
    ON ledger_entries (company_id) WHERE entry_type = 'OPENING_BALANCE';
