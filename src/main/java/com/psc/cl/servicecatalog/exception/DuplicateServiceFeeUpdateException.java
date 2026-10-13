package com.psc.cl.servicecatalog.exception;

import com.psc.cl.exception.InvalidRequestException;

import java.util.Collection;
import java.util.UUID;

/**
 * Raised when a bulk fee update names the same service more than once, so it is unclear which fee
 * was meant.
 */
public class DuplicateServiceFeeUpdateException extends InvalidRequestException {

    public DuplicateServiceFeeUpdateException(Collection<UUID> serviceIds) {
        super("Each service may appear only once in a fee update; repeated: " + serviceIds + ".");
    }
}
