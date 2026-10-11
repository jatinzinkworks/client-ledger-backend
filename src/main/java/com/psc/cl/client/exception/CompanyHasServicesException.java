package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceInUseException;

import java.util.UUID;

/**
 * Raised when a company that still has services assigned would be removed.
 */
public class CompanyHasServicesException extends ResourceInUseException {

    /**
     * @param clientId  the client the company belongs to
     * @param companyId the company being deleted
     */
    public CompanyHasServicesException(UUID clientId, UUID companyId) {
        super("Company " + companyId + " of client " + clientId + " has services assigned and "
                + "cannot be deleted. Remove those services first.");
    }
}
