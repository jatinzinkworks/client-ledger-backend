package com.psc.cl.ledger.model;

import java.time.LocalDate;

/**
 * An Indian financial year, as used under the Income-tax Act: 1 April to 31 March.
 *
 * @param label    the year as written on invoices and returns, such as {@code 2026-27}
 * @param firstDay 1 April of the first calendar year
 * @param lastDay  31 March of the second calendar year
 */
public record FinancialYear(String label, LocalDate firstDay, LocalDate lastDay) {
}
