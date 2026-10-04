package com.psc.cl.client.exception;

import com.psc.cl.exception.BusinessRuleViolationException;

import java.util.UUID;

/**
 * Raised when the only company of a client would be deleted. A client always holds at least one
 * company.
 */
public class LastCompanyRemovalException extends BusinessRuleViolationException {

    public LastCompanyRemovalException(UUID clientId, UUID companyId) {
        super("Company " + companyId + " is the only company of client " + clientId + " and cannot be "
                + "deleted. A client must keep at least one company; delete the client instead.");
    }
}
