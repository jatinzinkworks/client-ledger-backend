package com.psc.cl.client.exception;

import com.psc.cl.exception.BusinessRuleViolationException;

import java.util.UUID;

/**
 * Raised when a company would be added to a client that already holds the most companies allowed.
 */
public class CompanyLimitExceededException extends BusinessRuleViolationException {

    public CompanyLimitExceededException(UUID clientId, int limit) {
        super("Client " + clientId + " already holds " + limit + " companies, the most allowed. "
                + "Delete a company before adding another.");
    }
}
