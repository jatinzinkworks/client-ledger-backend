package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceInUseException;

import java.util.UUID;

/**
 * Raised when a client is deleted while any of their companies has payments or service charges in
 * its ledger. A ledger is a financial record, so that history is never removed along with the
 * client; opening balances alone are, as they record no dealings with the firm.
 */
public class ClientHasLedgerEntriesException extends ResourceInUseException {

    public ClientHasLedgerEntriesException(UUID clientId) {
        super("Client " + clientId + " has payments or service charges in the ledger of one or more companies "
                + "and cannot be deleted. Delete those entries first if they were recorded in error.");
    }
}
