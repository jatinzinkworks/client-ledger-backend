package com.psc.cl.ledger.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What one company was charged for the services falling due on one date.
 */
public interface CompanyChargeRow {

    /** @return the company */
    UUID getCompanyId();

    /** @return the date the charges fell due */
    LocalDate getDueDate();

    /** @return the sum of the company's service charges falling due on the date, GST included */
    BigDecimal getTotalCharged();
}
