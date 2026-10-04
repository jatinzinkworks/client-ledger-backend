package com.psc.cl.managercatalog.exception;

import com.psc.cl.exception.ResourceInUseException;

import java.util.UUID;

/**
 * Raised when a manager is deleted while clients are still assigned to them.
 */
public class ManagerHasClientsException extends ResourceInUseException {

    public ManagerHasClientsException(UUID id, long assignedClients) {
        super("Manager " + id + " is assigned to " + assignedClients
                + " client(s) and cannot be deleted. Reassign the clients first.");
    }

    /**
     * Used when the database rejects the delete and the number of clients is not known.
     *
     * @param id the manager that could not be deleted
     */
    public ManagerHasClientsException(UUID id) {
        super("Manager " + id + " is assigned to one or more clients and cannot be deleted. "
                + "Reassign the clients first.");
    }
}
