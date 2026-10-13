package com.psc.cl.ledger.exception;

import com.psc.cl.exception.InvalidRequestException;

import java.time.LocalDate;

/**
 * Raised when a payment is recorded as received on a date that has not come yet.
 */
public class FuturePaymentDateException extends InvalidRequestException {

    public FuturePaymentDateException(LocalDate dateReceived, LocalDate today) {
        super("dateReceived " + dateReceived + " is after today, " + today + ".");
    }
}
