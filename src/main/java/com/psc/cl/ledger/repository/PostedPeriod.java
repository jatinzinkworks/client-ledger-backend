package com.psc.cl.ledger.repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A due date of a company service that already has a ledger entry.
 */
public interface PostedPeriod {

    /** @return the service as assigned to the company */
    UUID getCompanyServiceId();

    /** @return the due date billed */
    LocalDate getDueDate();
}
