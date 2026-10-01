package com.psc.cl.servicecatalog.exception;

import com.psc.cl.exception.ResourceAlreadyExistsException;

/**
 * Raised when a service is added under a name another catalog entry already uses.
 */
public class ServiceNameAlreadyExistsException extends ResourceAlreadyExistsException {

    public ServiceNameAlreadyExistsException(String serviceName) {
        super("A service named '" + serviceName + "' already exists in the catalog.");
    }
}
