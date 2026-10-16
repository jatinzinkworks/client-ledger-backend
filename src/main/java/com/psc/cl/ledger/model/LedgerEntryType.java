package com.psc.cl.ledger.model;

/**
 * What a ledger entry records, which decides which of its fields are filled in.
 */
public enum LedgerEntryType {

    /** An amount owed for a service that fell due; a positive total, with every service figure filled in. */
    SERVICE_CHARGE,

    /** An amount received from the company; a negative total, with the payment details filled in. */
    PAYMENT,

    /**
     * What a company owed, or was owed, when it was taken on: the balance brought over from before
     * the firm kept its ledger here. Positive when the company owes it, negative when it starts in
     * credit; never zero. No service or payment field is filled in. At most one per company.
     */
    OPENING_BALANCE
}
