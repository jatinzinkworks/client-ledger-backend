package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceInUseException;

import java.util.UUID;

/**
 * Raised when a company that has payments or service charges in its ledger would be removed. A
 * ledger is a financial record, so that history is never removed along with the company; an opening
 * balance alone is, as it records no dealings with the firm.
 */
public class CompanyHasLedgerEntriesException extends ResourceInUseException {

    /**
     * @param clientId  the client the company belongs to
     * @param companyId the company being deleted
     */
    public CompanyHasLedgerEntriesException(UUID clientId, UUID companyId) {
        super("Company " + companyId + " of client " + clientId + " has payments or service charges in its "
                + "ledger and cannot be deleted. Delete those entries first if they were recorded in error.");
    }
}
