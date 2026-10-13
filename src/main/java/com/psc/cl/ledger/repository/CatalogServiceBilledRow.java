package com.psc.cl.ledger.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What one catalog service was billed over a range of due dates, across every client.
 */
public interface CatalogServiceBilledRow {

    /** @return the service in the Service Catalog */
    UUID getCatalogServiceId();

    /** @return the sum of its service charges in the range, GST included */
    BigDecimal getTotalBilled();
}
