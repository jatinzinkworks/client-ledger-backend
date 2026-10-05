-- Company services: record how the service is billed and when it next falls due.
--
-- service_type is the billing frequency of the catalog service. next_due_date is the first date
-- the catalog's invoice schedule falls on, on or after the delivery date; a ONE_OFF service is due
-- on its delivery date. The application works both out on every write. The UPDATE below fills
-- them in for rows that already exist, using the same rules, so the columns can be NOT NULL.
ALTER TABLE company_services
    ADD COLUMN service_type  VARCHAR(20),
    ADD COLUMN next_due_date DATE;

UPDATE company_services cs
SET service_type  = s.billing_frequency,
    next_due_date = CASE
        WHEN s.billing_frequency = 'ONE_OFF' THEN cs.delivery_date
        ELSE (
            -- Every month from the delivery month to a year on: an annual schedule is always met
            -- within that span. In months shorter than the invoice day, the last day is used.
            SELECT min(candidate.due_date)
            FROM generate_series(0, 12) AS offsets(n)
            CROSS JOIN LATERAL (
                SELECT (date_trunc('month', cs.delivery_date) + make_interval(months => offsets.n))::date
                           AS month_start
            ) AS months_ahead
            CROSS JOIN LATERAL (
                SELECT (months_ahead.month_start
                            + (least(s.invoice_day_of_month,
                                     extract(day FROM months_ahead.month_start + interval '1 month - 1 day')::int)
                               - 1))::date AS due_date
            ) AS candidate
            WHERE candidate.due_date >= cs.delivery_date
              AND (s.billing_frequency = 'MONTHLY'
                   -- For the quarter ending March, FIRST_MONTH is April: months 1, 4, 7 and 10.
                   OR (s.billing_frequency = 'QUARTERLY'
                       AND (extract(month FROM months_ahead.month_start)::int - 1) % 3
                           = array_position(ARRAY['FIRST_MONTH', 'SECOND_MONTH', 'THIRD_MONTH'],
                                            s.invoice_month_of_quarter::text) - 1)
                   OR (s.billing_frequency = 'ANNUAL'
                       AND extract(month FROM months_ahead.month_start)::int
                           = array_position(ARRAY['JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY',
                                                  'JUNE', 'JULY', 'AUGUST', 'SEPTEMBER',
                                                  'OCTOBER', 'NOVEMBER', 'DECEMBER'],
                                            s.invoice_month::text)))
        )
    END
FROM catalog_services s
WHERE s.id = cs.catalog_service_id;

ALTER TABLE company_services
    ALTER COLUMN service_type SET NOT NULL,
    ALTER COLUMN next_due_date SET NOT NULL,
    ADD CONSTRAINT ck_company_services_service_type
        CHECK (service_type IN ('ONE_OFF', 'MONTHLY', 'QUARTERLY', 'ANNUAL'));

-- Services falling due are looked up by date, across every company.
CREATE INDEX ix_company_services_next_due_date ON company_services (next_due_date);
