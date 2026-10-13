package com.psc.cl.ledger.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What the clients of one manager still owe, across all of their ledger entries.
 */
public interface ManagerPendingRow {

    /** @return the manager the clients are assigned to */
    UUID getManagerId();

    /** @return charges less payments of every client of the manager, across every financial year */
    BigDecimal getTotalPending();
}
