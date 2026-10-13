package com.psc.cl.ledger.exception;

import com.psc.cl.exception.InvalidRequestException;

/**
 * Raised when a list is asked to sort by a field it cannot sort by, or in an unknown direction.
 */
public class InvalidSortException extends InvalidRequestException {

    public InvalidSortException(String message) {
        super(message);
    }
}
