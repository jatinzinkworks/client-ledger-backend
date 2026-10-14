package com.psc.cl.ledger.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What one client was billed and paid over a range of dates, and what remains.
 */
public interface ClientBalanceRow {

    /** @return the client */
    UUID getClientId();

    /** @return the sum of the client's service charges in the range, GST included */
    BigDecimal getTotalBilled();

    /** @return the sum of the client's payments in the range, as stored: negative */
    BigDecimal getTotalPaid();

    /** @return billed less received */
    BigDecimal getPending();
}
