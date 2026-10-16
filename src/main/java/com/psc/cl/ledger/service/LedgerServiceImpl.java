package com.psc.cl.ledger.service;

import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.ServiceAssignment;
import com.psc.cl.client.repository.ClientCompanyRepository;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import com.psc.cl.client.service.NextDueDates;
import com.psc.cl.ledger.dto.ClientBalance;
import com.psc.cl.ledger.dto.ClientCompanyBalance;
import com.psc.cl.ledger.dto.ClientLedgerStatusResponse;
import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyBalance;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LastPayment;
import com.psc.cl.ledger.dto.LedgerEntryResponse;
import com.psc.cl.ledger.dto.LedgerEntryUpdateRequest;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.ledger.dto.LedgerListQuery;
import com.psc.cl.ledger.dto.LedgerListResponse;
import com.psc.cl.ledger.dto.LedgerSummary;
import com.psc.cl.ledger.dto.ManagerBalance;
import com.psc.cl.ledger.dto.MonthlyTotals;
import com.psc.cl.ledger.dto.PageMetadata;
import com.psc.cl.ledger.dto.PaymentListResponse;
import com.psc.cl.ledger.dto.PaymentRequest;
import com.psc.cl.ledger.dto.UnpaidCharge;
import com.psc.cl.ledger.exception.FutureOpeningBalanceDateException;
import com.psc.cl.ledger.exception.FuturePaymentDateException;
import com.psc.cl.ledger.exception.LedgerEntryNotFoundException;
import com.psc.cl.ledger.exception.NotAPaymentException;
import com.psc.cl.ledger.exception.OpeningBalanceNotCorrectableException;
import com.psc.cl.ledger.exception.PaymentNotCorrectableException;
import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.ledger.model.LedgerEntry;
import com.psc.cl.ledger.model.LedgerEntryType;
import com.psc.cl.ledger.repository.CatalogServiceBilledRow;
import com.psc.cl.ledger.repository.ClientBalanceRow;
import com.psc.cl.ledger.repository.ClientPendingRow;
import com.psc.cl.ledger.repository.CompanyBalanceRow;
import com.psc.cl.ledger.repository.CompanyChargeRow;
import com.psc.cl.ledger.repository.CompanyPaidRow;
import com.psc.cl.ledger.repository.DailyTotalsRow;
import com.psc.cl.ledger.repository.LedgerEntryRepository;
import com.psc.cl.ledger.repository.LedgerTotalsRow;
import com.psc.cl.ledger.repository.ManagerBalanceRow;
import com.psc.cl.ledger.repository.ManagerPendingRow;
import com.psc.cl.ledger.repository.ServiceBilledRow;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.repository.CatalogServiceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Default {@link LedgerService} backed by {@link LedgerEntryRepository}.
 *
 * <p>The company's due services are locked before anything is billed, so two postings for the
 * same company take turns: the second sees the due dates and posted flags the first moved on, and
 * finds nothing left to bill. The unique (company service, due date) key backs this up.
 */
@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerServiceImpl.class);

    private static final Comparator<LedgerEntry> EARLIEST_DUE_FIRST =
            Comparator.comparing(LedgerEntry::getDueDate).thenComparing(LedgerEntry::getServiceName);

    /**
     * Settles ties after the field a list is sorted by: the same order as {@link #EARLIEST_DUE_FIRST},
     * then the identifier, so an entry cannot appear on two pages or on none.
     */
    private static final List<String> TIE_BREAKERS = List.of("dueDate", "serviceName", "createdAt", "id");

    /** Order of the latest payments: most recently received, then most recently recorded, first. */
    private static final Sort LATEST_RECEIVED_FIRST = Sort.by(Sort.Direction.DESC, "dueDate", "createdAt", "id");

    /** Order of an export's ledger: earliest due first, as the lists sort by default. */
    private static final Sort EXPORT_ORDER = Sort.by(TIE_BREAKERS.toArray(String[]::new));

    /** Runs of anything but letters and digits, replaced in a file name so it is safe on every system. */
    private static final Pattern UNSAFE_FILE_NAME = Pattern.compile("[^A-Za-z0-9]+");

    /** What clients with no ledger entries owe, at the ledger's scale. */
    private static final BigDecimal NOTHING_PENDING = new BigDecimal("0.00");

    /** Zero at the ledger's scale, to start a sum from. */
    private static final BigDecimal NO_AMOUNT = new BigDecimal("0.00");

    /** Stands in for an open start of a date range; PostgreSQL dates cannot go back to LocalDate.MIN. */
    private static final LocalDate EARLIEST_DATE = LocalDate.of(1, 1, 1);

    /** Stands in for an open end of a date range. */
    private static final LocalDate LATEST_DATE = LocalDate.of(9999, 12, 31);

    private final ClientRepository clientRepository;

    private final ClientCompanyRepository clientCompanyRepository;

    private final ServiceAssignmentRepository serviceAssignmentRepository;

    private final CatalogServiceRepository catalogServiceRepository;

    private final LedgerEntryRepository ledgerEntryRepository;

    private final Clock clock;

    @Override
    @Transactional
    public List<LedgerEntryResponse> postDueServices(UUID clientId, UUID companyId) {
        requireCompany(clientId, companyId);
        LocalDate today = LocalDate.now(clock);

        List<ServiceAssignment> due = serviceAssignmentRepository.findDueForUpdate(companyId, today);
        if (due.isEmpty()) {
            log.info("No services due for the ledger for company {} of client {} as of {}",
                    companyId, clientId, today);
            return List.of();
        }

        // One query each for the catalog services and the dates already billed, rather than one per service.
        Set<UUID> serviceIds = due.stream()
                .map(ServiceAssignment::getCatalogServiceId)
                .collect(Collectors.toSet());
        Map<UUID, CatalogService> servicesById = catalogServiceRepository.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(CatalogService::getId, Function.identity()));
        List<UUID> assignmentIds = due.stream().map(ServiceAssignment::getId).toList();
        Set<Period> posted = ledgerEntryRepository.findPostedPeriods(assignmentIds).stream()
                .map(period -> new Period(period.getCompanyServiceId(), period.getDueDate()))
                .collect(Collectors.toSet());

        List<LedgerEntry> entries = new ArrayList<>();
        for (ServiceAssignment assignment : due) {
            CatalogService service = catalogService(servicesById.get(assignment.getCatalogServiceId()),
                    assignment.getCatalogServiceId());
            entries.addAll(post(clientId, assignment, service, today, posted));
        }
        entries.sort(EARLIEST_DUE_FIRST);

        List<LedgerEntry> saved = ledgerEntryRepository.saveAll(entries);
        log.info("Posted {} ledger entries for company {} of client {} as of {}",
                saved.size(), companyId, clientId, today);

        return saved.stream().map(LedgerEntryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerEntryResponse getEntry(UUID clientId, UUID companyId, UUID entryId) {
        requireCompany(clientId, companyId);
        return LedgerEntryResponse.from(requireEntry(companyId, entryId));
    }

    @Override
    @Transactional
    public LedgerEntryResponse updateEntry(UUID clientId, UUID companyId, UUID entryId,
                                           LedgerEntryUpdateRequest request) {
        requireCompany(clientId, companyId);
        LedgerEntry entry = requireEntry(companyId, entryId);
        if (entry.getEntryType() == LedgerEntryType.PAYMENT) {
            throw new PaymentNotCorrectableException(entryId);
        }
        if (entry.getEntryType() == LedgerEntryType.OPENING_BALANCE) {
            throw new OpeningBalanceNotCorrectableException(entryId);
        }

        // Same rounding as posting, so the corrected figures still add up.
        LedgerAmounts amounts = LedgerAmounts.calculate(twoPlaces(request.serviceFee()),
                twoPlaces(request.discountPercent()), twoPlaces(request.gstRatePercent()));
        applyAmounts(entry, amounts);

        // Flushing here stamps updatedAt before it is returned.
        ledgerEntryRepository.flush();
        log.info("Corrected ledger entry {} of company {} of client {}", entryId, companyId, clientId);

        return LedgerEntryResponse.from(entry);
    }

    @Override
    @Transactional
    public LedgerEntryResponse recordPayment(UUID clientId, UUID companyId, PaymentRequest request) {
        requireCompany(clientId, companyId);
        LocalDate today = LocalDate.now(clock);
        requireReceivedByToday(request, today);

        LedgerEntry payment = new LedgerEntry();
        payment.setClientId(clientId);
        payment.setCompanyId(companyId);
        payment.setEntryType(LedgerEntryType.PAYMENT);
        payment.setEntryDate(today);
        applyPayment(payment, request);

        LedgerEntry saved = ledgerEntryRepository.save(payment);
        log.info("Recorded payment {} by {} for company {} of client {}, received {}", saved.getId(),
                saved.getPaymentMethod(), companyId, clientId, saved.getDueDate());

        return LedgerEntryResponse.from(saved);
    }

    @Override
    @Transactional
    public LedgerEntryResponse recordOpeningBalance(UUID clientId, UUID companyId, BigDecimal amount,
                                                    LocalDate balanceDate) {
        if (amount.signum() == 0) {
            throw new IllegalArgumentException("An opening balance of zero is not recorded");
        }
        // "Today" is the firm's calendar day, read from the business clock.
        LocalDate today = LocalDate.now(clock);
        LocalDate date = balanceDate == null ? today : balanceDate;
        if (date.isAfter(today)) {
            throw new FutureOpeningBalanceDateException(date, today);
        }

        LedgerEntry opening = new LedgerEntry();
        opening.setClientId(clientId);
        opening.setCompanyId(companyId);
        opening.setEntryType(LedgerEntryType.OPENING_BALANCE);
        opening.setDueDate(date);
        opening.setFinancialYear(FinancialYears.of(date));
        opening.setEntryDate(today);
        opening.setTotalAmount(twoPlaces(amount));

        LedgerEntry saved = ledgerEntryRepository.save(opening);
        log.info("Recorded the opening balance {} of company {} of client {}, as of {}", saved.getId(), companyId,
                clientId, date);
        return LedgerEntryResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean clientHasEntries(UUID clientId) {
        return ledgerEntryRepository.existsByClientId(clientId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean companyHasEntries(UUID companyId) {
        return ledgerEntryRepository.existsByCompanyId(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentListResponse listPayments(UUID clientId, UUID companyId, LedgerListQuery query) {
        requireCompany(clientId, companyId);
        LocalDate from = startOf(query.fromDate());
        LocalDate to = endOf(query.toDate());

        Page<LedgerEntry> payments = ledgerEntryRepository.findByCompanyIdAndEntryTypeAndDueDateBetween(companyId,
                LedgerEntryType.PAYMENT, from, to, pageRequest(query));
        return PaymentListResponse.builder()
                .payments(payments.map(LedgerEntryResponse::from).getContent())
                .page(PageMetadata.of(payments))
                // The ledger's own totals, keeping only its payment rows.
                .summary(LedgerSummaries.payments(ledgerEntryRepository.totalCompanyEntries(companyId, from, to)))
                .build();
    }

    @Override
    @Transactional
    public LedgerEntryResponse updatePayment(UUID clientId, UUID companyId, UUID paymentId,
                                             PaymentRequest request) {
        requireCompany(clientId, companyId);
        LedgerEntry payment = requireEntry(companyId, paymentId);
        if (payment.getEntryType() != LedgerEntryType.PAYMENT) {
            throw new NotAPaymentException(paymentId);
        }
        requireReceivedByToday(request, LocalDate.now(clock));

        applyPayment(payment, request);

        // Flushing here stamps updatedAt before it is returned.
        ledgerEntryRepository.flush();
        log.info("Updated payment {} of company {} of client {}, received {}", paymentId, companyId, clientId,
                payment.getDueDate());

        return LedgerEntryResponse.from(payment);
    }

    /**
     * Writes a payment's details onto its entry, whether newly recorded or updated. The date received
     * places it in the ledger's order, date filters and financial year; the amount is stored as
     * negative, so a ledger's totals add up to what is still owed.
     */
    private static void applyPayment(LedgerEntry payment, PaymentRequest request) {
        payment.setDueDate(request.dateReceived());
        payment.setFinancialYear(FinancialYears.of(request.dateReceived()));
        payment.setTotalAmount(twoPlaces(request.amountReceived()).negate());
        payment.setPaymentMethod(request.method());
        payment.setPaymentReference(trimToNull(request.reference()));
        payment.setComment(trimToNull(request.comment()));
    }

    /** "Today" is the firm's calendar day, read from the business clock rather than the server's zone. */
    private static void requireReceivedByToday(PaymentRequest request, LocalDate today) {
        if (request.dateReceived().isAfter(today)) {
            throw new FuturePaymentDateException(request.dateReceived(), today);
        }
    }

    @Override
    @Transactional
    public void deleteEntry(UUID clientId, UUID companyId, UUID entryId) {
        requireCompany(clientId, companyId);
        LedgerEntry entry = requireEntry(companyId, entryId);

        ledgerEntryRepository.delete(entry);
        log.info("Deleted ledger entry {} of company {} of client {}, due {}", entryId, companyId, clientId,
                entry.getDueDate());
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyLedgerSummaryResponse summarizeCompany(UUID clientId, UUID companyId,
                                                         FinancialYear financialYear) {
        return summarize(clientId, requireCompany(clientId, companyId), financialYear);
    }

    /** A company's summary for a financial year, once the company has been found to be the client's. */
    private CompanyLedgerSummaryResponse summarize(UUID clientId, ClientCompany company,
                                                   FinancialYear financialYear) {
        LocalDate from = financialYear.firstDay();
        LocalDate to = financialYear.lastDay();

        // The same totals the ledger lists use, so the figures always agree with them.
        LedgerSummary totals = LedgerSummaries.of(ledgerEntryRepository.totalCompanyEntries(company.getId(),
                from, to));
        LedgerEntry lastPayment = ledgerEntryRepository
                .findFirstByCompanyIdAndEntryTypeAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(company.getId(),
                        LedgerEntryType.PAYMENT, from, to)
                .orElse(null);

        return companySummary(clientId, company, financialYear, totals, lastPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public ClientLedgerSummaryResponse summarizeClient(UUID clientId, FinancialYear financialYear) {
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
        LocalDate from = financialYear.firstDay();
        LocalDate to = financialYear.lastDay();

        // The same totals the ledger lists use, across every company of the client.
        LedgerSummary totals = LedgerSummaries.of(ledgerEntryRepository.totalClientEntries(clientId, from, to));
        LastPayment lastPayment = ledgerEntryRepository
                .findFirstByClientIdAndEntryTypeAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(clientId,
                        LedgerEntryType.PAYMENT, from, to)
                .map(payment -> lastPaymentBuilder(payment)
                        .companyId(payment.getCompanyId())
                        .companyName(companyName(client, payment.getCompanyId()))
                        .build())
                .orElse(null);

        return ClientLedgerSummaryResponse.builder()
                .clientId(clientId)
                .clientName(fullName(client))
                .financialYear(financialYear.label())
                .fromDate(from)
                .toDate(to)
                .totalBilled(totals.getTotalAmount())
                .openingBalance(totals.getOpeningBalance())
                .totalPaymentReceived(totals.getTotalPaid())
                .pending(totals.getBalance())
                .totalDiscount(totals.getTotalDiscountAmount())
                .lastPayment(lastPayment)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ClientLedgerStatusResponse clientLedgerStatus(UUID clientId, FinancialYear financialYear,
                                                         boolean pendingOnly) {
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
        Map<UUID, String> companyNames = client.getCompanies().stream()
                .collect(Collectors.toMap(ClientCompany::getId, ClientCompany::getCompanyName));

        // The same totals query the ledger lists use, over every date, so the years agree with them.
        List<LedgerTotalsRow> rows = ledgerEntryRepository.totalClientEntries(clientId, EARLIEST_DATE, LATEST_DATE);
        LocalDate today = LocalDate.now(clock);
        FinancialYear currentYear = FinancialYears.containing(today);
        List<FinancialYear> years = yearsLatestFirst(firstYear(client, rows, today), lastYear(rows, currentYear));
        // The client's span stays the same when one year is asked for, even one outside it.
        List<FinancialYear> listed = financialYear == null ? years : List.of(financialYear);

        return ClientLedgerStatusResponse.builder()
                .clientId(clientId)
                .clientName(fullName(client))
                .clientSince(years.getLast().firstDay())
                .yearCount(years.size())
                .years(LedgerSummaries.statusesByYear(rows, companyNames, listed, currentYear, pendingOnly))
                .build();
    }

    /** The year the client was added in, or that of their earliest entry if earlier, as for migrated history. */
    private FinancialYear firstYear(Client client, List<LedgerTotalsRow> rows, LocalDate today) {
        LocalDate addedOn = client.getCreatedAt() == null ? today : LocalDate.ofInstant(client.getCreatedAt(),
                clock.getZone());
        FinancialYear added = FinancialYears.containing(addedOn);
        return rows.stream()
                .map(LedgerTotalsRow::getFinancialYear)
                .map(FinancialYears::parse)
                .filter(year -> year.firstDay().isBefore(added.firstDay()))
                .min(Comparator.comparing(FinancialYear::firstDay))
                .orElse(added);
    }

    /** The current year, unless an entry falls later. */
    private static FinancialYear lastYear(List<LedgerTotalsRow> rows, FinancialYear currentYear) {
        return rows.stream()
                .map(LedgerTotalsRow::getFinancialYear)
                .map(FinancialYears::parse)
                .filter(year -> year.firstDay().isAfter(currentYear.firstDay()))
                .max(Comparator.comparing(FinancialYear::firstDay))
                .orElse(currentYear);
    }

    /** At least the last year, even if the client was somehow added after it. */
    private static List<FinancialYear> yearsLatestFirst(FinancialYear first, FinancialYear last) {
        LocalDate earliest = first.firstDay().isAfter(last.firstDay()) ? last.firstDay() : first.firstDay();
        List<FinancialYear> years = new ArrayList<>();
        for (FinancialYear year = last; !year.firstDay().isBefore(earliest);
             year = FinancialYears.containing(year.firstDay().minusDays(1))) {
            years.add(year);
        }
        return years;
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerExport exportClient(UUID clientId, FinancialYear financialYear) {
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
        Map<UUID, String> companyNames = client.getCompanies().stream()
                .collect(Collectors.toMap(ClientCompany::getId, ClientCompany::getCompanyName));

        // The Summary sheet shows exactly what the summary endpoint returns for the year.
        ClientLedgerSummaryResponse summary = summarizeClient(clientId, financialYear);
        List<LedgerEntry> entries = ledgerEntryRepository.findAllByClientIdAndDueDateBetween(clientId,
                financialYear.firstDay(), financialYear.lastDay(), EXPORT_ORDER);
        byte[] content = LedgerWorkbook.forClient(summary, entries, companyNames, LocalDate.now(clock));

        log.info("Exported {} ledger entries of client {} for FY {}", entries.size(), clientId,
                financialYear.label());
        return new LedgerExport(exportFileName(fullName(client), "Client", financialYear), content);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerExport exportCompany(UUID clientId, UUID companyId, FinancialYear financialYear) {
        ClientCompany company = requireCompany(clientId, companyId);

        // The Summary sheet shows exactly what the company summary endpoint returns for the year.
        CompanyLedgerSummaryResponse summary = summarize(clientId, company, financialYear);
        List<LedgerEntry> entries = ledgerEntryRepository.findAllByCompanyIdAndDueDateBetween(companyId,
                financialYear.firstDay(), financialYear.lastDay(), EXPORT_ORDER);
        byte[] content = LedgerWorkbook.forCompany(fullName(company.getClient()), summary, entries,
                LocalDate.now(clock));

        log.info("Exported {} ledger entries of company {} of client {} for FY {}", entries.size(), companyId,
                clientId, financialYear.label());
        return new LedgerExport(exportFileName(company.getCompanyName(), "Company", financialYear), content);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerExport statementForClient(UUID clientId, FinancialYear financialYear) {
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
        Map<UUID, String> companyNames = client.getCompanies().stream()
                .collect(Collectors.toMap(ClientCompany::getId, ClientCompany::getCompanyName));

        // The summary shows exactly what the summary endpoint returns for the year.
        ClientLedgerSummaryResponse summary = summarizeClient(clientId, financialYear);
        List<LedgerEntry> entries = ledgerEntryRepository.findAllByClientIdAndDueDateBetween(clientId,
                financialYear.firstDay(), financialYear.lastDay(), EXPORT_ORDER);
        byte[] content = LedgerStatementPdf.forClient(summary, entries, companyNames, LocalDate.now(clock));

        log.info("Wrote a statement of {} ledger entries of client {} for FY {}", entries.size(), clientId,
                financialYear.label());
        return new LedgerExport(exportFileName(fullName(client), "Client", financialYear, ".pdf"), content);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerExport statementForCompany(UUID clientId, UUID companyId, FinancialYear financialYear) {
        ClientCompany company = requireCompany(clientId, companyId);

        // The summary shows exactly what the company summary endpoint returns for the year.
        CompanyLedgerSummaryResponse summary = summarize(clientId, company, financialYear);
        List<LedgerEntry> entries = ledgerEntryRepository.findAllByCompanyIdAndDueDateBetween(companyId,
                financialYear.firstDay(), financialYear.lastDay(), EXPORT_ORDER);
        byte[] content = LedgerStatementPdf.forCompany(fullName(company.getClient()), summary, entries,
                LocalDate.now(clock));

        log.info("Wrote a statement of {} ledger entries of company {} of client {} for FY {}", entries.size(),
                companyId, clientId, financialYear.label());
        return new LedgerExport(exportFileName(company.getCompanyName(), "Company", financialYear, ".pdf"),
                content);
    }

    private static String fullName(Client client) {
        return client.getFirstName() + " " + client.getLastName();
    }

    /**
     * Names an export after whose ledger it is and the year, such as
     * {@code Rohan_Mehta_Ledger_FY2026-27.xlsx}. Anything but letters and digits in the name becomes
     * an underscore, so the name is safe in a download header and on any file system.
     *
     * @param name          the client's or company's name
     * @param fallback      used instead when no letters or digits of the name survive, as for a name
     *                      written only in Devanagari
     * @param financialYear the year exported
     */
    static String exportFileName(String name, String fallback, FinancialYear financialYear) {
        return exportFileName(name, fallback, financialYear, ".xlsx");
    }

    /**
     * Names an export as {@link #exportFileName(String, String, FinancialYear)} does, with the given
     * file extension, such as {@code .pdf}.
     */
    static String exportFileName(String name, String fallback, FinancialYear financialYear, String extension) {
        String safe = UNSAFE_FILE_NAME.matcher(name).replaceAll("_").replaceAll("^_|_$", "");
        return (safe.isEmpty() ? fallback : safe) + "_Ledger_FY" + financialYear.label() + extension;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> pendingByClient(Collection<UUID> clientIds) {
        if (clientIds.isEmpty()) {
            // Nothing to total, and an empty IN list is not valid SQL.
            return Map.of();
        }
        return ledgerEntryRepository.totalPendingByClient(clientIds).stream()
                .collect(Collectors.toMap(ClientPendingRow::getClientId, ClientPendingRow::getTotalPending));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> pendingByManager() {
        return ledgerEntryRepository.totalPendingByManager().stream()
                .collect(Collectors.toMap(ManagerPendingRow::getManagerId, ManagerPendingRow::getTotalPending));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal pendingForManager(UUID managerId) {
        BigDecimal pending = ledgerEntryRepository.totalPendingForManager(managerId);
        return pending == null ? NOTHING_PENDING : pending;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> billedByCatalogService(FinancialYear financialYear) {
        return billedByCatalogService(financialYear.firstDay(), financialYear.lastDay());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> billedByCatalogServiceToDate() {
        // "Today" is the firm's calendar day, read from the business clock.
        return billedByCatalogService(EARLIEST_DATE, LocalDate.now(clock));
    }

    private Map<UUID, BigDecimal> billedByCatalogService(LocalDate from, LocalDate to) {
        return ledgerEntryRepository.totalBilledByCatalogService(LedgerEntryType.SERVICE_CHARGE, from, to).stream()
                .collect(Collectors.toMap(CatalogServiceBilledRow::getCatalogServiceId,
                        CatalogServiceBilledRow::getTotalBilled));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> billedByService(UUID companyId, FinancialYear financialYear) {
        return ledgerEntryRepository.totalBilledByService(companyId, LedgerEntryType.SERVICE_CHARGE,
                        financialYear.firstDay(), financialYear.lastDay()).stream()
                .collect(Collectors.toMap(ServiceBilledRow::getCompanyServiceId, ServiceBilledRow::getTotalBilled));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientBalance> topClientsByPending(FinancialYear financialYear, int limit) {
        LocalDate from = financialYear == null ? EARLIEST_DATE : financialYear.firstDay();
        LocalDate to = financialYear == null ? LATEST_DATE : financialYear.lastDay();
        return clientBalances(ledgerEntryRepository.topClientsByPending(LedgerEntryType.SERVICE_CHARGE,
                LedgerEntryType.PAYMENT, from, to, PageRequest.of(0, limit)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientBalance> topClientsByBilled(FinancialYear financialYear, int limit) {
        LocalDate from = financialYear == null ? EARLIEST_DATE : financialYear.firstDay();
        LocalDate to = financialYear == null ? LATEST_DATE : financialYear.lastDay();
        return clientBalances(ledgerEntryRepository.topClientsByBilled(LedgerEntryType.SERVICE_CHARGE,
                LedgerEntryType.PAYMENT, from, to, PageRequest.of(0, limit)));
    }

    private static List<ClientBalance> clientBalances(List<ClientBalanceRow> rows) {
        return rows.stream()
                .map(row -> new ClientBalance(row.getClientId(), twoPlacesOf(row.getTotalBilled()),
                        twoPlacesOf(row.getTotalPaid()).negate(), twoPlacesOf(row.getPending())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagerBalance> topManagersByPending(FinancialYear financialYear, int limit) {
        LocalDate from = financialYear == null ? EARLIEST_DATE : financialYear.firstDay();
        LocalDate to = financialYear == null ? LATEST_DATE : financialYear.lastDay();
        return managerBalances(ledgerEntryRepository.topManagersByPending(LedgerEntryType.SERVICE_CHARGE,
                LedgerEntryType.PAYMENT, from, to, PageRequest.of(0, limit)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagerBalance> topManagersByBilled(FinancialYear financialYear, int limit) {
        LocalDate from = financialYear == null ? EARLIEST_DATE : financialYear.firstDay();
        LocalDate to = financialYear == null ? LATEST_DATE : financialYear.lastDay();
        return managerBalances(ledgerEntryRepository.topManagersByBilled(LedgerEntryType.SERVICE_CHARGE,
                LedgerEntryType.PAYMENT, from, to, PageRequest.of(0, limit)));
    }

    private static List<ManagerBalance> managerBalances(List<ManagerBalanceRow> rows) {
        return rows.stream()
                .map(row -> new ManagerBalance(row.getManagerId(), twoPlacesOf(row.getTotalBilled()),
                        twoPlacesOf(row.getTotalPaid()).negate(), twoPlacesOf(row.getPending())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientCompanyBalance> topCompaniesByPending(FinancialYear financialYear, int limit) {
        LocalDate from = financialYear == null ? EARLIEST_DATE : financialYear.firstDay();
        LocalDate to = financialYear == null ? LATEST_DATE : financialYear.lastDay();
        return companyBalances(ledgerEntryRepository.topCompaniesByPending(LedgerEntryType.SERVICE_CHARGE,
                LedgerEntryType.PAYMENT, from, to, PageRequest.of(0, limit)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientCompanyBalance> topCompaniesByBilled(FinancialYear financialYear, int limit) {
        LocalDate from = financialYear == null ? EARLIEST_DATE : financialYear.firstDay();
        LocalDate to = financialYear == null ? LATEST_DATE : financialYear.lastDay();
        return companyBalances(ledgerEntryRepository.topCompaniesByBilled(LedgerEntryType.SERVICE_CHARGE,
                LedgerEntryType.PAYMENT, from, to, PageRequest.of(0, limit)));
    }

    private static List<ClientCompanyBalance> companyBalances(List<CompanyBalanceRow> rows) {
        return rows.stream()
                .map(row -> new ClientCompanyBalance(row.getCompanyId(), row.getClientId(),
                        twoPlacesOf(row.getTotalBilled()), twoPlacesOf(row.getTotalPaid()).negate(),
                        twoPlacesOf(row.getPending())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> recentPayments(int limit) {
        // A payment's due date is the date it was received; the identifier settles a full tie.
        return ledgerEntryRepository.findByEntryType(LedgerEntryType.PAYMENT,
                        PageRequest.of(0, limit, LATEST_RECEIVED_FIRST)).stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }

    /** Sums of the 0 in a CASE come back at its scale, so bring them to the ledger's two places. */
    private static BigDecimal twoPlacesOf(BigDecimal sum) {
        return sum.setScale(2, RoundingMode.UNNECESSARY);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerSummary totalsForYear(FinancialYear financialYear) {
        // The same totals the ledger lists and summaries use, over every client.
        return totalsBetween(financialYear.firstDay(), financialYear.lastDay());
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerSummary totalsBetween(LocalDate fromDate, LocalDate toDate) {
        return LedgerSummaries.of(ledgerEntryRepository.totalEntries(fromDate, toDate));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonthlyTotals> monthlyTotals(FinancialYear financialYear) {
        Map<YearMonth, List<DailyTotalsRow>> rowsByMonth = ledgerEntryRepository.totalsByDay(
                        LedgerEntryType.SERVICE_CHARGE, LedgerEntryType.PAYMENT,
                        financialYear.firstDay(), financialYear.lastDay()).stream()
                .collect(Collectors.groupingBy(row -> YearMonth.from(row.getDueDate())));

        List<MonthlyTotals> months = new ArrayList<>();
        YearMonth last = YearMonth.from(financialYear.lastDay());
        for (YearMonth month = YearMonth.from(financialYear.firstDay()); !month.isAfter(last);
             month = month.plusMonths(1)) {
            List<DailyTotalsRow> rows = rowsByMonth.getOrDefault(month, List.of());
            months.add(new MonthlyTotals(month, sumOf(rows, DailyTotalsRow::getTotalBilled),
                    sumOf(rows, DailyTotalsRow::getTotalPaid).negate(), sumOf(rows, DailyTotalsRow::getTotalDiscount)));
        }
        return months;
    }

    private static BigDecimal sumOf(List<DailyTotalsRow> rows, Function<DailyTotalsRow, BigDecimal> amount) {
        return rows.stream().map(amount).map(LedgerServiceImpl::twoPlacesOf).reduce(NO_AMOUNT, BigDecimal::add);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnpaidCharge> unpaidCharges(LocalDate asOf) {
        // What each company has paid, as a positive amount, still to be applied to its charges. An
        // opening balance in credit counts as paid, and one owed as a charge falling due on its date.
        Map<UUID, BigDecimal> unapplied = ledgerEntryRepository.totalPaidByCompany(LedgerEntryType.PAYMENT,
                        LedgerEntryType.OPENING_BALANCE, asOf)
                .stream()
                .collect(Collectors.toMap(CompanyPaidRow::getCompanyId, row -> row.getTotalPaid().negate()));

        // The rows come oldest first, so each payment settles the oldest charges it can.
        List<UnpaidCharge> unpaid = new ArrayList<>();
        for (CompanyChargeRow row : ledgerEntryRepository.chargesByCompanyAndDueDate(
                LedgerEntryType.SERVICE_CHARGE, LedgerEntryType.OPENING_BALANCE, asOf)) {
            BigDecimal available = unapplied.getOrDefault(row.getCompanyId(), NO_AMOUNT);
            BigDecimal applied = available.min(row.getTotalCharged()).max(NO_AMOUNT);
            unapplied.put(row.getCompanyId(), available.subtract(applied));
            BigDecimal remaining = row.getTotalCharged().subtract(applied);
            if (remaining.signum() > 0) {
                unpaid.add(new UnpaidCharge(row.getCompanyId(), row.getDueDate(), remaining));
            }
        }
        log.debug("Found {} unpaid charges as of {}", unpaid.size(), asOf);
        return unpaid;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CompanyBalance> companyBalances(UUID clientId, FinancialYear financialYear) {
        // The same totals query the ledger lists and summaries use, once for every company.
        return LedgerSummaries.balancesByCompany(ledgerEntryRepository.totalClientEntries(clientId,
                financialYear.firstDay(), financialYear.lastDay()));
    }

    /**
     * An entry's company is held to its client by foreign key, so a company missing from the client
     * means the data is inconsistent and is reported as a server error.
     */
    private static String companyName(Client client, UUID companyId) {
        return client.getCompanies().stream()
                .filter(company -> company.getId().equals(companyId))
                .map(ClientCompany::getCompanyName)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Ledger entries refer to company " + companyId
                        + ", which is not a company of client " + client.getId()));
    }

    /**
     * One company's summary for a financial year, from the same totals the ledger lists use, so its
     * pending is their balance: billed plus opening balance less received.
     *
     * @param lastPayment the company's latest payment in the year, or null when it made none
     */
    private static CompanyLedgerSummaryResponse companySummary(UUID clientId, ClientCompany company,
                                                               FinancialYear financialYear, LedgerSummary totals,
                                                               LedgerEntry lastPayment) {
        return CompanyLedgerSummaryResponse.builder()
                .clientId(clientId)
                .companyId(company.getId())
                .companyName(company.getCompanyName())
                .financialYear(financialYear.label())
                .fromDate(financialYear.firstDay())
                .toDate(financialYear.lastDay())
                .totalBilled(totals.getTotalAmount())
                .openingBalance(totals.getOpeningBalance())
                .totalPaymentReceived(totals.getTotalPaid())
                .pending(totals.getBalance())
                .totalDiscount(totals.getTotalDiscountAmount())
                .lastPayment(lastPayment == null ? null : lastPaymentBuilder(lastPayment).build())
                .build();
    }

    /** A payment's date and amount, the amount turned positive from how the ledger stores it. */
    private static LastPayment.LastPaymentBuilder lastPaymentBuilder(LedgerEntry payment) {
        return LastPayment.builder()
                .date(payment.getDueDate())
                .amount(payment.getTotalAmount().negate());
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerListResponse listCompanyEntries(UUID clientId, UUID companyId, LedgerListQuery query) {
        requireCompany(clientId, companyId);
        LocalDate from = startOf(query.fromDate());
        LocalDate to = endOf(query.toDate());

        Page<LedgerEntry> entries = ledgerEntryRepository.findByCompanyIdAndDueDateBetween(companyId, from, to,
                pageRequest(query));
        return listResponse(entries,
                LedgerSummaries.of(ledgerEntryRepository.totalCompanyEntries(companyId, from, to)));
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerListResponse listClientEntries(UUID clientId, LedgerListQuery query) {
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
        // Names label the summary's company breakdown; read here, inside the transaction.
        Map<UUID, String> companyNames = client.getCompanies().stream()
                .collect(Collectors.toMap(ClientCompany::getId, ClientCompany::getCompanyName));
        LocalDate from = startOf(query.fromDate());
        LocalDate to = endOf(query.toDate());

        Page<LedgerEntry> entries = ledgerEntryRepository.findByClientIdAndDueDateBetween(clientId, from, to,
                pageRequest(query));
        return listResponse(entries,
                LedgerSummaries.of(ledgerEntryRepository.totalClientEntries(clientId, from, to), companyNames));
    }

    /**
     * The page asked for, ordered by the field asked for and then by {@link #TIE_BREAKERS}, which
     * always ascend.
     */
    static PageRequest pageRequest(LedgerListQuery query) {
        String sortBy = query.sortBy().property();
        Sort tieBreakers = Sort.by(TIE_BREAKERS.stream().filter(property -> !property.equals(sortBy))
                .toArray(String[]::new));
        return PageRequest.of(query.page(), query.size(), Sort.by(query.direction(), sortBy).and(tieBreakers));
    }

    /** The summary totals every entry matching the filters, not only those on the page. */
    private static LedgerListResponse listResponse(Page<LedgerEntry> entries, LedgerSummary summary) {
        return LedgerListResponse.builder()
                .entries(entries.map(LedgerEntryResponse::from).getContent())
                .page(PageMetadata.of(entries))
                .summary(summary)
                .build();
    }

    /** An open start becomes the earliest date the column can hold, so one query serves every range. */
    private static LocalDate startOf(LocalDate fromDate) {
        return fromDate == null ? EARLIEST_DATE : fromDate;
    }

    /** An open end becomes the latest date anyone would bill, so one query serves every range. */
    private static LocalDate endOf(LocalDate toDate) {
        return toDate == null ? LATEST_DATE : toDate;
    }

    /**
     * Bills one assignment for every date it fell due, up to today, and moves it on: a ONE_OFF
     * service is marked as posted, and a recurring one takes its next due date after today. Dates
     * already billed are skipped, which can happen when an earlier delivery date was set again.
     *
     * <p>The schedule is the catalog service's current one, as on every assignment write.
     */
    private List<LedgerEntry> post(UUID clientId, ServiceAssignment assignment, CatalogService service,
                                   LocalDate today, Set<Period> posted) {
        assignment.setServiceType(service.getBillingFrequency());
        List<LedgerEntry> entries = new ArrayList<>();

        if (service.getBillingFrequency() == BillingFrequency.ONE_OFF) {
            addUnlessPosted(entries, posted, clientId, assignment, service, assignment.getNextDueDate(), today);
            assignment.setLedgerPosted(true);
            return entries;
        }

        // Brings a date set under an earlier schedule onto the current one before stepping through it.
        LocalDate dueDate = NextDueDates.onOrAfter(service, assignment.getNextDueDate());
        while (!dueDate.isAfter(today)) {
            addUnlessPosted(entries, posted, clientId, assignment, service, dueDate, today);
            dueDate = NextDueDates.onOrAfter(service, dueDate.plusDays(1));
        }
        assignment.setNextDueDate(dueDate);
        return entries;
    }

    private void addUnlessPosted(List<LedgerEntry> entries, Set<Period> posted, UUID clientId,
                                 ServiceAssignment assignment, CatalogService service, LocalDate dueDate,
                                 LocalDate today) {
        if (posted.contains(new Period(assignment.getId(), dueDate))) {
            log.warn("Service assignment {} was already billed for {}; skipping it", assignment.getId(), dueDate);
            return;
        }
        entries.add(entry(clientId, assignment, service, dueDate, today));
    }

    private LedgerEntry entry(UUID clientId, ServiceAssignment assignment, CatalogService service,
                              LocalDate dueDate, LocalDate today) {
        LedgerAmounts amounts = LedgerAmounts.calculate(service.getStandardFee(),
                assignment.getDiscountPercent(), service.getGstRatePercent());

        LedgerEntry entry = new LedgerEntry();
        entry.setClientId(clientId);
        entry.setCompanyId(assignment.getCompany().getId());
        entry.setEntryType(LedgerEntryType.SERVICE_CHARGE);
        entry.setCompanyServiceId(assignment.getId());
        entry.setCatalogServiceId(service.getId());
        entry.setServiceName(service.getServiceName());
        entry.setServiceType(service.getBillingFrequency());
        entry.setDueDate(dueDate);
        entry.setFinancialYear(FinancialYears.of(dueDate));
        entry.setEntryDate(today);
        applyAmounts(entry, amounts);
        return entry;
    }

    private static void applyAmounts(LedgerEntry entry, LedgerAmounts amounts) {
        entry.setServiceFee(amounts.serviceFee());
        entry.setDiscountPercent(amounts.discountPercent());
        entry.setDiscountAmount(amounts.discountAmount());
        entry.setTaxableAmount(amounts.taxableAmount());
        entry.setGstRatePercent(amounts.gstRatePercent());
        entry.setGstAmount(amounts.gstAmount());
        entry.setTotalAmount(amounts.totalAmount());
    }

    /** Validation has already limited the value to two places; this brings it to the column's scale. */
    private static BigDecimal twoPlaces(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LedgerEntry requireEntry(UUID companyId, UUID entryId) {
        return ledgerEntryRepository.findByIdAndCompanyId(entryId, companyId)
                .orElseThrow(() -> new LedgerEntryNotFoundException(companyId, entryId));
    }

    private void requireClient(UUID clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new ClientNotFoundException(clientId);
        }
    }

    private ClientCompany requireCompany(UUID clientId, UUID companyId) {
        requireClient(clientId);
        return clientCompanyRepository.findByIdAndClientId(companyId, clientId)
                .orElseThrow(() -> new CompanyNotFoundException(clientId, companyId));
    }

    /**
     * The foreign key guarantees an assigned catalog service exists, so a miss here means the data
     * is inconsistent and is reported as a server error.
     */
    private CatalogService catalogService(CatalogService service, UUID serviceId) {
        if (service == null) {
            throw new IllegalStateException("Assigned catalog service " + serviceId + " does not exist");
        }
        return service;
    }

    /** A due date of one company service. */
    private record Period(UUID companyServiceId, LocalDate dueDate) {
    }
}
