package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceNotFoundException;

import java.util.UUID;

/**
 * Raised when a company is assigned a service that does not exist in the Service Catalog.
 */
public class AssignedServiceNotFoundException extends ResourceNotFoundException {

    public AssignedServiceNotFoundException(UUID serviceId) {
        super("No service found in the Service Catalog with id " + serviceId + ".");
    }
}
