package com.psc.cl.servicecatalog.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Which month after a quarter closes the invoice is raised in.
 *
 * <p>For the quarter ending March, {@code FIRST_MONTH} is April, {@code SECOND_MONTH} is May and
 * {@code THIRD_MONTH} is June.
 */
@Schema(name = "QuarterInvoiceMonth",
        description = "Which month after the quarter closes the invoice is raised in")
public enum QuarterInvoiceMonth {

    FIRST_MONTH,
    SECOND_MONTH,
    THIRD_MONTH
}
