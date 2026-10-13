package com.psc.cl.servicecatalog.exception;

import com.psc.cl.exception.ResourceNotFoundException;

import java.util.Collection;
import java.util.UUID;

/**
 * Raised when an operation on several catalog services names identifiers that do not exist,
 * listing every one of them so they can all be corrected at once.
 */
public class CatalogServicesNotFoundException extends ResourceNotFoundException {

    public CatalogServicesNotFoundException(Collection<UUID> ids) {
        super("No services found with ids " + ids + ".");
    }
}
