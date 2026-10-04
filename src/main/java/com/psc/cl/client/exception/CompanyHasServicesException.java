package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceInUseException;

import java.util.UUID;

/**
 * Raised when companies that still have services assigned would be removed.
 */
public class CompanyHasServicesException extends ResourceInUseException {

    /**
     * For a client update that would drop such companies.
     *
     * @param clientId the client being updated
     */
    public CompanyHasServicesException(UUID clientId) {
        super("Client " + clientId + " has services assigned to one or more of the companies the "
                + "update would remove. Keep those companies in the request.");
    }

    /**
     * For a company deleted directly.
     *
     * @param clientId  the client the company belongs to
     * @param companyId the company being deleted
     */
    public CompanyHasServicesException(UUID clientId, UUID companyId) {
        super("Company " + companyId + " of client " + clientId + " has services assigned and "
                + "cannot be deleted. Remove those services first.");
    }
}
