package com.psc.cl.ledger.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What one company was billed and paid over a range of dates, and what remains.
 */
public interface CompanyBalanceRow {

    /** @return the company */
    UUID getCompanyId();

    /** @return the client the company belongs to */
    UUID getClientId();

    /** @return the sum of the company's service charges in the range, GST included */
    BigDecimal getTotalBilled();

    /** @return the sum of the company's payments in the range, as stored: negative */
    BigDecimal getTotalPaid();

    /** @return billed less received */
    BigDecimal getPending();
}
