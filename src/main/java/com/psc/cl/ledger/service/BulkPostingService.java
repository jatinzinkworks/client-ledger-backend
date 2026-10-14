package com.psc.cl.ledger.service;

import com.psc.cl.ledger.dto.BulkPostingResponse;

/**
 * Posts due services to the ledgers of every company at once.
 */
public interface BulkPostingService {

    /**
     * Posts the due services of every company of every client, as
     * {@link LedgerService#postDueServices} does for one. Each company is posted on its own, so a
     * failure for one is reported and leaves the others posted.
     *
     * @return how many entries were posted, for which companies, and which companies failed
     */
    BulkPostingResponse postAllDueServices();
}
