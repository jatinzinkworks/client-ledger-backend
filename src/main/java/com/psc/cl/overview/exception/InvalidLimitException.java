package com.psc.cl.overview.exception;

import com.psc.cl.exception.InvalidRequestException;

/**
 * Raised when a ranking or list is asked for fewer entries than it allows, or more than it returns
 * at once.
 */
public class InvalidLimitException extends InvalidRequestException {

    public InvalidLimitException(int limit, int max) {
        this("limit", limit, 1, max);
    }

    public InvalidLimitException(String parameter, int value, int min, int max) {
        super(parameter + " must be from " + min + " to " + max + ", but was " + value + ".");
    }
}
