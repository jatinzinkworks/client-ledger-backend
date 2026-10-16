package com.psc.cl.ledger.exception;

import com.psc.cl.exception.InvalidRequestException;

import java.time.LocalDate;

/**
 * Raised when an opening balance is dated after today: it is the balance a company stood at when
 * it was taken on, so it cannot lie ahead.
 */
public class FutureOpeningBalanceDateException extends InvalidRequestException {

    public FutureOpeningBalanceDateException(LocalDate openingBalanceDate, LocalDate today) {
        super("openingBalanceDate " + openingBalanceDate + " is after today, " + today + ".");
    }
}
