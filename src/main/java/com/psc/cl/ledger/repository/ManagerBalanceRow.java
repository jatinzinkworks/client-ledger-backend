package com.psc.cl.ledger.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What the clients of one manager were billed and paid over a range of dates, and what remains.
 */
public interface ManagerBalanceRow {

    /** @return the manager the clients are assigned to */
    UUID getManagerId();

    /** @return the sum of the clients' service charges in the range, GST included */
    BigDecimal getTotalBilled();

    /** @return the sum of the clients' payments in the range, as stored: negative */
    BigDecimal getTotalPaid();

    /** @return billed less received */
    BigDecimal getPending();
}
