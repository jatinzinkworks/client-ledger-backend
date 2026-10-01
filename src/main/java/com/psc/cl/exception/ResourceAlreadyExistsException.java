package com.psc.cl.exception;

/**
 * Raised when a caller tries to create a resource whose unique identity is already taken.
 */
public class ResourceAlreadyExistsException extends RuntimeException {

    public ResourceAlreadyExistsException(String message) {
        super(message);
    }
}
