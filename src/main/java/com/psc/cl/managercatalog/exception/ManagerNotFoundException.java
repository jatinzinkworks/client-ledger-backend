package com.psc.cl.managercatalog.exception;

import com.psc.cl.exception.ResourceNotFoundException;

import java.util.UUID;

/**
 * Raised when a manager is addressed by an identifier that does not exist.
 */
public class ManagerNotFoundException extends ResourceNotFoundException {

    public ManagerNotFoundException(UUID id) {
        super("No manager found with id " + id + ".");
    }
}
