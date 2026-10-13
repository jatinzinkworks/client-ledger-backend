-- Ledger entries: hold payments received alongside the service charges they settle.
--
-- entry_type tells the two apart. A SERVICE_CHARGE has every service figure filled in and a
-- positive total, exactly as before. A PAYMENT has none of them; it records how the money arrived,
-- and its total is the amount received as a negative number, so adding up a ledger's totals gives
-- the balance still owed. For a payment, due_date holds the date it was received, so date filters,
-- ordering and the financial year treat both kinds alike.
ALTER TABLE ledger_entries
    ADD COLUMN entry_type        VARCHAR(20),
    ADD COLUMN payment_method    VARCHAR(20),
    ADD COLUMN payment_reference VARCHAR(100),
    ADD COLUMN comment           VARCHAR(1000);

-- Every existing entry is a service charge.
UPDATE ledger_entries
SET entry_type = 'SERVICE_CHARGE';

ALTER TABLE ledger_entries
    ALTER COLUMN entry_type SET NOT NULL,
    ALTER COLUMN company_service_id DROP NOT NULL,
    ALTER COLUMN catalog_service_id DROP NOT NULL,
    ALTER COLUMN service_name DROP NOT NULL,
    ALTER COLUMN service_type DROP NOT NULL,
    ALTER COLUMN service_fee DROP NOT NULL,
    ALTER COLUMN discount_percent DROP NOT NULL,
    ALTER COLUMN discount_amount DROP NOT NULL,
    ALTER COLUMN taxable_amount DROP NOT NULL,
    ALTER COLUMN gst_rate_percent DROP NOT NULL,
    ALTER COLUMN gst_amount DROP NOT NULL,
    ADD CONSTRAINT ck_ledger_entries_entry_type
        CHECK (entry_type IN ('SERVICE_CHARGE', 'PAYMENT')),
    ADD CONSTRAINT ck_ledger_entries_payment_method
        CHECK (payment_method IN ('BANK_TRANSFER', 'UPI', 'CHEQUE', 'CASH')),
    -- Replaces the amounts check from V11, which assumed every entry was a service charge.
    DROP CONSTRAINT ck_ledger_entries_amounts,
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
    );
