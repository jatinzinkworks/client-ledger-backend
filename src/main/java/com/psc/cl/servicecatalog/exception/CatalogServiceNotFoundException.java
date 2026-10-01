package com.psc.cl.servicecatalog.exception;

import com.psc.cl.exception.ResourceNotFoundException;

import java.util.UUID;

/**
 * Raised when a catalog service is addressed by an identifier that does not exist.
 */
public class CatalogServiceNotFoundException extends ResourceNotFoundException {

    public CatalogServiceNotFoundException(UUID id) {
        super("No service found with id " + id + ".");
    }
}
