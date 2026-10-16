package com.psc.cl.ledger.exception;

import com.psc.cl.exception.BusinessRuleViolationException;

import java.util.UUID;

/**
 * Raised when the rates of an opening balance are to be corrected; it has none, being a single
 * amount brought over. A wrong opening balance is deleted instead.
 */
public class OpeningBalanceNotCorrectableException extends BusinessRuleViolationException {

    public OpeningBalanceNotCorrectableException(UUID entryId) {
        super("Ledger entry " + entryId + " is an opening balance and has no rates to correct. Delete it if it "
                + "is wrong.");
    }
}
