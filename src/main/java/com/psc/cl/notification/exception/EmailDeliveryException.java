package com.psc.cl.notification.exception;

import com.psc.cl.exception.ExternalServiceException;

/**
 * Raised when an email could not be sent: the SMTP server refused or could not be reached, or
 * email has not been configured. Reported as 502 Bad Gateway.
 */
public class EmailDeliveryException extends ExternalServiceException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
