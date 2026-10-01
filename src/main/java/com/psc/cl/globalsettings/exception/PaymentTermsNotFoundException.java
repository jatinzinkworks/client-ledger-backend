package com.psc.cl.globalsettings.exception;

import com.psc.cl.exception.ResourceNotFoundException;

/**
 * Raised when payment terms are read before any have been saved.
 */
public class PaymentTermsNotFoundException extends ResourceNotFoundException {

    public PaymentTermsNotFoundException() {
        super("No payment terms have been configured yet.");
    }
}
