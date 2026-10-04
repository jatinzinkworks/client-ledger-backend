package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceNotFoundException;

import java.util.UUID;

/**
 * Raised when a client is assigned to a manager that does not exist in the Manager Catalog.
 */
public class AssignedManagerNotFoundException extends ResourceNotFoundException {

    public AssignedManagerNotFoundException(UUID managerId) {
        super("No manager found with id " + managerId + " to assign to the client.");
    }
}
