package com.psc.cl.overview.service;

import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.overview.dto.AgeingResponse;
import com.psc.cl.overview.dto.BilledByCategoryResponse;
import com.psc.cl.overview.dto.MonthlyTrendResponse;
import com.psc.cl.overview.dto.OverviewResponse;
import com.psc.cl.overview.dto.RecentPaymentsResponse;
import com.psc.cl.overview.dto.TopBilledClientsResponse;
import com.psc.cl.overview.dto.TopBilledCompaniesResponse;
import com.psc.cl.overview.dto.TopBilledManagersResponse;
import com.psc.cl.overview.dto.TopPendingClientsResponse;
import com.psc.cl.overview.dto.TopPendingCompaniesResponse;
import com.psc.cl.overview.dto.TopPendingManagersResponse;

/**
 * Business operations giving the firm's position across every client.
 */
public interface OverviewService {

    /** The most clients a ranking returns at once. */
    int MAX_TOP_CLIENTS = 50;

    /** The most managers a ranking returns at once. */
    int MAX_TOP_MANAGERS = 50;

    /** The most companies a ranking returns at once. */
    int MAX_TOP_COMPANIES = 50;

    /** The most payments the latest payments list returns at once. */
    int MAX_RECENT_PAYMENTS = 100;

    /** The most services each category of the billing by category lists at once. */
    int MAX_TOP_SERVICES_PER_CATEGORY = 10;

    /**
     * The firm's position for one financial year: the ledger's billed, received, pending and
     * discount totals across every client in the year, as each client's summary works them out, and
     * how many clients, companies and assigned services there are now. Alongside, the same totals
     * for the previous year up to the same point in it, for comparison.
     *
     * @param financialYear the financial year to total
     * @return the overview; every amount zero when the year has no ledger entries
     */
    OverviewResponse overview(FinancialYear financialYear);

    /**
     * The clients owing most, most first: billed less received in one financial year, or across
     * every year to date, with their manager. Clients settled or in credit are left out.
     *
     * @param financialYear the year to rank by, or null for every year to date
     * @param limit         the most clients to return, from 1 to {@link #MAX_TOP_CLIENTS}
     * @return the ranking; no clients when nobody owes anything
     */
    TopPendingClientsResponse topPendingClients(FinancialYear financialYear, int limit);

    /**
     * The clients billed most, most first: their service charges in one financial year, or across
     * every year to date, with what was received and remains, and their manager. Clients billed
     * nothing are left out.
     *
     * @param financialYear the year to rank by, or null for every year to date
     * @param limit         the most clients to return, from 1 to {@link #MAX_TOP_CLIENTS}
     * @return the ranking; no clients when nothing was billed
     */
    TopBilledClientsResponse topBilledClients(FinancialYear financialYear, int limit);

    /**
     * The managers whose clients owe most, most first: billed less received across all of their
     * clients, in one financial year or across every year to date, with how many clients each has.
     * Managers whose clients are settled or in credit overall are left out.
     *
     * @param financialYear the year to rank by, or null for every year to date
     * @param limit         the most managers to return, from 1 to {@link #MAX_TOP_MANAGERS}
     * @return the ranking; no managers when nobody owes anything
     */
    TopPendingManagersResponse topPendingManagers(FinancialYear financialYear, int limit);

    /**
     * The managers whose clients were billed most, most first: the service charges of all of their
     * clients in one financial year, or across every year to date, with what was received and
     * remains, and how many clients each has. Managers whose clients were billed nothing are left
     * out.
     *
     * @param financialYear the year to rank by, or null for every year to date
     * @param limit         the most managers to return, from 1 to {@link #MAX_TOP_MANAGERS}
     * @return the ranking; no managers when nothing was billed
     */
    TopBilledManagersResponse topBilledManagers(FinancialYear financialYear, int limit);

    /**
     * The companies owing most, most first: billed less received in one financial year, or across
     * every year to date, with the client each belongs to. Companies settled or in credit are left
     * out.
     *
     * @param financialYear the year to rank by, or null for every year to date
     * @param limit         the most companies to return, from 1 to {@link #MAX_TOP_COMPANIES}
     * @return the ranking; no companies when nobody owes anything
     */
    TopPendingCompaniesResponse topPendingCompanies(FinancialYear financialYear, int limit);

    /**
     * The companies billed most, most first: their service charges in one financial year, or across
     * every year to date, with what was received and remains, and the client each belongs to.
     * Companies billed nothing are left out.
     *
     * @param financialYear the year to rank by, or null for every year to date
     * @param limit         the most companies to return, from 1 to {@link #MAX_TOP_COMPANIES}
     * @return the ranking; no companies when nothing was billed
     */
    TopBilledCompaniesResponse topBilledCompanies(FinancialYear financialYear, int limit);

    /**
     * The latest payments received across every client and company, most recently received first,
     * with the company and client that made each.
     *
     * @param limit the most payments to return, from 1 to {@link #MAX_RECENT_PAYMENTS}
     * @return the payments; none when nothing has been received
     */
    RecentPaymentsResponse recentPayments(int limit);

    /**
     * How long what the firm is owed has been due, as of today: what remains unpaid across every
     * company, split into ranges of days past due. Payments are not matched to charges, so each
     * company's payments are applied to its oldest charges first. Also gives what is overdue under
     * the payment terms' markOverdueAfterDays.
     *
     * @return every range, with zero when nothing is owed in it
     */
    AgeingResponse ageing();

    /**
     * What was billed, received and discounted across every client in each month of one financial
     * year.
     *
     * @param financialYear the financial year
     * @return all twelve months, April first; months without entries have every amount zero
     */
    MonthlyTrendResponse monthlyTrend(FinancialYear financialYear);

    /**
     * What each practice area was billed in one financial year: the service charges of its catalog
     * services falling due in the year, and how many of them were billed.
     *
     * @param financialYear the financial year
     * @param topServices   how many of each category's most billed services to list, from 0 to
     *                      {@link #MAX_TOP_SERVICES_PER_CATEGORY}; 0 lists none
     * @return every category, billed most first
     */
    BilledByCategoryResponse billedByCategory(FinancialYear financialYear, int topServices);
}
