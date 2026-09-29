-- Global Settings: tenant-wide payment terms.
CREATE TABLE payment_terms (
    id                         UUID        NOT NULL,
    payment_due_after_days     INTEGER     NOT NULL DEFAULT 15,
    mark_overdue_after_days    INTEGER     NOT NULL DEFAULT 60,
    payment_reminder_enabled   BOOLEAN     NOT NULL DEFAULT FALSE,
    payment_reminder_days      INTEGER,
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_payment_terms PRIMARY KEY (id),
    CONSTRAINT ck_payment_terms_due_after_days
        CHECK (payment_due_after_days BETWEEN 1 AND 365),
    CONSTRAINT ck_payment_terms_overdue_after_days
        CHECK (mark_overdue_after_days BETWEEN 1 AND 365),
    CONSTRAINT ck_payment_terms_reminder_days
        CHECK (payment_reminder_days IS NULL OR payment_reminder_days BETWEEN 1 AND 365),
    CONSTRAINT ck_payment_terms_reminder_days_required
        CHECK (payment_reminder_enabled = FALSE OR payment_reminder_days IS NOT NULL)
);

-- Payment terms are a singleton global setting. A unique index over a constant expression lets
-- the database hold that invariant, so two concurrent creates cannot both succeed.
CREATE UNIQUE INDEX ux_payment_terms_singleton ON payment_terms ((TRUE));
