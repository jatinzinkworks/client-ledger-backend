package com.psc.cl.notification.exception;

import com.psc.cl.exception.BusinessRuleViolationException;

import java.util.UUID;

/**
 * Raised when a client would be sent a WhatsApp message without having agreed to receive them, which
 * WhatsApp's business policy forbids. Reported as 409 Conflict.
 */
public class WhatsAppNotOptedInException extends BusinessRuleViolationException {

    public WhatsAppNotOptedInException(UUID clientId) {
        super("Client " + clientId + " has not opted in to WhatsApp messages; set whatsappOptIn on the client "
                + "once they agree");
    }
}
