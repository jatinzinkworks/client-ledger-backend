package com.psc.cl.client.exception;

import com.psc.cl.exception.BusinessRuleViolationException;

/**
 * Raised when the next client id is asked for but the highest held is already the last the format allows.
 */
public class ClientIdsExhaustedException extends BusinessRuleViolationException {

    public ClientIdsExhaustedException(String highest) {
        super("Client id " + highest + " is the last available; no further client id can be given.");
    }
}
