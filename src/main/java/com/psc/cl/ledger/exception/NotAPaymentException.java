package com.psc.cl.ledger.exception;

import com.psc.cl.exception.BusinessRuleViolationException;

import java.util.UUID;

/**
 * Raised when a service charge is to be updated as though it were a payment.
 */
public class NotAPaymentException extends BusinessRuleViolationException {

    public NotAPaymentException(UUID entryId) {
        super("Ledger entry " + entryId + " is a service charge, not a payment. Correct its rates through its "
                + "ledger entry instead.");
    }
}
