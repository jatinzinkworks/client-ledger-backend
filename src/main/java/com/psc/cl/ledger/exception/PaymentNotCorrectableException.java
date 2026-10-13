package com.psc.cl.ledger.exception;

import com.psc.cl.exception.BusinessRuleViolationException;

import java.util.UUID;

/**
 * Raised when the rates of a payment are to be corrected; only service charges have rates.
 */
public class PaymentNotCorrectableException extends BusinessRuleViolationException {

    public PaymentNotCorrectableException(UUID entryId) {
        super("Ledger entry " + entryId + " is a payment and has no rates to correct. Update it through its "
                + "payment instead.");
    }
}
