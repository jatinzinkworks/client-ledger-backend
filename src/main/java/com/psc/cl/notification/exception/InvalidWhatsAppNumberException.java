package com.psc.cl.notification.exception;

import com.psc.cl.exception.BusinessRuleViolationException;

import java.util.UUID;

/**
 * Raised when the phone number in a client's record cannot be turned into a number WhatsApp accepts.
 * The number itself is left out of the message. Reported as 409 Conflict; correcting the client's
 * phone number resolves it.
 */
public class InvalidWhatsAppNumberException extends BusinessRuleViolationException {

    public InvalidWhatsAppNumberException(UUID clientId) {
        super("The phone number of client " + clientId + " cannot be used for WhatsApp: it needs a country "
                + "code and 8 to 15 digits");
    }
}
