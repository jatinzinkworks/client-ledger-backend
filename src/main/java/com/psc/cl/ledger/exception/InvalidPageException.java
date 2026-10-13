package com.psc.cl.ledger.exception;

import com.psc.cl.exception.InvalidRequestException;

/**
 * Raised when a list is asked for a page before the first, or for pages of an unsupported size.
 */
public class InvalidPageException extends InvalidRequestException {

    public InvalidPageException(String message) {
        super(message);
    }
}
