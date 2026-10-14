package com.psc.cl.ledger.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What one company has paid in total.
 */
public interface CompanyPaidRow {

    /** @return the company */
    UUID getCompanyId();

    /** @return the sum of the company's payments, as stored: negative */
    BigDecimal getTotalPaid();
}
