package com.psc.cl.ledger.repository;

import com.psc.cl.ledger.model.LedgerEntryType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Totals of the ledger entries of one type, for one company in one financial year.
 *
 * <p>Payments carry no service figures, so on a row of payments every sum but
 * {@link #getTotalAmount()} is null, and that one is negative.
 */
public interface LedgerTotalsRow {

    /** @return the company the entries were billed to */
    UUID getCompanyId();

    /** @return the financial year the entries' due dates fall in, such as 2026-27 */
    String getFinancialYear();

    /** @return whether the row totals service charges or payments */
    LedgerEntryType getEntryType();

    /** @return the number of entries */
    long getEntryCount();

    /** @return the sum of the service fees */
    BigDecimal getTotalServiceFee();

    /** @return the sum of the discount amounts */
    BigDecimal getTotalDiscountAmount();

    /** @return the sum of the taxable amounts */
    BigDecimal getTotalTaxableAmount();

    /** @return the sum of the GST amounts */
    BigDecimal getTotalGstAmount();

    /** @return the sum of the amounts owed */
    BigDecimal getTotalAmount();
}
