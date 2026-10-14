package com.psc.cl.notification.exception;

import com.psc.cl.exception.ExternalServiceException;

/**
 * Raised when a WhatsApp message could not be sent: WhatsApp is not configured, the WhatsApp Cloud
 * API refused the message or could not be reached, or the recipient's phone number cannot be used.
 * Reported as 502 Bad Gateway when it ends a request.
 */
public class WhatsAppDeliveryException extends ExternalServiceException {

    public WhatsAppDeliveryException(String message) {
        super(message);
    }

    public WhatsAppDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
