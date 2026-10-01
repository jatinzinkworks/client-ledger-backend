package com.psc.cl.servicecatalog.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Month;

/**
 * Calendar month an annually billed service is invoiced in.
 *
 * <p>Deliberately a plain enum rather than {@link java.time.Month}: Jackson treats java.time types
 * specially and writes {@code Month.APRIL} as the number {@code 4}, while still reading
 * {@code "APRIL"}. That asymmetry would mean a caller posting {@code "APRIL"} got {@code 4} back.
 * A project enum serialises and deserialises by name in both directions, matching
 * {@link QuarterInvoiceMonth} and the names already stored in the database.
 */
@Schema(name = "InvoiceMonth", description = "Calendar month an annual invoice is raised in")
public enum InvoiceMonth {

    JANUARY,
    FEBRUARY,
    MARCH,
    APRIL,
    MAY,
    JUNE,
    JULY,
    AUGUST,
    SEPTEMBER,
    OCTOBER,
    NOVEMBER,
    DECEMBER;

    /**
     * Converts to the java.time equivalent, for date arithmetic when invoices are generated.
     *
     * @return the matching {@link Month}
     */
    public Month toMonth() {
        return Month.of(ordinal() + 1);
    }

    /**
     * Converts from the java.time equivalent.
     *
     * @param month the month to convert
     * @return the matching {@code InvoiceMonth}
     */
    public static InvoiceMonth from(Month month) {
        return values()[month.getValue() - 1];
    }
}
