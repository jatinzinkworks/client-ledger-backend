package com.psc.cl.ledger.exception;

import com.psc.cl.exception.ResourceNotFoundException;

import java.util.UUID;

/**
 * Raised when a ledger entry is addressed under a company it was not billed to.
 */
public class LedgerEntryNotFoundException extends ResourceNotFoundException {

    public LedgerEntryNotFoundException(UUID companyId, UUID entryId) {
        super("No ledger entry found with id " + entryId + " for company " + companyId + ".");
    }
}
