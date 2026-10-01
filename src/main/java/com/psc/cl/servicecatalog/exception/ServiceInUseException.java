package com.psc.cl.servicecatalog.exception;

import com.psc.cl.exception.ResourceInUseException;

/**
 * Raised when a service is deleted while companies still subscribe to it.
 */
public class ServiceInUseException extends ResourceInUseException {

    public ServiceInUseException(String serviceName, int usedByCompanies) {
        super("Service '" + serviceName + "' is used by " + usedByCompanies
                + " company(ies) and cannot be deleted.");
    }
}
