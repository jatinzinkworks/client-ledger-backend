package com.psc.cl.client.exception;

import com.psc.cl.exception.ResourceInUseException;

import java.util.UUID;

/**
 * Raised when a client is deleted while any of their companies still has entries in its ledger,
 * such as payments or an opening balance. A ledger is a financial record, so it is never removed
 * along with the client.
 */
public class ClientHasLedgerEntriesException extends ResourceInUseException {

    public ClientHasLedgerEntriesException(UUID clientId) {
        super("Client " + clientId + " has ledger entries, such as payments or an opening balance, on one or "
                + "more companies and cannot be deleted. Delete those entries first if they were recorded in "
                + "error.");
    }
}
