package com.psc.cl.servicecatalog.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How often a catalog service is billed, which in turn decides the shape of its invoice schedule.
 */
@Schema(name = "BillingFrequency", description = "How often the service is billed")
public enum BillingFrequency {

    /** Billed once on completion; carries no invoice schedule. */
    ONE_OFF,

    /** Billed every month on a given day. */
    MONTHLY,

    /** Billed every quarter, in a chosen month after the quarter ends, on a given day. */
    QUARTERLY,

    /** Billed once a year, in a chosen month, on a given day. */
    ANNUAL;

    /** @return true when this frequency requires an invoice schedule */
    public boolean requiresInvoiceSchedule() {
        return this != ONE_OFF;
    }
}
