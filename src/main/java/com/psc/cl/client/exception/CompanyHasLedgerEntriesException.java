package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceInUseException;

import java.util.UUID;

/**
 * Raised when a company that still has entries in its ledger, such as payments or an opening
 * balance, would be removed. A ledger is a financial record, so it is never removed along with the
 * company.
 */
public class CompanyHasLedgerEntriesException extends ResourceInUseException {

    /**
     * @param clientId  the client the company belongs to
     * @param companyId the company being deleted
     */
    public CompanyHasLedgerEntriesException(UUID clientId, UUID companyId) {
        super("Company " + companyId + " of client " + clientId + " has ledger entries, such as payments or "
                + "an opening balance, and cannot be deleted. Delete those entries first if they were recorded "
                + "in error.");
    }
}
