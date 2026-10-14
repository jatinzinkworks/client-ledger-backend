package com.psc.cl.notification.service;

import com.psc.cl.notification.dto.BulkLedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.LedgerSummaryEmailResponse;

import java.util.UUID;

/**
 * Sends clients emails about their accounts.
 */
public interface NotificationService {

    /**
     * Emails a client the summary of one of their companies' ledgers for the current financial year,
     * with the year's full ledger attached as an Excel workbook. It goes to the client's email,
     * copying in their manager.
     *
     * @param clientId  the client
     * @param companyId the company of the client to summarise
     * @return who the email went to and what it held
     * @throws com.psc.cl.client.exception.ClientNotFoundException  when no client has this identifier
     * @throws com.psc.cl.client.exception.CompanyNotFoundException when the client has no such company
     * @throws com.psc.cl.notification.exception.EmailDeliveryException when email is not configured, or
     *         the SMTP server refuses the email or cannot be reached
     */
    LedgerSummaryEmailResponse sendLedgerSummary(UUID clientId, UUID companyId);

    /**
     * Emails a client the summary of their ledger for the current financial year across all of their
     * companies together, with a line for each company, and the year's full ledger attached as an
     * Excel workbook with a sheet per company. It goes to the client's email, copying in their manager.
     *
     * @param clientId the client
     * @return who the email went to and what it held
     * @throws com.psc.cl.client.exception.ClientNotFoundException when no client has this identifier
     * @throws com.psc.cl.notification.exception.EmailDeliveryException when email is not configured, or
     *         the SMTP server refuses the email or cannot be reached
     */
    LedgerSummaryEmailResponse sendClientLedgerSummary(UUID clientId);

    /**
     * Emails every client their ledger summary across all of their companies, as
     * {@link #sendClientLedgerSummary} does for one. Each client is emailed on its own, so a failure
     * for one is reported and the others are still emailed.
     *
     * @return which clients were emailed and which could not be
     * @throws com.psc.cl.notification.exception.EmailDeliveryException when email is not configured,
     *         before any client is tried
     */
    BulkLedgerSummaryEmailResponse sendAllClientLedgerSummaries();
}
