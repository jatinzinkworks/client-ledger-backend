package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceAlreadyExistsException;

/**
 * Raised when a client is added under a client id another client already holds.
 */
public class ClientIdAlreadyExistsException extends ResourceAlreadyExistsException {

    public ClientIdAlreadyExistsException(String clientId) {
        super("A client with client id " + clientId + " already exists.");
    }
}
