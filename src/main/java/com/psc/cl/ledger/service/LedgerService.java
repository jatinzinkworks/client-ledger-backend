package com.psc.cl.ledger.service;

import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyBalance;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.ledger.dto.LedgerEntryResponse;
import com.psc.cl.ledger.dto.LedgerEntryUpdateRequest;
import com.psc.cl.ledger.dto.LedgerListQuery;
import com.psc.cl.ledger.dto.LedgerListResponse;
import com.psc.cl.ledger.dto.PaymentListResponse;
import com.psc.cl.ledger.dto.PaymentRequest;
import com.psc.cl.ledger.model.FinancialYear;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business operations on the ledger of amounts companies owe for their services.
 */
public interface LedgerService {

    /**
     * Posts a ledger entry for every service of a company that has fallen due, on or before
     * today.
     *
     * <p>A recurring service is billed for each date it fell due that has not been billed yet,
     * then moves on to its next due date after today. A ONE_OFF service is billed once and marked
     * as posted, so it is never billed again.
     *
     * @param clientId  the client
     * @param companyId the company of the client to bill
     * @return the entries created, earliest due first; empty when nothing was due
     */
    List<LedgerEntryResponse> postDueServices(UUID clientId, UUID companyId);

    /**
     * Fetches one ledger entry billed to a company of a client.
     *
     * @param clientId  the client
     * @param companyId the company of the client
     * @param entryId   the ledger entry
     * @return the entry
     */
    LedgerEntryResponse getEntry(UUID clientId, UUID companyId, UUID entryId);

    /**
     * Corrects the rates of a ledger entry, working its amounts out again from them. What the entry
     * bills, and when, stays as it was.
     *
     * @param clientId  the client
     * @param companyId the company of the client
     * @param entryId   the ledger entry
     * @param request   the corrected service fee, discount and GST rate
     * @return the entry as corrected
     */
    LedgerEntryResponse updateEntry(UUID clientId, UUID companyId, UUID entryId, LedgerEntryUpdateRequest request);

    /**
     * Records a payment received from a company as an entry in its ledger, its amount stored as
     * negative so the ledger's totals add up to what is still owed.
     *
     * @param clientId  the client
     * @param companyId the company of the client that paid
     * @param request   the amount, date, method, reference and comment of the payment
     * @return the ledger entry recorded for the payment
     * @throws com.psc.cl.ledger.exception.FuturePaymentDateException when the payment is dated after today
     */
    LedgerEntryResponse recordPayment(UUID clientId, UUID companyId, PaymentRequest request);

    /**
     * Lists one page of the payments recorded for one company of a client.
     *
     * @param clientId  the client
     * @param companyId the company of the client
     * @param query     the range of dates received to include, and the page and order to return
     * @return the page of payments, with the count and total of every matching payment across all pages
     */
    PaymentListResponse listPayments(UUID clientId, UUID companyId, LedgerListQuery query);

    /**
     * Replaces every detail of a recorded payment: its amount, date received, method, reference and
     * comment. Its financial year follows the new date; when it was recorded stays as it was.
     *
     * @param clientId  the client
     * @param companyId the company of the client that paid
     * @param paymentId the ledger entry of the payment
     * @param request   the payment's corrected details
     * @return the payment's ledger entry as updated
     * @throws com.psc.cl.ledger.exception.NotAPaymentException when the entry is a service charge
     * @throws com.psc.cl.ledger.exception.FuturePaymentDateException when the payment is dated after today
     */
    LedgerEntryResponse updatePayment(UUID clientId, UUID companyId, UUID paymentId, PaymentRequest request);

    /**
     * Deletes a ledger entry: a payment recorded in error, or a service charge. For a service charge,
     * the service it billed is left as it is: a ONE_OFF service stays marked as posted, and a recurring
     * one keeps its next due date, so the date is not billed again.
     *
     * @param clientId  the client
     * @param companyId the company of the client
     * @param entryId   the ledger entry
     */
    void deleteEntry(UUID clientId, UUID companyId, UUID entryId);

    /**
     * Summarises a company's account for one financial year: what it was billed, what it paid, what
     * remains, the discount it was given, and its latest payment. Service charges count in the year
     * they fell due; payments in the year they were received.
     *
     * @param clientId      the client
     * @param companyId     the company of the client
     * @param financialYear the financial year to summarise
     * @return the year's summary; every amount zero, and no last payment, when the year has no entries
     */
    CompanyLedgerSummaryResponse summarizeCompany(UUID clientId, UUID companyId, FinancialYear financialYear);

    /**
     * Summarises a client's account for one financial year across all of their companies together:
     * what was billed, what was paid, what remains, the discount given, and the latest payment from
     * any company.
     *
     * @param clientId      the client
     * @param financialYear the financial year to summarise
     * @return the year's summary; every amount zero, and no last payment, when the year has no entries
     */
    ClientLedgerSummaryResponse summarizeClient(UUID clientId, FinancialYear financialYear);

    /**
     * Exports a client's ledger for one financial year as an Excel workbook: a Summary sheet with
     * the figures of {@link #summarizeClient}, and a Ledger sheet with every entry of the year across
     * all of the client's companies, earliest first, with a running balance and totals, followed by
     * one sheet per company, by name, with only that company's entries, balance and totals.
     *
     * @param clientId      the client
     * @param financialYear the financial year to export
     * @return the .xlsx file, named after the client and the year, such as
     *         {@code Rohan_Mehta_Ledger_FY2026-27.xlsx}; a year with no entries still exports, with
     *         zero totals and an empty ledger
     */
    LedgerExport exportClient(UUID clientId, FinancialYear financialYear);

    /**
     * Exports the ledger of one company of a client for one financial year as an Excel workbook: a
     * Summary sheet with the figures of {@link #summarizeCompany}, and a Ledger sheet with every
     * entry of the company in the year, earliest first, with a running balance and totals.
     *
     * @param clientId      the client
     * @param companyId     the company of the client
     * @param financialYear the financial year to export
     * @return the .xlsx file, named after the company and the year, such as
     *         {@code Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.xlsx}; a year with no entries still
     *         exports, with zero totals and an empty ledger
     */
    LedgerExport exportCompany(UUID clientId, UUID companyId, FinancialYear financialYear);

    /**
     * The balance of each company of a client for one financial year: billed, received and pending.
     * For other modules that show these figures alongside their own, such as a client's list of
     * companies. The client is not checked for existence; the caller has already loaded it.
     *
     * @param clientId      the client
     * @param financialYear the financial year
     * @return each company's balance, by company; companies with nothing billed or received in the
     *         year are left out, so callers should treat them as {@link CompanyBalance#ZERO}
     */
    Map<UUID, CompanyBalance> companyBalances(UUID clientId, FinancialYear financialYear);

    /**
     * Lists one page of the ledger entries posted for one company of a client.
     *
     * @param clientId  the client
     * @param companyId the company of the client
     * @param query     the range of due dates to include, and the page and order to return
     * @return the page of the company's entries, with the totals of every matching entry across all
     *         pages
     */
    LedgerListResponse listCompanyEntries(UUID clientId, UUID companyId, LedgerListQuery query);

    /**
     * Lists one page of the ledger entries posted for any company of a client.
     *
     * @param clientId the client
     * @param query    the range of due dates to include, and the page and order to return
     * @return the page of the client's entries, with the totals of every matching entry across all
     *         pages, by financial year and by company
     */
    LedgerListResponse listClientEntries(UUID clientId, LedgerListQuery query);
}
