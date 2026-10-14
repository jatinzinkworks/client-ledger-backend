package com.psc.cl.ledger.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * What was billed, received and discounted across every client in one calendar month: the ledger's
 * figures offered to other modules, such as the overview's monthly trend.
 *
 * @param month    the calendar month
 * @param billed   sum of the service charges falling due in the month, GST included, after discount
 * @param received sum of the payments received in the month, as a positive number
 * @param discount sum of the discount given on the service charges falling due in the month
 */
public record MonthlyTotals(YearMonth month, BigDecimal billed, BigDecimal received, BigDecimal discount) {
}
