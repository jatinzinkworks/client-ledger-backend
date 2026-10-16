package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceNotFoundException;

import java.util.Collection;
import java.util.UUID;

/**
 * Raised when an operation on several clients names identifiers that do not exist, listing every
 * one of them so they can all be corrected at once.
 */
public class ClientsNotFoundException extends ResourceNotFoundException {

    public ClientsNotFoundException(Collection<UUID> ids) {
        super("No clients found with ids " + ids + ".");
    }
}
