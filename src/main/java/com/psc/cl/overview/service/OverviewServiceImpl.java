package com.psc.cl.overview.service;

import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.repository.ClientCompanyRepository;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.ClientRepository.ManagerClientCount;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import com.psc.cl.globalsettings.model.PaymentTerms;
import com.psc.cl.globalsettings.repository.PaymentTermsRepository;
import com.psc.cl.ledger.dto.ClientBalance;
import com.psc.cl.ledger.dto.ClientCompanyBalance;
import com.psc.cl.ledger.dto.LedgerEntryResponse;
import com.psc.cl.ledger.dto.LedgerSummary;
import com.psc.cl.ledger.dto.ManagerBalance;
import com.psc.cl.ledger.dto.UnpaidCharge;
import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.ledger.service.FinancialYears;
import com.psc.cl.ledger.service.LedgerService;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.repository.ManagerRepository;
import com.psc.cl.overview.dto.AgeingBucket;
import com.psc.cl.overview.dto.AgeingResponse;
import com.psc.cl.overview.dto.BilledByCategoryResponse;
import com.psc.cl.overview.dto.BilledClient;
import com.psc.cl.overview.dto.BilledCompany;
import com.psc.cl.overview.dto.BilledManager;
import com.psc.cl.overview.dto.CategoryBilling;
import com.psc.cl.overview.dto.CategoryService;
import com.psc.cl.overview.dto.MonthlyTrend;
import com.psc.cl.overview.dto.MonthlyTrendResponse;
import com.psc.cl.overview.dto.OverviewResponse;
import com.psc.cl.overview.dto.PendingClient;
import com.psc.cl.overview.dto.PendingCompany;
import com.psc.cl.overview.dto.PendingManager;
import com.psc.cl.overview.dto.PeriodTotals;
import com.psc.cl.overview.dto.RecentPayment;
import com.psc.cl.overview.dto.RecentPaymentsResponse;
import com.psc.cl.overview.dto.TopBilledClientsResponse;
import com.psc.cl.overview.dto.TopBilledCompaniesResponse;
import com.psc.cl.overview.dto.TopBilledManagersResponse;
import com.psc.cl.overview.dto.TopPendingClientsResponse;
import com.psc.cl.overview.dto.TopPendingCompaniesResponse;
import com.psc.cl.overview.dto.TopPendingManagersResponse;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.model.ServiceCategory;
import com.psc.cl.servicecatalog.repository.CatalogServiceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link OverviewService}, drawing the amounts from the ledger and the counts from the
 * client module's repositories.
 */
@Service
@RequiredArgsConstructor
public class OverviewServiceImpl implements OverviewService {

    private static final Logger log = LoggerFactory.getLogger(OverviewServiceImpl.class);

    /** Zero at the ledger's scale, to start a sum from. */
    private static final BigDecimal NO_AMOUNT = new BigDecimal("0.00");

    /** The ranges of days past due the ageing splits what is owed into, newest first. */
    private static final List<AgeingRange> AGEING_RANGES = List.of(
            new AgeingRange("0-30", 0, 30),
            new AgeingRange("31-60", 31, 60),
            new AgeingRange("61-90", 61, 90),
            new AgeingRange("90+", 91, null));

    private final LedgerService ledgerService;

    private final ClientRepository clientRepository;

    private final ClientCompanyRepository clientCompanyRepository;

    private final ServiceAssignmentRepository serviceAssignmentRepository;

    private final ManagerRepository managerRepository;

    private final CatalogServiceRepository catalogServiceRepository;

    private final PaymentTermsRepository paymentTermsRepository;

    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public OverviewResponse overview(FinancialYear financialYear) {
        LedgerSummary totals = ledgerService.totalsForYear(financialYear);
        OverviewResponse overview = OverviewResponse.builder()
                .financialYear(financialYear.label())
                .fromDate(financialYear.firstDay())
                .toDate(financialYear.lastDay())
                .totalBilled(totals.getTotalAmount())
                .openingBalance(totals.getOpeningBalance())
                .totalPaymentReceived(totals.getTotalPaid())
                .pending(totals.getBalance())
                .totalDiscount(totals.getTotalDiscountAmount())
                .previousToDate(previousToDate(financialYear))
                .totalClients(clientRepository.count())
                .totalCompanies(clientCompanyRepository.count())
                .totalServices(serviceAssignmentRepository.count())
                .build();
        log.debug("Built the overview for FY {} across {} clients", financialYear.label(), overview.getTotalClients());
        return overview;
    }

    /**
     * The previous year's totals up to the point the given year has reached: today, a year earlier,
     * while the year is running; the whole previous year once it is over; nothing before it starts.
     * Charges are posted only once due and payments cannot be dated ahead, so the given year's own
     * totals already run only to date.
     */
    private PeriodTotals previousToDate(FinancialYear financialYear) {
        FinancialYear previous = FinancialYears.containing(financialYear.firstDay().minusYears(1));
        LocalDate reached = Collections.min(List.of(LocalDate.now(clock), financialYear.lastDay()));
        LocalDate toDate = reached.minusYears(1);
        LedgerSummary totals = ledgerService.totalsBetween(previous.firstDay(), toDate);
        return PeriodTotals.builder()
                .financialYear(previous.label())
                .fromDate(previous.firstDay())
                .toDate(toDate)
                .totalBilled(totals.getTotalAmount())
                .openingBalance(totals.getOpeningBalance())
                .totalPaymentReceived(totals.getTotalPaid())
                .pending(totals.getBalance())
                .totalDiscount(totals.getTotalDiscountAmount())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AgeingResponse ageing() {
        LocalDate asOf = LocalDate.now(clock);
        int overdueAfterDays = paymentTermsRepository.findFirstByOrderByCreatedAtAsc()
                .map(PaymentTerms::getMarkOverdueAfterDays)
                .orElse(PaymentTerms.DEFAULT_MARK_OVERDUE_AFTER_DAYS);
        List<UnpaidCharge> unpaid = ledgerService.unpaidCharges(asOf);

        List<AgeingBucket> buckets = AGEING_RANGES.stream()
                .map(range -> {
                    List<UnpaidCharge> inRange = unpaid.stream()
                            .filter(charge -> range.contains(daysPastDue(charge, asOf)))
                            .toList();
                    return AgeingBucket.builder()
                            .label(range.label())
                            .fromDays(range.fromDays())
                            .toDays(range.toDays())
                            .amount(sumUnpaid(inRange))
                            .companyCount(companyCount(inRange))
                            .build();
                })
                .toList();
        AgeingResponse ageing = AgeingResponse.builder()
                .asOf(asOf)
                .overdueAfterDays(overdueAfterDays)
                .buckets(buckets)
                .total(sumUnpaid(unpaid))
                .overdue(sumUnpaid(unpaid.stream()
                        .filter(charge -> daysPastDue(charge, asOf) > overdueAfterDays)
                        .toList()))
                .companyCount(companyCount(unpaid))
                .build();
        log.debug("Aged {} unpaid charges of {} companies as of {}", unpaid.size(), ageing.getCompanyCount(), asOf);
        return ageing;
    }

    private static long daysPastDue(UnpaidCharge charge, LocalDate asOf) {
        return ChronoUnit.DAYS.between(charge.dueDate(), asOf);
    }

    private static BigDecimal sumUnpaid(List<UnpaidCharge> charges) {
        return charges.stream().map(UnpaidCharge::unpaid).reduce(NO_AMOUNT, BigDecimal::add);
    }

    private static long companyCount(List<UnpaidCharge> charges) {
        return charges.stream().map(UnpaidCharge::companyId).distinct().count();
    }

    /**
     * A range of days past due, both ends inclusive.
     *
     * @param toDays the most days in the range, or null when it is open-ended
     */
    private record AgeingRange(String label, int fromDays, Integer toDays) {

        boolean contains(long days) {
            return days >= fromDays && (toDays == null || days <= toDays);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyTrendResponse monthlyTrend(FinancialYear financialYear) {
        List<MonthlyTrend> months = ledgerService.monthlyTotals(financialYear).stream()
                .map(totals -> MonthlyTrend.builder()
                        .month(totals.month().toString())
                        .billed(totals.billed())
                        .received(totals.received())
                        .discount(totals.discount())
                        .build())
                .toList();
        log.debug("Built the monthly trend for FY {}", financialYear.label());

        return MonthlyTrendResponse.builder()
                .financialYear(financialYear.label())
                .months(months)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BilledByCategoryResponse billedByCategory(FinancialYear financialYear, int topServices) {
        Map<UUID, BigDecimal> billed = ledgerService.billedByCatalogService(financialYear);
        Map<ServiceCategory, List<CategoryService>> servicesByCategory = new EnumMap<>(ServiceCategory.class);
        Map<UUID, CatalogService> catalogServices = billed.isEmpty() ? Map.of()
                : catalogServiceRepository.findAllById(billed.keySet()).stream()
                        .collect(Collectors.toMap(CatalogService::getId, Function.identity()));
        billed.forEach((catalogServiceId, amount) -> {
            CatalogService service = catalogServices.get(catalogServiceId);
            if (service == null) {
                // Charges are held to their catalog service by foreign key, so the data is inconsistent.
                throw new IllegalStateException("Ledger entries refer to catalog service " + catalogServiceId
                        + ", which does not exist");
            }
            servicesByCategory.computeIfAbsent(service.getCategory(), category -> new ArrayList<>())
                    .add(CategoryService.builder()
                            .catalogServiceId(catalogServiceId)
                            .serviceName(service.getServiceName())
                            .billed(amount)
                            .build());
        });

        // Every category is listed; the sort is stable, so those billed the same keep the catalog's order.
        List<CategoryBilling> categories = Arrays.stream(ServiceCategory.values())
                .map(category -> categoryBilling(category, servicesByCategory.getOrDefault(category, List.of()),
                        topServices))
                .sorted(Comparator.comparing(CategoryBilling::getBilled).reversed())
                .toList();
        log.debug("Split FY {} billing across {} categories", financialYear.label(), categories.size());

        return BilledByCategoryResponse.builder()
                .financialYear(financialYear.label())
                .categories(categories)
                .build();
    }

    private static CategoryBilling categoryBilling(ServiceCategory category, List<CategoryService> services,
                                                   int topServices) {
        return CategoryBilling.builder()
                .category(category)
                .billed(services.stream().map(CategoryService::getBilled).reduce(NO_AMOUNT, BigDecimal::add))
                .serviceCount(services.size())
                .topServices(topServices == 0 ? null : services.stream()
                        .sorted(Comparator.comparing(CategoryService::getBilled).reversed()
                                .thenComparing(CategoryService::getServiceName))
                        .limit(topServices)
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TopPendingClientsResponse topPendingClients(FinancialYear financialYear, int limit) {
        List<PendingClient> clients = rankedClients(ledgerService.topClientsByPending(financialYear, limit))
                .stream()
                .map(ranked -> PendingClient.builder()
                        .rank(ranked.rank())
                        .clientId(ranked.client().getId())
                        .clientName(ranked.clientName())
                        .managerId(ranked.client().getManagerId())
                        .managerName(ranked.managerName())
                        .totalBilled(ranked.balance().totalBilled())
                        .totalPaymentReceived(ranked.balance().totalPaymentReceived())
                        .pending(ranked.balance().pending())
                        .build())
                .toList();
        log.debug("Ranked {} clients by pending for FY {}", clients.size(),
                financialYear == null ? "all" : financialYear.label());

        return TopPendingClientsResponse.builder()
                .financialYear(financialYear == null ? null : financialYear.label())
                .limit(limit)
                .clients(clients)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TopBilledClientsResponse topBilledClients(FinancialYear financialYear, int limit) {
        List<BilledClient> clients = rankedClients(ledgerService.topClientsByBilled(financialYear, limit))
                .stream()
                .map(ranked -> BilledClient.builder()
                        .rank(ranked.rank())
                        .clientId(ranked.client().getId())
                        .clientName(ranked.clientName())
                        .managerId(ranked.client().getManagerId())
                        .managerName(ranked.managerName())
                        .totalBilled(ranked.balance().totalBilled())
                        .totalPaymentReceived(ranked.balance().totalPaymentReceived())
                        .pending(ranked.balance().pending())
                        .build())
                .toList();
        log.debug("Ranked {} clients by billed for FY {}", clients.size(),
                financialYear == null ? "all" : financialYear.label());

        return TopBilledClientsResponse.builder()
                .financialYear(financialYear == null ? null : financialYear.label())
                .limit(limit)
                .clients(clients)
                .build();
    }

    /**
     * The ledger's ranking of clients, each with their record and manager, numbered from 1. One
     * query each for the clients and their managers, rather than one per client.
     */
    private List<RankedClient> rankedClients(List<ClientBalance> balances) {
        if (balances.isEmpty()) {
            return List.of();
        }
        Map<UUID, Client> clientsById = clientRepository.findAllById(balances.stream()
                        .map(ClientBalance::clientId).toList()).stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));
        Map<UUID, Manager> managersById = managerRepository.findAllById(clientsById.values().stream()
                        .map(Client::getManagerId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Manager::getId, Function.identity()));

        List<RankedClient> ranked = new ArrayList<>();
        for (ClientBalance balance : balances) {
            Client client = clientsById.get(balance.clientId());
            if (client == null) {
                // Ledger entries are held to their client by foreign key, so this means the data is inconsistent.
                throw new IllegalStateException("Ledger entries refer to client " + balance.clientId()
                        + ", which does not exist");
            }
            ranked.add(new RankedClient(ranked.size() + 1, client, managersById.get(client.getManagerId()),
                    balance));
        }
        return ranked;
    }

    /** A client's place in a ranking, with their manager and what the ranking is of. */
    private record RankedClient(int rank, Client client, Manager manager, ClientBalance balance) {

        String clientName() {
            return client.getFirstName() + " " + client.getLastName();
        }

        /** Clients are held to their manager by foreign key, so a missing one is only ever left unnamed. */
        String managerName() {
            return manager == null ? null : manager.getFirstName() + " " + manager.getLastName();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TopPendingManagersResponse topPendingManagers(FinancialYear financialYear, int limit) {
        List<PendingManager> managers = rankedManagers(ledgerService.topManagersByPending(financialYear, limit))
                .stream()
                .map(ranked -> PendingManager.builder()
                        .rank(ranked.rank())
                        .managerId(ranked.manager().getId())
                        .managerName(ranked.managerName())
                        .clientCount(ranked.clientCount())
                        .totalBilled(ranked.balance().totalBilled())
                        .totalPaymentReceived(ranked.balance().totalPaymentReceived())
                        .pending(ranked.balance().pending())
                        .build())
                .toList();
        log.debug("Ranked {} managers by pending for FY {}", managers.size(),
                financialYear == null ? "all" : financialYear.label());

        return TopPendingManagersResponse.builder()
                .financialYear(financialYear == null ? null : financialYear.label())
                .limit(limit)
                .managers(managers)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TopBilledManagersResponse topBilledManagers(FinancialYear financialYear, int limit) {
        List<BilledManager> managers = rankedManagers(ledgerService.topManagersByBilled(financialYear, limit))
                .stream()
                .map(ranked -> BilledManager.builder()
                        .rank(ranked.rank())
                        .managerId(ranked.manager().getId())
                        .managerName(ranked.managerName())
                        .clientCount(ranked.clientCount())
                        .totalBilled(ranked.balance().totalBilled())
                        .totalPaymentReceived(ranked.balance().totalPaymentReceived())
                        .pending(ranked.balance().pending())
                        .build())
                .toList();
        log.debug("Ranked {} managers by billed for FY {}", managers.size(),
                financialYear == null ? "all" : financialYear.label());

        return TopBilledManagersResponse.builder()
                .financialYear(financialYear == null ? null : financialYear.label())
                .limit(limit)
                .managers(managers)
                .build();
    }

    /**
     * The ledger's ranking of managers, each with their record and client count, numbered from 1.
     * One query each for the managers and every manager's client count, rather than one per manager.
     */
    private List<RankedManager> rankedManagers(List<ManagerBalance> balances) {
        if (balances.isEmpty()) {
            return List.of();
        }
        Map<UUID, Manager> managersById = managerRepository.findAllById(balances.stream()
                        .map(ManagerBalance::managerId).toList()).stream()
                .collect(Collectors.toMap(Manager::getId, Function.identity()));
        Map<UUID, Long> clientCounts = clientRepository.countClientsByManager().stream()
                .collect(Collectors.toMap(ManagerClientCount::getManagerId, ManagerClientCount::getClientCount));

        List<RankedManager> ranked = new ArrayList<>();
        for (ManagerBalance balance : balances) {
            Manager manager = managersById.get(balance.managerId());
            if (manager == null) {
                // Clients are held to their manager by foreign key, so this means the data is inconsistent.
                throw new IllegalStateException("Clients refer to manager " + balance.managerId()
                        + ", who does not exist");
            }
            ranked.add(new RankedManager(ranked.size() + 1, manager,
                    clientCounts.getOrDefault(manager.getId(), 0L), balance));
        }
        return ranked;
    }

    /** A manager's place in a ranking, with what the ranking is of. */
    private record RankedManager(int rank, Manager manager, long clientCount, ManagerBalance balance) {

        String managerName() {
            return manager.getFirstName() + " " + manager.getLastName();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TopPendingCompaniesResponse topPendingCompanies(FinancialYear financialYear, int limit) {
        List<PendingCompany> companies = rankedCompanies(ledgerService.topCompaniesByPending(financialYear, limit))
                .stream()
                .map(ranked -> PendingCompany.builder()
                        .rank(ranked.rank())
                        .companyId(ranked.balance().companyId())
                        .companyName(ranked.companyName())
                        .clientId(ranked.balance().clientId())
                        .clientName(ranked.clientName())
                        .totalBilled(ranked.balance().totalBilled())
                        .totalPaymentReceived(ranked.balance().totalPaymentReceived())
                        .pending(ranked.balance().pending())
                        .build())
                .toList();
        log.debug("Ranked {} companies by pending for FY {}", companies.size(),
                financialYear == null ? "all" : financialYear.label());

        return TopPendingCompaniesResponse.builder()
                .financialYear(financialYear == null ? null : financialYear.label())
                .limit(limit)
                .companies(companies)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TopBilledCompaniesResponse topBilledCompanies(FinancialYear financialYear, int limit) {
        List<BilledCompany> companies = rankedCompanies(ledgerService.topCompaniesByBilled(financialYear, limit))
                .stream()
                .map(ranked -> BilledCompany.builder()
                        .rank(ranked.rank())
                        .companyId(ranked.balance().companyId())
                        .companyName(ranked.companyName())
                        .clientId(ranked.balance().clientId())
                        .clientName(ranked.clientName())
                        .totalBilled(ranked.balance().totalBilled())
                        .totalPaymentReceived(ranked.balance().totalPaymentReceived())
                        .pending(ranked.balance().pending())
                        .build())
                .toList();
        log.debug("Ranked {} companies by billed for FY {}", companies.size(),
                financialYear == null ? "all" : financialYear.label());

        return TopBilledCompaniesResponse.builder()
                .financialYear(financialYear == null ? null : financialYear.label())
                .limit(limit)
                .companies(companies)
                .build();
    }

    /** The ledger's ranking of companies, each with its name and its client's, numbered from 1. */
    private List<RankedCompany> rankedCompanies(List<ClientCompanyBalance> balances) {
        Names names = names(balances.stream().map(ClientCompanyBalance::companyId).toList(),
                balances.stream().map(ClientCompanyBalance::clientId).toList());
        List<RankedCompany> ranked = new ArrayList<>();
        for (ClientCompanyBalance balance : balances) {
            ranked.add(new RankedCompany(ranked.size() + 1, names.company(balance.companyId()),
                    names.client(balance.clientId()), balance));
        }
        return ranked;
    }

    /** A company's place in a ranking, with its and its client's names and what the ranking is of. */
    private record RankedCompany(int rank, String companyName, String clientName, ClientCompanyBalance balance) {
    }

    @Override
    @Transactional(readOnly = true)
    public RecentPaymentsResponse recentPayments(int limit) {
        List<LedgerEntryResponse> entries = ledgerService.recentPayments(limit);
        Names names = names(entries.stream().map(LedgerEntryResponse::getCompanyId).toList(),
                entries.stream().map(LedgerEntryResponse::getClientId).toList());

        List<RecentPayment> payments = entries.stream()
                .map(entry -> RecentPayment.builder()
                        .paymentId(entry.getId())
                        .dateReceived(entry.getDueDate())
                        // Payments are stored negative, so the ledger's totals add up to what is owed.
                        .amount(entry.getTotalAmount().negate())
                        .method(entry.getPaymentMethod())
                        .reference(entry.getPaymentReference())
                        .comment(entry.getComment())
                        .companyId(entry.getCompanyId())
                        .companyName(names.company(entry.getCompanyId()))
                        .clientId(entry.getClientId())
                        .clientName(names.client(entry.getClientId()))
                        .recordedAt(entry.getCreatedAt())
                        .build())
                .toList();
        log.debug("Listed the latest {} payments", payments.size());

        return RecentPaymentsResponse.builder()
                .limit(limit)
                .payments(payments)
                .build();
    }

    /** One query each for the companies and clients named, rather than one per row. */
    private Names names(Collection<UUID> companyIds, Collection<UUID> clientIds) {
        Map<UUID, String> companies = companyIds.isEmpty() ? Map.of()
                : clientCompanyRepository.findAllById(Set.copyOf(companyIds)).stream()
                        .collect(Collectors.toMap(ClientCompany::getId, ClientCompany::getCompanyName));
        Map<UUID, String> clients = clientIds.isEmpty() ? Map.of()
                : clientRepository.findAllById(Set.copyOf(clientIds)).stream()
                        .collect(Collectors.toMap(Client::getId,
                                client -> client.getFirstName() + " " + client.getLastName()));
        return new Names(companies, clients);
    }

    /**
     * Names of the companies and clients ledger rows refer to. Ledger entries are held to both by
     * foreign key, so a missing name means the data is inconsistent and is reported as a server error.
     */
    private record Names(Map<UUID, String> companies, Map<UUID, String> clients) {

        String company(UUID id) {
            return require(companies, id, "company");
        }

        String client(UUID id) {
            return require(clients, id, "client");
        }

        private static String require(Map<UUID, String> names, UUID id, String kind) {
            String name = names.get(id);
            if (name == null) {
                throw new IllegalStateException("Ledger entries refer to " + kind + " " + id
                        + ", which does not exist");
            }
            return name;
        }
    }
}
