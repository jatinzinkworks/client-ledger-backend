package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceNotFoundException;

import java.util.UUID;

/**
 * Raised when a company is addressed under a client that does not hold it.
 */
public class CompanyNotFoundException extends ResourceNotFoundException {

    public CompanyNotFoundException(UUID clientId, UUID companyId) {
        super("No company found with id " + companyId + " for client " + clientId + ".");
    }
}
