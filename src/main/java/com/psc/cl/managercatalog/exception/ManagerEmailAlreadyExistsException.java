package com.psc.cl.managercatalog.exception;

import com.psc.cl.exception.ResourceAlreadyExistsException;

/**
 * Raised when a manager is stored under an email address another manager already holds.
 */
public class ManagerEmailAlreadyExistsException extends ResourceAlreadyExistsException {

    public ManagerEmailAlreadyExistsException(String email) {
        super("A manager with email " + email + " already exists.");
    }
}
