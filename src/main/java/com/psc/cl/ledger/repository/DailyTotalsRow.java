package com.psc.cl.ledger.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What was billed, received and discounted across every client on one day.
 */
public interface DailyTotalsRow {

    /** @return the day: the date charges fell due on, or payments were received on, as held in due_date */
    LocalDate getDueDate();

    /** @return the sum of the day's service charges, GST included, after discount */
    BigDecimal getTotalBilled();

    /** @return the sum of the day's payments, as stored: negative */
    BigDecimal getTotalPaid();

    /** @return the sum of the discount given on the day's service charges */
    BigDecimal getTotalDiscount();
}
