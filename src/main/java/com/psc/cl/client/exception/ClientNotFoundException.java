package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceNotFoundException;

import java.util.UUID;

/**
 * Raised when a client is addressed by an identifier that does not exist.
 */
public class ClientNotFoundException extends ResourceNotFoundException {

    public ClientNotFoundException(UUID id) {
        super("No client found with id " + id + ".");
    }
}
