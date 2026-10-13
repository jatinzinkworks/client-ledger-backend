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
