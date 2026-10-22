package com.psc.cl.notification.service;

import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.notification.dto.BulkLedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.LedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.PaymentReminderResponse;

import java.util.UUID;

/**
 * Sends clients emails about their accounts.
 */
public interface NotificationService {

    /**
     * Emails a client the summary of one of their companies' ledgers for the current financial year,
     * with the year's ledger statement attached as a PDF. It goes to the client's email,
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
     * companies together, with a line for each company, and the year's ledger statement attached as a
     * PDF with each company's details on a page of its own. It goes to the client's email, copying in
     * their manager.
     *
     * @param clientId the client
     * @return who the email went to and what it held
     * @throws com.psc.cl.client.exception.ClientNotFoundException when no client has this identifier
     * @throws com.psc.cl.notification.exception.EmailDeliveryException when email is not configured, or
     *         the SMTP server refuses the email or cannot be reached
     */
    LedgerSummaryEmailResponse sendClientLedgerSummary(UUID clientId);

    /**
     * Writes the PDF ledger statement {@link #sendClientLedgerSummary} attaches, for the client to be
     * downloaded rather than emailed: across all of their companies, headed by the firm's logo and
     * name, for one financial year, or for every year they have been a client together. For the
     * current year it is the very statement the email attaches. Nothing is sent.
     *
     * @param clientId      the client
     * @param financialYear the financial year of the statement; null for every year together
     * @return the .pdf file, named after the client and the year, such as
     *         {@code Rohan_Mehta_Ledger_FY2026-27.pdf}, or {@code Rohan_Mehta_Ledger_All_Years.pdf} for
     *         every year
     * @throws com.psc.cl.client.exception.ClientNotFoundException when no client has this identifier
     */
    LedgerExport clientLedgerSummaryPdf(UUID clientId, FinancialYear financialYear);

    /**
     * Writes the PDF ledger statement {@link #sendLedgerSummary} attaches, for one company of a client,
     * to be downloaded rather than emailed: headed by the firm's logo and name, for one financial year,
     * or for every year together. For the current year it is the very statement the email attaches.
     * Nothing is sent.
     *
     * @param clientId      the client
     * @param companyId     the company of the client
     * @param financialYear the financial year of the statement; null for every year together
     * @return the .pdf file, named after the company and the year, such as
     *         {@code Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.pdf}, or
     *         {@code Mehta_Textiles_Pvt_Ltd_Ledger_All_Years.pdf} for every year
     * @throws com.psc.cl.client.exception.ClientNotFoundException  when no client has this identifier
     * @throws com.psc.cl.client.exception.CompanyNotFoundException when the client has no such company
     */
    LedgerExport companyLedgerSummaryPdf(UUID clientId, UUID companyId, FinancialYear financialYear);

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

    /**
     * Emails a payment reminder to every client with an amount pending across every financial year and
     * all of their companies. It asks for that whole amount, with the same figures, company lines and
     * attached ledger for the current year as {@link #sendClientLedgerSummary}, plus any balance carried
     * from earlier years. Clients settled or in credit overall are skipped. Each client is emailed on
     * its own, so a failure for one is reported and the others are still reminded.
     *
     * @return which clients were reminded, how many were skipped, and which could not be reminded
     * @throws com.psc.cl.notification.exception.EmailDeliveryException when email is not configured,
     *         before any client is tried
     */
    PaymentReminderResponse sendPaymentReminders();
}
