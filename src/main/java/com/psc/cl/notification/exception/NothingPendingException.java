package com.psc.cl.notification.exception;

import com.psc.cl.exception.BusinessRuleViolationException;

import java.util.UUID;

/**
 * Raised when a client would be reminded to pay but owes nothing across every financial year: settled,
 * in credit, or never billed. Reported as 409 Conflict.
 */
public class NothingPendingException extends BusinessRuleViolationException {

    public NothingPendingException(UUID clientId) {
        super("Client " + clientId + " has nothing pending, so there is nothing to remind them of");
    }
}
