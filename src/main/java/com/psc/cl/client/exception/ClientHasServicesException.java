package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceInUseException;

import java.util.UUID;

/**
 * Raised when a client is deleted while services are still assigned to any of their companies.
 */
public class ClientHasServicesException extends ResourceInUseException {

    public ClientHasServicesException(UUID clientId) {
        super("Client " + clientId + " has services assigned to one or more companies and cannot be "
                + "deleted. Remove those services first.");
    }
}
