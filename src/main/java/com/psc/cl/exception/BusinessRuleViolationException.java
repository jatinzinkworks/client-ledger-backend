package com.psc.cl.exception;

/**
 * Raised when a well-formed request would break a business rule given the current state of the
 * data, for example removing something a record must always have.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
