package com.psc.cl.ledger.model;

/**
 * What a ledger entry records, which decides which of its fields are filled in.
 */
public enum LedgerEntryType {

    /** An amount owed for a service that fell due; a positive total, with every service figure filled in. */
    SERVICE_CHARGE,

    /** An amount received from the company; a negative total, with the payment details filled in. */
    PAYMENT
}
