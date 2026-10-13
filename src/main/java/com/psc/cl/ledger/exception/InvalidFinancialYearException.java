package com.psc.cl.ledger.exception;

import com.psc.cl.exception.InvalidRequestException;

/**
 * Raised when a financial year filter is not a year written as {@code yyyy-yy}, such as
 * {@code 2026-27}, whose second part follows on from the first.
 */
public class InvalidFinancialYearException extends InvalidRequestException {

    public InvalidFinancialYearException(String financialYear) {
        super("financialYear '" + financialYear + "' is not a financial year such as 2026-27.");
    }
}
