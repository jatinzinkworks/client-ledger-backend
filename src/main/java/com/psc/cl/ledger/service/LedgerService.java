package com.psc.cl.ledger.service;

import com.psc.cl.ledger.dto.ClientBalance;
import com.psc.cl.ledger.dto.ClientCompanyBalance;
import com.psc.cl.ledger.dto.ClientLedgerStatusResponse;
import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyBalance;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LedgerEntryResponse;
import com.psc.cl.ledger.dto.LedgerEntryUpdateRequest;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.ledger.dto.LedgerListQuery;
import com.psc.cl.ledger.dto.LedgerListResponse;
import com.psc.cl.ledger.dto.LedgerSummary;
import com.psc.cl.ledger.dto.ManagerBalance;
import com.psc.cl.ledger.dto.MonthlyTotals;
import com.psc.cl.ledger.dto.OpeningBalance;
import com.psc.cl.ledger.dto.PaymentListResponse;
import com.psc.cl.ledger.dto.PaymentRequest;
import com.psc.cl.ledger.dto.UnpaidCharge;
import com.psc.cl.ledger.model.FinancialYear;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
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
     * Records the balance a company was taken on with, brought over from before the firm kept its
     * ledger here, as an entry in its ledger. It is meant for a company being added, in the same
     * transaction, so the company is not looked up again; foreign keys hold it to its client. A
     * company has at most one opening balance.
     *
     * @param clientId    the client
     * @param companyId   the company of the client
     * @param amount      the balance, at most two decimals: positive when the company owes it, negative
     *                    when it starts in credit; never zero
     * @param balanceDate the date the balance stood at, which places it in a financial year; null for
     *                    today
     * @return the ledger entry recorded for the opening balance
     * @throws com.psc.cl.ledger.exception.FutureOpeningBalanceDateException when the date is after today
     * @throws IllegalArgumentException when the amount is zero
     */
    LedgerEntryResponse recordOpeningBalance(UUID clientId, UUID companyId, BigDecimal amount,
                                             LocalDate balanceDate);

    /**
     * Sets the opening balance of a company already held, as an update of the company gives it:
     * records one when the company has none, replaces the amount and date of the one it has, or
     * removes it when the amount is zero. It is meant for a company being updated, in the same
     * transaction, so the company is not looked up again.
     *
     * @param clientId    the client
     * @param companyId   the company of the client
     * @param amount      the balance, at most two decimals: positive when the company owes it, negative
     *                    when it starts in credit; zero to remove it
     * @param balanceDate the date the balance stood at, which places it in a financial year; null to
     *                    keep the date of the balance the company has, or today for a new one
     * @throws com.psc.cl.ledger.exception.FutureOpeningBalanceDateException when the date is after today
     */
    void setOpeningBalance(UUID clientId, UUID companyId, BigDecimal amount, LocalDate balanceDate);

    /**
     * The opening balances of several companies, in one query.
     *
     * @param companyIds the companies
     * @return each company's opening balance, by company; a company without one is left out
     */
    Map<UUID, OpeningBalance> openingBalances(Collection<UUID> companyIds);

    /**
     * Whether any company of a client has a service charge or a payment in its ledger: the history
     * that stops a client from being deleted. An opening balance alone does not.
     *
     * @param clientId the client
     * @return true when the client's ledger holds anything besides opening balances
     */
    boolean clientHasEntriesBesidesOpeningBalances(UUID clientId);

    /**
     * Whether a company has a service charge or a payment in its ledger: the history that stops a
     * company from being deleted. An opening balance alone does not.
     *
     * @param companyId the company
     * @return true when the company's ledger holds anything besides its opening balance
     */
    boolean companyHasEntriesBesidesOpeningBalance(UUID companyId);

    /**
     * Removes the opening balances of every company of a client, as the client is deleted, in the
     * same transaction and before it.
     *
     * @param clientId the client being deleted
     * @return how many opening balances were removed
     */
    int removeClientOpeningBalances(UUID clientId);

    /**
     * Removes a company's opening balance, as the company is deleted, in the same transaction and
     * before it.
     *
     * @param companyId the company being deleted
     * @return how many opening balances were removed: 1, or 0 when it had none
     */
    int removeCompanyOpeningBalance(UUID companyId);

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
     * A client's balance for every financial year since they became a client, latest first, or for
     * one year: billed before discount, discount, received, the year's closing balance and its
     * status, across all of their companies and for each company. The first year is that of the
     * client's earliest ledger entry, or of the day they were added if earlier; the last is the
     * current year. Years without entries are listed with zero figures. clientSince and yearCount
     * always cover that whole span, whichever years are listed.
     *
     * @param clientId      the client
     * @param financialYear the only year to list, or null for every year
     * @param pendingOnly   whether each year lists only the companies still owing for it
     * @return the client's years
     */
    ClientLedgerStatusResponse clientLedgerStatus(UUID clientId, FinancialYear financialYear, boolean pendingOnly);

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
     * Writes a client's ledger statement for one financial year as a PDF, to send to the client: the
     * figures of {@link #summarizeClient} with a line per company, then each company's entries of the
     * year, earliest first, with a running balance and totals, on a page per company. Each charge is
     * shown only as its total fee, with discount and GST folded in; no discount is shown. Every page is
     * headed by the firm's logo and name.
     *
     * @param clientId      the client
     * @param financialYear the financial year of the statement
     * @param firmName      the firm's name for the top of every page; null when none is saved
     * @return the .pdf file, named after the client and the year, such as
     *         {@code Rohan_Mehta_Ledger_FY2026-27.pdf}
     * @throws com.psc.cl.client.exception.ClientNotFoundException
     *         when no client carries this identifier
     */
    LedgerExport statementForClient(UUID clientId, FinancialYear financialYear, String firmName);

    /**
     * Writes the ledger statement of one company of a client for one financial year as a PDF, to send
     * to the client: the figures of {@link #summarizeCompany}, then the company's entries of the year,
     * laid out as in {@link #statementForClient}.
     *
     * @param clientId      the client
     * @param companyId     the company of the client
     * @param financialYear the financial year of the statement
     * @param firmName      the firm's name for the top of every page; null when none is saved
     * @return the .pdf file, named after the company and the year, such as
     *         {@code Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.pdf}
     */
    LedgerExport statementForCompany(UUID clientId, UUID companyId, FinancialYear financialYear, String firmName);

    /**
     * What each of the given clients still owes: billed less received across all of their companies
     * and every financial year. For other modules that show it alongside their own figures, such as
     * the client list. The clients are not checked for existence.
     *
     * @param clientIds the clients to total
     * @return the pending amount by client identifier; clients with no ledger entries are left out,
     *         so callers should treat them as zero
     */
    Map<UUID, BigDecimal> pendingByClient(Collection<UUID> clientIds);

    /**
     * What the clients of each manager still owe: billed less received across all of their companies
     * and every financial year. For the Manager Catalog, which shows it alongside each manager.
     *
     * @return the pending amount by manager identifier; managers whose clients have no ledger
     *         entries are left out, so callers should treat them as zero
     */
    Map<UUID, BigDecimal> pendingByManager();

    /**
     * What the clients of one manager still owe, as {@link #pendingByManager()} gives for all.
     *
     * @param managerId the manager; not checked for existence
     * @return billed less received, zero when the manager's clients have no ledger entries
     */
    BigDecimal pendingForManager(UUID managerId);

    /**
     * What each catalog service was billed in one financial year across every client: the sum of
     * its service charges falling due in the year, GST included. For the Service Catalog, which
     * shows it alongside each service.
     *
     * @param financialYear the financial year
     * @return the amount billed by catalog service identifier; services with nothing billed in the
     *         year are left out, so callers should treat them as zero
     */
    Map<UUID, BigDecimal> billedByCatalogService(FinancialYear financialYear);

    /**
     * What each catalog service has been billed to date across every client and every financial
     * year: the sum of its service charges falling due on or before today, GST included.
     *
     * @return the amount billed by catalog service identifier; services never billed are left out,
     *         so callers should treat them as zero
     */
    Map<UUID, BigDecimal> billedByCatalogServiceToDate();

    /**
     * What each service of one company was billed in one financial year: the sum of its service
     * charges falling due in the year, GST included. For other modules that show this alongside their
     * own figures, such as a company's list of services. The company is not checked for existence;
     * the caller has already done so.
     *
     * @param companyId     the company
     * @param financialYear the financial year
     * @return the amount billed by company service (assignment) identifier; services with nothing
     *         billed in the year are left out, so callers should treat them as zero
     */
    Map<UUID, BigDecimal> billedByService(UUID companyId, FinancialYear financialYear);

    /**
     * The clients owing most, most first: billed less received, in one financial year or across
     * every year. Only clients still owing are included; those settled or in credit are left out.
     *
     * @param financialYear the year to rank by, or null for every year to date
     * @param limit         the most clients to return, at least one
     * @return the clients, at most limit of them; empty when nobody owes anything
     */
    List<ClientBalance> topClientsByPending(FinancialYear financialYear, int limit);

    /**
     * The managers whose clients owe most, most first: billed less received across all of their
     * clients, in one financial year or across every year. Clients count towards the manager they
     * are assigned to now. Only managers whose clients still owe overall are included.
     *
     * @param financialYear the year to rank by, or null for every year to date
     * @param limit         the most managers to return, at least one
     * @return the managers, at most limit of them; empty when nobody owes anything
     */
    List<ManagerBalance> topManagersByPending(FinancialYear financialYear, int limit);

    /**
     * The managers whose clients were billed most, most first: the service charges of all of their
     * clients, in one financial year or across every year, with what was received and remains.
     * Clients count towards the manager they are assigned to now. Only managers whose clients were
     * billed anything are included.
     *
     * @param financialYear the year to rank by, or null for every year to date
     * @param limit         the most managers to return, at least one
     * @return the managers, at most limit of them; empty when nothing was billed
     */
    List<ManagerBalance> topManagersByBilled(FinancialYear financialYear, int limit);

    /**
     * The companies owing most, most first: billed less received, in one financial year or across
     * every year. Only companies still owing are included; those settled or in credit are left out.
     *
     * @param financialYear the year to rank by, or null for every year to date
     * @param limit         the most companies to return, at least one
     * @return the companies, at most limit of them; empty when nobody owes anything
     */
    List<ClientCompanyBalance> topCompaniesByPending(FinancialYear financialYear, int limit);

    /**
     * The companies billed most, most first: their service charges in one financial year or across
     * every year, with what was received and remains. Only companies billed anything are included.
     *
     * @param financialYear the year to rank by, or null for every year to date
     * @param limit         the most companies to return, at least one
     * @return the companies, at most limit of them; empty when nothing was billed
     */
    List<ClientCompanyBalance> topCompaniesByBilled(FinancialYear financialYear, int limit);

    /**
     * The latest payments received across every client and company: the most recently received
     * first, and those received the same day latest recorded first.
     *
     * @param limit the most payments to return, at least one
     * @return the payments, at most limit of them; their amounts as stored, negative
     */
    List<LedgerEntryResponse> recentPayments(int limit);

    /**
     * The clients billed most, most first: their service charges in one financial year or across
     * every year, with what was received and remains. Only clients billed anything are included.
     *
     * @param financialYear the year to rank by, or null for every year to date
     * @param limit         the most clients to return, at least one
     * @return the clients, at most limit of them; empty when nothing was billed
     */
    List<ClientBalance> topClientsByBilled(FinancialYear financialYear, int limit);

    /**
     * Totals every ledger entry of every client in one financial year, the same way a client's
     * summary totals theirs: totalAmount is what was billed, totalPaid what was received, and balance
     * the difference. For the firm-wide overview.
     *
     * @param financialYear the financial year
     * @return the totals; every amount zero when the year has no entries
     */
    LedgerSummary totalsForYear(FinancialYear financialYear);

    /**
     * Totals every ledger entry of every client dated within a range, both ends inclusive, as
     * {@link #totalsForYear} does for a whole year. For comparing part of one year with another.
     *
     * @param fromDate earliest date to include
     * @param toDate   latest date to include
     * @return the totals; every amount zero when the range has no entries, or ends before it starts
     */
    LedgerSummary totalsBetween(LocalDate fromDate, LocalDate toDate);

    /**
     * What was billed, received and discounted across every client in each calendar month of one
     * financial year, April first. Service charges count in the month they fell due; payments in
     * the month they were received. For the overview's monthly trend.
     *
     * @param financialYear the financial year
     * @return all twelve months, earliest first; months without entries have every amount zero
     */
    List<MonthlyTotals> monthlyTotals(FinancialYear financialYear);

    /**
     * What every company still owes, by the date the charges fell due. Payments are not matched to
     * charges, so each company's payments are applied to its oldest charges first; what is left is
     * unpaid. Companies settled or in credit have nothing unpaid. For the overview's receivables
     * ageing.
     *
     * @param asOf the day to work it out on: charges falling due and payments received after it are
     *             left out
     * @return the unpaid charges, earliest due first; empty when nobody owes anything
     */
    List<UnpaidCharge> unpaidCharges(LocalDate asOf);

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
