package com.psc.cl.exception;

/**
 * Raised when a service outside this application, such as the SMTP server, fails or cannot be
 * reached, so the request could not be completed through no fault of its own.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
