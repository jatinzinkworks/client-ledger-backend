package com.psc.cl.ledger.repository;

import com.psc.cl.ledger.model.LedgerEntry;
import com.psc.cl.ledger.model.LedgerEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for ledger entries.
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Totals of entries, one row per company, financial year and entry type; completed by a where
     * clause and {@link #BY_ROW}. The service figures of a payments-only row are null.
     */
    String TOTALS = "select e.companyId as companyId, e.financialYear as financialYear, "
            + "e.entryType as entryType, count(e) as entryCount, "
            + "sum(e.serviceFee) as totalServiceFee, sum(e.discountAmount) as totalDiscountAmount, "
            + "sum(e.taxableAmount) as totalTaxableAmount, sum(e.gstAmount) as totalGstAmount, "
            + "sum(e.totalAmount) as totalAmount from LedgerEntry e ";

    /** Closes {@link #TOTALS}. */
    String BY_ROW = " group by e.companyId, e.financialYear, e.entryType";

    /**
     * Lists the due dates already billed for the given company services, in one query. Served by
     * the unique (company_service_id, due_date) index.
     *
     * @param companyServiceIds the company services to look up
     * @return one entry per due date billed; services never billed are left out
     */
    @Query("select e.companyServiceId as companyServiceId, e.dueDate as dueDate from LedgerEntry e "
            + "where e.companyServiceId in :companyServiceIds")
    List<PostedPeriod> findPostedPeriods(@Param("companyServiceIds") Collection<UUID> companyServiceIds);

    /**
     * Finds an entry only when it was billed to the given company, so it cannot be reached under
     * another company's path.
     *
     * @param id        the entry identifier
     * @param companyId the company the entry must have been billed to
     * @return the entry, or empty when the company has no entry with this identifier
     */
    Optional<LedgerEntry> findByIdAndCompanyId(UUID id, UUID companyId);

    /**
     * Finds the latest entry of one type for one company within a range of dates, both ends
     * inclusive; the one recorded last settles a tie on date. Served by the (company_id, due_date)
     * index.
     *
     * @param companyId the company
     * @param entryType the type of entry, such as a payment
     * @param fromDate  earliest date to include
     * @param toDate    latest date to include
     * @return the latest entry, or empty when there is none in the range
     */
    Optional<LedgerEntry> findFirstByCompanyIdAndEntryTypeAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(
            UUID companyId, LedgerEntryType entryType, LocalDate fromDate, LocalDate toDate);

    /**
     * Finds the latest entry of one type across every company of one client within a range of dates,
     * both ends inclusive; the one recorded last settles a tie on date. Served by the
     * (client_id, due_date) index.
     *
     * @param clientId  the client
     * @param entryType the type of entry, such as a payment
     * @param fromDate  earliest date to include
     * @param toDate    latest date to include
     * @return the latest entry, or empty when there is none in the range
     */
    Optional<LedgerEntry> findFirstByClientIdAndEntryTypeAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(
            UUID clientId, LedgerEntryType entryType, LocalDate fromDate, LocalDate toDate);

    /**
     * Reads one page of the entries of one type for one company within a range of dates, both ends
     * inclusive. Served by the (company_id, due_date) index.
     *
     * @param companyId the company
     * @param entryType the type of entry, such as a payment
     * @param fromDate  earliest date to include
     * @param toDate    latest date to include
     * @param pageable  the page to read and the order to read entries in
     * @return the page, with the count of every entry of the type in the range
     */
    Page<LedgerEntry> findByCompanyIdAndEntryTypeAndDueDateBetween(UUID companyId, LedgerEntryType entryType,
                                                                   LocalDate fromDate, LocalDate toDate,
                                                                   Pageable pageable);

    /**
     * Reads one page of the entries of one company due within a range, both ends inclusive.
     * Served by the (company_id, due_date) index.
     *
     * @param companyId the company
     * @param fromDate  earliest due date to include
     * @param toDate    latest due date to include
     * @param pageable  the page to read and the order to read entries in
     * @return the page, with the count of every entry in the range
     */
    Page<LedgerEntry> findByCompanyIdAndDueDateBetween(UUID companyId, LocalDate fromDate, LocalDate toDate,
                                                       Pageable pageable);

    /**
     * Reads one page of the entries of every company of one client due within a range, both ends
     * inclusive. Served by the (client_id, due_date) index.
     *
     * @param clientId the client
     * @param fromDate earliest due date to include
     * @param toDate   latest due date to include
     * @param pageable the page to read and the order to read entries in
     * @return the page, with the count of every entry in the range
     */
    Page<LedgerEntry> findByClientIdAndDueDateBetween(UUID clientId, LocalDate fromDate, LocalDate toDate,
                                                      Pageable pageable);

    /**
     * Reads every entry of one company due within a range, both ends inclusive, in one go, for
     * exports. Served by the (company_id, due_date) index.
     *
     * @param companyId the company
     * @param fromDate  earliest due date to include
     * @param toDate    latest due date to include
     * @param sort      the order to read entries in
     * @return the entries; empty when none matches
     */
    List<LedgerEntry> findAllByCompanyIdAndDueDateBetween(UUID companyId, LocalDate fromDate, LocalDate toDate,
                                                          Sort sort);

    /**
     * Reads every entry of every company of one client due within a range, both ends inclusive, in
     * one go, for exports. Served by the (client_id, due_date) index.
     *
     * @param clientId the client
     * @param fromDate earliest due date to include
     * @param toDate   latest due date to include
     * @param sort     the order to read entries in
     * @return the entries; empty when none matches
     */
    List<LedgerEntry> findAllByClientIdAndDueDateBetween(UUID clientId, LocalDate fromDate, LocalDate toDate,
                                                         Sort sort);

    /**
     * Sums the service charges of each service of one company falling due within a range, both ends
     * inclusive, in one query. Served by the (company_id, due_date) index.
     *
     * @param companyId the company
     * @param entryType the type of entry to sum; service charges
     * @param fromDate  earliest due date to include
     * @param toDate    latest due date to include
     * @return one row per company service billed in the range; services not billed are left out
     */
    @Query("select e.companyServiceId as companyServiceId, sum(e.totalAmount) as totalBilled from LedgerEntry e "
            + "where e.companyId = :companyId and e.entryType = :entryType and e.companyServiceId is not null "
            + "and e.dueDate between :fromDate and :toDate group by e.companyServiceId")
    List<ServiceBilledRow> totalBilledByService(@Param("companyId") UUID companyId,
                                                @Param("entryType") LedgerEntryType entryType,
                                                @Param("fromDate") LocalDate fromDate,
                                                @Param("toDate") LocalDate toDate);

    /**
     * Sums the service charges of each catalog service falling due within a range, both ends
     * inclusive, across every client, in one query. Served by the due_date index.
     *
     * @param entryType the type of entry to sum; service charges
     * @param fromDate  earliest due date to include
     * @param toDate    latest due date to include
     * @return one row per catalog service billed in the range; services not billed are left out
     */
    @Query("select e.catalogServiceId as catalogServiceId, sum(e.totalAmount) as totalBilled from LedgerEntry e "
            + "where e.entryType = :entryType and e.catalogServiceId is not null "
            + "and e.dueDate between :fromDate and :toDate group by e.catalogServiceId")
    List<CatalogServiceBilledRow> totalBilledByCatalogService(@Param("entryType") LedgerEntryType entryType,
                                                              @Param("fromDate") LocalDate fromDate,
                                                              @Param("toDate") LocalDate toDate);

    /**
     * Totals what each of the given clients still owes, across every ledger entry of every company
     * and financial year, in one query: charges are stored positive and payments negative, so the
     * sum is billed less received. Served by the (client_id, due_date) index.
     *
     * @param clientIds the clients to total; must not be empty
     * @return one row per client with ledger entries; other clients are left out
     */
    @Query("select e.clientId as clientId, sum(e.totalAmount) as totalPending from LedgerEntry e "
            + "where e.clientId in :clientIds group by e.clientId")
    List<ClientPendingRow> totalPendingByClient(@Param("clientIds") Collection<UUID> clientIds);

    /**
     * Totals what the clients of each manager still owe, across every ledger entry of every
     * financial year, in one query: charges are stored positive and payments negative, so the sum
     * is billed less received. Clients are matched to their manager as they are assigned now.
     *
     * @return one row per manager whose clients have ledger entries; other managers are left out
     */
    @Query("select c.managerId as managerId, sum(e.totalAmount) as totalPending "
            + "from LedgerEntry e join Client c on c.id = e.clientId group by c.managerId")
    List<ManagerPendingRow> totalPendingByManager();

    /**
     * Totals what the clients of one manager still owe, across every ledger entry of every
     * financial year, as {@link #totalPendingByManager()} does for all managers.
     *
     * @param managerId the manager
     * @return billed less received; null when the manager's clients have no ledger entries
     */
    @Query("select sum(e.totalAmount) from LedgerEntry e join Client c on c.id = e.clientId "
            + "where c.managerId = :managerId")
    BigDecimal totalPendingForManager(@Param("managerId") UUID managerId);

    /**
     * Totals every entry of one company due within a range, whichever page is being read.
     *
     * @param companyId the company
     * @param fromDate  earliest due date to include
     * @param toDate    latest due date to include
     * @return one row per financial year and entry type with entries; empty when none matches
     */
    @Query(TOTALS + "where e.companyId = :companyId and e.dueDate between :fromDate and :toDate" + BY_ROW)
    List<LedgerTotalsRow> totalCompanyEntries(@Param("companyId") UUID companyId,
                                              @Param("fromDate") LocalDate fromDate,
                                              @Param("toDate") LocalDate toDate);

    /**
     * Ranks the clients still owing over a range of dates, both ends inclusive, by what they owe,
     * most first: one row per client whose charges in the range exceed their payments in it. Clients
     * owing the same come in identifier order, so the ranking is stable.
     *
     * @param charge   the entry type of service charges, whose amounts count as billed
     * @param payment  the entry type of payments, whose amounts count as paid
     * @param fromDate earliest due date to include
     * @param toDate   latest due date to include
     * @param pageable how many clients to return, as the size of the first page
     * @return the clients owing most, at most the page size of them
     */
    @Query("select e.clientId as clientId, "
            + "sum(case when e.entryType = :charge then e.totalAmount else 0 end) as totalBilled, "
            + "sum(case when e.entryType = :payment then e.totalAmount else 0 end) as totalPaid, "
            + "sum(e.totalAmount) as pending "
            + "from LedgerEntry e where e.dueDate between :fromDate and :toDate "
            + "group by e.clientId having sum(e.totalAmount) > 0 "
            + "order by sum(e.totalAmount) desc, e.clientId")
    List<ClientBalanceRow> topClientsByPending(@Param("charge") LedgerEntryType charge,
                                               @Param("payment") LedgerEntryType payment,
                                               @Param("fromDate") LocalDate fromDate,
                                               @Param("toDate") LocalDate toDate,
                                               Pageable pageable);

    /**
     * Ranks clients by what they were billed over a range of dates, both ends inclusive, most
     * first: one row per client charged anything in the range. Clients billed the same come in
     * identifier order, so the ranking is stable.
     *
     * @param charge   the entry type of service charges, whose amounts count as billed
     * @param payment  the entry type of payments, whose amounts count as paid
     * @param fromDate earliest due date to include
     * @param toDate   latest due date to include
     * @param pageable how many clients to return, as the size of the first page
     * @return the clients billed most, at most the page size of them
     */
    @Query("select e.clientId as clientId, "
            + "sum(case when e.entryType = :charge then e.totalAmount else 0 end) as totalBilled, "
            + "sum(case when e.entryType = :payment then e.totalAmount else 0 end) as totalPaid, "
            + "sum(e.totalAmount) as pending "
            + "from LedgerEntry e where e.dueDate between :fromDate and :toDate "
            + "group by e.clientId having sum(case when e.entryType = :charge then e.totalAmount else 0 end) > 0 "
            + "order by sum(case when e.entryType = :charge then e.totalAmount else 0 end) desc, e.clientId")
    List<ClientBalanceRow> topClientsByBilled(@Param("charge") LedgerEntryType charge,
                                              @Param("payment") LedgerEntryType payment,
                                              @Param("fromDate") LocalDate fromDate,
                                              @Param("toDate") LocalDate toDate,
                                              Pageable pageable);

    /**
     * Ranks managers by what their clients still owe over a range of dates, both ends inclusive,
     * most first: one row per manager whose clients' charges in the range exceed their payments in
     * it. Clients are matched to their manager as they are assigned now. Managers owing the same come
     * in identifier order, so the ranking is stable.
     *
     * @param charge   the entry type of service charges, whose amounts count as billed
     * @param payment  the entry type of payments, whose amounts count as paid
     * @param fromDate earliest due date to include
     * @param toDate   latest due date to include
     * @param pageable how many managers to return, as the size of the first page
     * @return the managers whose clients owe most, at most the page size of them
     */
    @Query("select c.managerId as managerId, "
            + "sum(case when e.entryType = :charge then e.totalAmount else 0 end) as totalBilled, "
            + "sum(case when e.entryType = :payment then e.totalAmount else 0 end) as totalPaid, "
            + "sum(e.totalAmount) as pending "
            + "from LedgerEntry e join Client c on c.id = e.clientId "
            + "where e.dueDate between :fromDate and :toDate "
            + "group by c.managerId having sum(e.totalAmount) > 0 "
            + "order by sum(e.totalAmount) desc, c.managerId")
    List<ManagerBalanceRow> topManagersByPending(@Param("charge") LedgerEntryType charge,
                                                 @Param("payment") LedgerEntryType payment,
                                                 @Param("fromDate") LocalDate fromDate,
                                                 @Param("toDate") LocalDate toDate,
                                                 Pageable pageable);

    /**
     * Ranks managers by what was billed to their clients over a range of dates, both ends
     * inclusive, most first: one row per manager whose clients were charged anything in the range.
     * Clients are matched to their manager as they are assigned now. Managers billed the same come
     * in identifier order, so the ranking is stable.
     *
     * @param charge   the entry type of service charges, whose amounts count as billed
     * @param payment  the entry type of payments, whose amounts count as paid
     * @param fromDate earliest due date to include
     * @param toDate   latest due date to include
     * @param pageable how many managers to return, as the size of the first page
     * @return the managers whose clients were billed most, at most the page size of them
     */
    @Query("select c.managerId as managerId, "
            + "sum(case when e.entryType = :charge then e.totalAmount else 0 end) as totalBilled, "
            + "sum(case when e.entryType = :payment then e.totalAmount else 0 end) as totalPaid, "
            + "sum(e.totalAmount) as pending "
            + "from LedgerEntry e join Client c on c.id = e.clientId "
            + "where e.dueDate between :fromDate and :toDate "
            + "group by c.managerId having sum(case when e.entryType = :charge then e.totalAmount else 0 end) > 0 "
            + "order by sum(case when e.entryType = :charge then e.totalAmount else 0 end) desc, c.managerId")
    List<ManagerBalanceRow> topManagersByBilled(@Param("charge") LedgerEntryType charge,
                                                @Param("payment") LedgerEntryType payment,
                                                @Param("fromDate") LocalDate fromDate,
                                                @Param("toDate") LocalDate toDate,
                                                Pageable pageable);

    /**
     * Ranks companies by what they still owe over a range of dates, both ends inclusive, most first:
     * one row per company whose charges in the range exceed its payments in it. Companies owing the
     * same come in identifier order, so the ranking is stable.
     *
     * @param charge   the entry type of service charges, whose amounts count as billed
     * @param payment  the entry type of payments, whose amounts count as paid
     * @param fromDate earliest due date to include
     * @param toDate   latest due date to include
     * @param pageable how many companies to return, as the size of the first page
     * @return the companies owing most, at most the page size of them
     */
    @Query("select e.companyId as companyId, e.clientId as clientId, "
            + "sum(case when e.entryType = :charge then e.totalAmount else 0 end) as totalBilled, "
            + "sum(case when e.entryType = :payment then e.totalAmount else 0 end) as totalPaid, "
            + "sum(e.totalAmount) as pending "
            + "from LedgerEntry e where e.dueDate between :fromDate and :toDate "
            + "group by e.companyId, e.clientId having sum(e.totalAmount) > 0 "
            + "order by sum(e.totalAmount) desc, e.companyId")
    List<CompanyBalanceRow> topCompaniesByPending(@Param("charge") LedgerEntryType charge,
                                                  @Param("payment") LedgerEntryType payment,
                                                  @Param("fromDate") LocalDate fromDate,
                                                  @Param("toDate") LocalDate toDate,
                                                  Pageable pageable);

    /**
     * Ranks companies by what they were billed over a range of dates, both ends inclusive, most
     * first: one row per company charged anything in the range. Companies billed the same come in
     * identifier order, so the ranking is stable.
     *
     * @param charge   the entry type of service charges, whose amounts count as billed
     * @param payment  the entry type of payments, whose amounts count as paid
     * @param fromDate earliest due date to include
     * @param toDate   latest due date to include
     * @param pageable how many companies to return, as the size of the first page
     * @return the companies billed most, at most the page size of them
     */
    @Query("select e.companyId as companyId, e.clientId as clientId, "
            + "sum(case when e.entryType = :charge then e.totalAmount else 0 end) as totalBilled, "
            + "sum(case when e.entryType = :payment then e.totalAmount else 0 end) as totalPaid, "
            + "sum(e.totalAmount) as pending "
            + "from LedgerEntry e where e.dueDate between :fromDate and :toDate "
            + "group by e.companyId, e.clientId "
            + "having sum(case when e.entryType = :charge then e.totalAmount else 0 end) > 0 "
            + "order by sum(case when e.entryType = :charge then e.totalAmount else 0 end) desc, e.companyId")
    List<CompanyBalanceRow> topCompaniesByBilled(@Param("charge") LedgerEntryType charge,
                                                 @Param("payment") LedgerEntryType payment,
                                                 @Param("fromDate") LocalDate fromDate,
                                                 @Param("toDate") LocalDate toDate,
                                                 Pageable pageable);

    /**
     * Reads the first entries of one type across every client, in the order asked for, such as the
     * latest payments. Served by the due_date index when ordered by due date.
     *
     * @param entryType the type of entry, such as a payment
     * @param pageable  how many entries to read, as the size of the first page, and their order
     * @return the entries, at most the page size of them
     */
    List<LedgerEntry> findByEntryType(LedgerEntryType entryType, Pageable pageable);

    /**
     * Totals every entry of every client due within a range, for the firm-wide overview. Served by
     * the due_date index.
     *
     * @param fromDate earliest due date to include
     * @param toDate   latest due date to include
     * @return one row per company, financial year and entry type with entries; empty when none matches
     */
    @Query(TOTALS + "where e.dueDate between :fromDate and :toDate" + BY_ROW)
    List<LedgerTotalsRow> totalEntries(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /**
     * Totals what was billed, received and discounted across every client on each day of a range,
     * both ends inclusive, for the overview's monthly trend. Served by the due_date index.
     *
     * @param charge   the entry type of service charges, whose amounts count as billed
     * @param payment  the entry type of payments, whose amounts count as paid
     * @param fromDate earliest date to include
     * @param toDate   latest date to include
     * @return one row per day with entries; days without entries are left out
     */
    @Query("select e.dueDate as dueDate, "
            + "sum(case when e.entryType = :charge then e.totalAmount else 0 end) as totalBilled, "
            + "sum(case when e.entryType = :payment then e.totalAmount else 0 end) as totalPaid, "
            + "sum(case when e.entryType = :charge then coalesce(e.discountAmount, 0) else 0 end) as totalDiscount "
            + "from LedgerEntry e where e.dueDate between :fromDate and :toDate group by e.dueDate")
    List<DailyTotalsRow> totalsByDay(@Param("charge") LedgerEntryType charge,
                                     @Param("payment") LedgerEntryType payment,
                                     @Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate);

    /**
     * Sums the service charges of each company by the date they fell due, for every date on or
     * before the one given, oldest first, so payments can be applied to the oldest charges first.
     *
     * @param charge the entry type of service charges
     * @param asOf   latest due date to include
     * @return one row per company and due date with charges, earliest due first
     */
    @Query("select e.companyId as companyId, e.dueDate as dueDate, sum(e.totalAmount) as totalCharged "
            + "from LedgerEntry e where e.entryType = :charge and e.dueDate <= :asOf "
            + "group by e.companyId, e.dueDate order by e.dueDate, e.companyId")
    List<CompanyChargeRow> chargesByCompanyAndDueDate(@Param("charge") LedgerEntryType charge,
                                                      @Param("asOf") LocalDate asOf);

    /**
     * Totals what each company has paid on or before a date.
     *
     * @param payment the entry type of payments
     * @param asOf    latest date received to include
     * @return one row per company that has paid anything; other companies are left out
     */
    @Query("select e.companyId as companyId, sum(e.totalAmount) as totalPaid from LedgerEntry e "
            + "where e.entryType = :payment and e.dueDate <= :asOf group by e.companyId")
    List<CompanyPaidRow> totalPaidByCompany(@Param("payment") LedgerEntryType payment,
                                            @Param("asOf") LocalDate asOf);

    /**
     * Totals every entry of every company of one client due within a range, whichever page is being
     * read.
     *
     * @param clientId the client
     * @param fromDate earliest due date to include
     * @param toDate   latest due date to include
     * @return one row per company, financial year and entry type with entries; empty when none matches
     */
    @Query(TOTALS + "where e.clientId = :clientId and e.dueDate between :fromDate and :toDate" + BY_ROW)
    List<LedgerTotalsRow> totalClientEntries(@Param("clientId") UUID clientId,
                                             @Param("fromDate") LocalDate fromDate,
                                             @Param("toDate") LocalDate toDate);
}
