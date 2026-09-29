package com.psc.cl.globalsettings.exception;

import com.psc.cl.exception.ResourceNotFoundException;

/**
 * Raised when firm details are read before any have been saved.
 */
public class FirmDetailsNotFoundException extends ResourceNotFoundException {

    public FirmDetailsNotFoundException() {
        super("No firm details have been configured yet.");
    }
}
