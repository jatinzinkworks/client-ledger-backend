package com.psc.cl.ledger.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What one client still owes, across all of their ledger entries.
 */
public interface ClientPendingRow {

    /** @return the client */
    UUID getClientId();

    /** @return charges less payments of every company of the client, across every financial year */
    BigDecimal getTotalPending();
}
