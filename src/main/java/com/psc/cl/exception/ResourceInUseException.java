package com.psc.cl.exception;

/**
 * Raised when a resource cannot be removed because other records still depend on it.
 */
public class ResourceInUseException extends RuntimeException {

    public ResourceInUseException(String message) {
        super(message);
    }
}
