package com.psc.cl.ledger.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What one company service was billed over a range of due dates.
 */
public interface ServiceBilledRow {

    /** @return the service as assigned to the company */
    UUID getCompanyServiceId();

    /** @return the sum of its service charges in the range, GST included */
    BigDecimal getTotalBilled();
}
