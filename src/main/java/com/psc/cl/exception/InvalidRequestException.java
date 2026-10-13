package com.psc.cl.exception;

/**
 * Raised when a request is well-formed field by field but its values do not make sense together,
 * for example a date range that ends before it starts.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
