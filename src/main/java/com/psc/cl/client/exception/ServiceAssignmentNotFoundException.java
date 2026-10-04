package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceNotFoundException;

import java.util.UUID;

/**
 * Raised when an assigned service is addressed under a company that does not hold it.
 */
public class ServiceAssignmentNotFoundException extends ResourceNotFoundException {

    public ServiceAssignmentNotFoundException(UUID companyId, UUID assignmentId) {
        super("No service assignment found with id " + assignmentId + " for company " + companyId + ".");
    }
}
