package com.psc.cl.ledger.exception;

import com.psc.cl.exception.InvalidRequestException;

import java.time.LocalDate;

/**
 * Raised when a date range filter ends before it starts.
 */
public class InvalidDateRangeException extends InvalidRequestException {

    public InvalidDateRangeException(LocalDate fromDate, LocalDate toDate) {
        super("fromDate " + fromDate + " is after toDate " + toDate + ".");
    }
}
