package com.psc.cl.overview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.psc.cl.ledger.dto.MonthlyTotals;
import com.psc.cl.ledger.dto.UnpaidCharge;
import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.ledger.model.LedgerEntryType;
import com.psc.cl.ledger.model.PaymentMethod;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class OverviewServiceImplTest {

    /** 14 October 2026 in India. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T20:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private static final UUID MANAGER_ID = UUID.fromString("e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47");

    @Mock
    private LedgerService ledgerService;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientCompanyRepository clientCompanyRepository;

    @Mock
    private ServiceAssignmentRepository serviceAssignmentRepository;

    @Mock
    private ManagerRepository managerRepository;

    @Mock
    private CatalogServiceRepository catalogServiceRepository;

    @Mock
    private PaymentTermsRepository paymentTermsRepository;

    private OverviewServiceImpl overviewService;

    @BeforeEach
    void setUp() {
        overviewService = new OverviewServiceImpl(ledgerService, clientRepository, clientCompanyRepository,
                serviceAssignmentRepository, managerRepository, catalogServiceRepository, paymentTermsRepository,
                CLOCK);
    }

    @Test
    @DisplayName("gives the year's billed, received, pending and discount across clients, with the counts")
    void givesOverview() {
        FinancialYear year = FinancialYears.parse("2026-27");
        when(ledgerService.totalsForYear(year)).thenReturn(LedgerSummary.builder()
                .totalAmount(new BigDecimal("1845000.00"))
                .totalPaid(new BigDecimal("1571500.00"))
                .balance(new BigDecimal("273500.00"))
                .totalDiscountAmount(new BigDecimal("63500.00"))
                .build());
        when(clientRepository.count()).thenReturn(42L);
        when(clientCompanyRepository.count()).thenReturn(97L);
        when(serviceAssignmentRepository.count()).thenReturn(268L);
        when(ledgerService.totalsBetween(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 10, 14)))
                .thenReturn(LedgerSummary.builder()
                        .totalAmount(new BigDecimal("1647000.00"))
                        .totalPaid(new BigDecimal("1402000.00"))
                        .balance(new BigDecimal("245000.00"))
                        .totalDiscountAmount(new BigDecimal("58000.00"))
                        .build());

        OverviewResponse overview = overviewService.overview(year);

        assertThat(overview.getFinancialYear()).isEqualTo("2026-27");
        assertThat(overview.getFromDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(overview.getToDate()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(overview.getTotalBilled()).isEqualTo(new BigDecimal("1845000.00"));
        assertThat(overview.getTotalPaymentReceived()).isEqualTo(new BigDecimal("1571500.00"));
        assertThat(overview.getPending()).isEqualTo(new BigDecimal("273500.00"));
        assertThat(overview.getTotalDiscount()).isEqualTo(new BigDecimal("63500.00"));
        assertThat(overview.getTotalClients()).isEqualTo(42L);
        assertThat(overview.getTotalCompanies()).isEqualTo(97L);
        assertThat(overview.getTotalServices()).isEqualTo(268L);
        PeriodTotals previous = overview.getPreviousToDate();
        assertThat(previous.getFinancialYear()).isEqualTo("2025-26");
        assertThat(previous.getFromDate()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(previous.getToDate()).isEqualTo(LocalDate.of(2025, 10, 14));
        assertThat(previous.getTotalBilled()).isEqualTo(new BigDecimal("1647000.00"));
        assertThat(previous.getTotalPaymentReceived()).isEqualTo(new BigDecimal("1402000.00"));
        assertThat(previous.getPending()).isEqualTo(new BigDecimal("245000.00"));
        assertThat(previous.getTotalDiscount()).isEqualTo(new BigDecimal("58000.00"));
    }

    @Test
    @DisplayName("compares a year that is over with the whole of the year before")
    void comparesFinishedYearWithWholePreviousYear() {
        FinancialYear year = FinancialYears.parse("2025-26");
        when(ledgerService.totalsForYear(year)).thenReturn(emptySummary());
        when(ledgerService.totalsBetween(LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 31)))
                .thenReturn(emptySummary());

        OverviewResponse overview = overviewService.overview(year);

        assertThat(overview.getPreviousToDate().getFinancialYear()).isEqualTo("2024-25");
        assertThat(overview.getPreviousToDate().getToDate()).isEqualTo(LocalDate.of(2025, 3, 31));
    }

    @Test
    @DisplayName("ages what is owed into ranges of days past due, with what is overdue under the payment terms")
    void agesReceivables() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(paymentTermsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(
                PaymentTerms.builder().markOverdueAfterDays(45).build()));
        when(ledgerService.unpaidCharges(TODAY)).thenReturn(List.of(
                new UnpaidCharge(first, TODAY.minusDays(120), new BigDecimal("1000.00")),
                new UnpaidCharge(first, TODAY.minusDays(50), new BigDecimal("2000.00")),
                new UnpaidCharge(second, TODAY.minusDays(31), new BigDecimal("500.00")),
                new UnpaidCharge(second, TODAY.minusDays(30), new BigDecimal("300.00")),
                new UnpaidCharge(first, TODAY, new BigDecimal("200.00"))));

        AgeingResponse ageing = overviewService.ageing();

        assertThat(ageing.getAsOf()).isEqualTo(TODAY);
        assertThat(ageing.getOverdueAfterDays()).isEqualTo(45);
        assertThat(ageing.getBuckets()).extracting(AgeingBucket::getLabel)
                .containsExactly("0-30", "31-60", "61-90", "90+");
        assertThat(ageing.getBuckets()).extracting(AgeingBucket::getAmount).containsExactly(
                new BigDecimal("500.00"), new BigDecimal("2500.00"), new BigDecimal("0.00"),
                new BigDecimal("1000.00"));
        assertThat(ageing.getBuckets()).extracting(AgeingBucket::getCompanyCount).containsExactly(2L, 2L, 0L, 1L);
        assertThat(ageing.getBuckets().getLast().getToDays()).isNull();
        assertThat(ageing.getTotal()).isEqualTo(new BigDecimal("4000.00"));
        assertThat(ageing.getOverdue()).isEqualTo(new BigDecimal("3000.00"));
        assertThat(ageing.getCompanyCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("ages with the default overdue days when no payment terms are saved")
    void agesWithDefaultOverdueDays() {
        when(paymentTermsRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(ledgerService.unpaidCharges(TODAY)).thenReturn(List.of());

        AgeingResponse ageing = overviewService.ageing();

        assertThat(ageing.getOverdueAfterDays()).isEqualTo(PaymentTerms.DEFAULT_MARK_OVERDUE_AFTER_DAYS);
        assertThat(ageing.getTotal()).isEqualTo(new BigDecimal("0.00"));
        assertThat(ageing.getBuckets()).allSatisfy(bucket -> assertThat(bucket.getCompanyCount()).isZero());
    }

    @Test
    @DisplayName("gives the ledger's monthly totals, each month written as yyyy-MM")
    void givesMonthlyTrend() {
        FinancialYear year = FinancialYears.parse("2026-27");
        when(ledgerService.monthlyTotals(year)).thenReturn(List.of(
                new MonthlyTotals(YearMonth.of(2026, 4), new BigDecimal("11800.00"), new BigDecimal("5000.00"),
                        new BigDecimal("1000.00"))));

        MonthlyTrendResponse trend = overviewService.monthlyTrend(year);

        assertThat(trend.getFinancialYear()).isEqualTo("2026-27");
        MonthlyTrend april = trend.getMonths().getFirst();
        assertThat(april.getMonth()).isEqualTo("2026-04");
        assertThat(april.getBilled()).isEqualTo(new BigDecimal("11800.00"));
        assertThat(april.getReceived()).isEqualTo(new BigDecimal("5000.00"));
        assertThat(april.getDiscount()).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("splits the year's billing across every category, most first, with their top services")
    void splitsBillingByCategory() {
        FinancialYear year = FinancialYears.parse("2026-27");
        CatalogService gst = catalogService("GST Return Filing", ServiceCategory.COMPLIANCE);
        CatalogService roc = catalogService("ROC Filing", ServiceCategory.COMPLIANCE);
        CatalogService itr = catalogService("ITR Filing", ServiceCategory.TAX);
        when(ledgerService.billedByCatalogService(year)).thenReturn(Map.of(
                gst.getId(), new BigDecimal("8000.00"),
                roc.getId(), new BigDecimal("3000.00"),
                itr.getId(), new BigDecimal("5000.00")));
        when(catalogServiceRepository.findAllById(any())).thenReturn(List.of(gst, roc, itr));

        BilledByCategoryResponse response = overviewService.billedByCategory(year, 1);

        assertThat(response.getFinancialYear()).isEqualTo("2026-27");
        assertThat(response.getCategories()).extracting(CategoryBilling::getCategory).containsExactly(
                ServiceCategory.COMPLIANCE, ServiceCategory.TAX, ServiceCategory.AUDIT,
                ServiceCategory.ACCOUNTING, ServiceCategory.ADVISORY);
        CategoryBilling compliance = response.getCategories().getFirst();
        assertThat(compliance.getBilled()).isEqualTo(new BigDecimal("11000.00"));
        assertThat(compliance.getServiceCount()).isEqualTo(2L);
        assertThat(compliance.getTopServices()).extracting(CategoryService::getServiceName)
                .containsExactly("GST Return Filing");
        assertThat(response.getCategories().getLast().getBilled()).isEqualTo(new BigDecimal("0.00"));
        assertThat(response.getCategories().getLast().getTopServices()).isEmpty();
    }

    @Test
    @DisplayName("leaves out the top services when none are asked for")
    void splitsBillingByCategoryWithoutServices() {
        FinancialYear year = FinancialYears.parse("2026-27");
        when(ledgerService.billedByCatalogService(year)).thenReturn(Map.of());

        BilledByCategoryResponse response = overviewService.billedByCategory(year, 0);

        assertThat(response.getCategories()).hasSize(ServiceCategory.values().length)
                .allSatisfy(category -> assertThat(category.getTopServices()).isNull());
        verify(catalogServiceRepository, never()).findAllById(any());
    }

    private static LedgerSummary emptySummary() {
        BigDecimal zero = new BigDecimal("0.00");
        return LedgerSummary.builder().totalAmount(zero).totalPaid(zero).balance(zero).totalDiscountAmount(zero)
                .build();
    }

    private static CatalogService catalogService(String name, ServiceCategory category) {
        CatalogService service = new CatalogService();
        service.setId(UUID.randomUUID());
        service.setServiceName(name);
        service.setCategory(category);
        return service;
    }

    @Test
    @DisplayName("ranks the clients owing most in the ledger's order, naming each and their manager")
    void ranksTopPendingClients() {
        FinancialYear year = FinancialYears.parse("2026-27");
        Client rohan = client("Rohan", "Mehta");
        Client asha = client("Asha", "Kapoor");
        when(ledgerService.topClientsByPending(year, 10)).thenReturn(List.of(
                new ClientBalance(rohan.getId(), new BigDecimal("39235.00"), new BigDecimal("18605.00"),
                        new BigDecimal("20630.00")),
                new ClientBalance(asha.getId(), new BigDecimal("5605.00"), new BigDecimal("0.00"),
                        new BigDecimal("5605.00"))));
        when(clientRepository.findAllById(List.of(rohan.getId(), asha.getId()))).thenReturn(List.of(asha, rohan));
        when(managerRepository.findAllById(Set.of(MANAGER_ID))).thenReturn(List.of(manager()));

        TopPendingClientsResponse top = overviewService.topPendingClients(year, 10);

        assertThat(top.getFinancialYear()).isEqualTo("2026-27");
        assertThat(top.getLimit()).isEqualTo(10);
        assertThat(top.getClients()).extracting(PendingClient::getRank).containsExactly(1, 2);
        assertThat(top.getClients()).extracting(PendingClient::getClientName)
                .containsExactly("Rohan Mehta", "Asha Kapoor");
        PendingClient first = top.getClients().getFirst();
        assertThat(first.getClientId()).isEqualTo(rohan.getId());
        assertThat(first.getManagerId()).isEqualTo(MANAGER_ID);
        assertThat(first.getManagerName()).isEqualTo("Pranay Singhal");
        assertThat(first.getTotalBilled()).isEqualTo(new BigDecimal("39235.00"));
        assertThat(first.getTotalPaymentReceived()).isEqualTo(new BigDecimal("18605.00"));
        assertThat(first.getPending()).isEqualTo(new BigDecimal("20630.00"));
    }

    @Test
    @DisplayName("ranks across every year when no financial year is given, and returns no clients when none owe")
    void ranksNobodyAcrossEveryYear() {
        when(ledgerService.topClientsByPending(null, 5)).thenReturn(List.of());

        TopPendingClientsResponse top = overviewService.topPendingClients(null, 5);

        assertThat(top.getFinancialYear()).isNull();
        assertThat(top.getClients()).isEmpty();
    }

    @Test
    @DisplayName("ranks the managers whose clients owe most in the ledger's order, with their client counts")
    void ranksTopPendingManagers() {
        FinancialYear year = FinancialYears.parse("2026-27");
        UUID otherId = UUID.fromString("1f4a8c62-3b7d-4e19-a5c0-8d2e6f9b3a71");
        Manager other = manager();
        other.setId(otherId);
        other.setFirstName("Bhavna");
        other.setLastName("Desai");
        when(ledgerService.topManagersByPending(year, 10)).thenReturn(List.of(
                new ManagerBalance(MANAGER_ID, new BigDecimal("39235.00"), new BigDecimal("18605.00"),
                        new BigDecimal("20630.00")),
                new ManagerBalance(otherId, new BigDecimal("5605.00"), new BigDecimal("0.00"),
                        new BigDecimal("5605.00"))));
        when(managerRepository.findAllById(List.of(MANAGER_ID, otherId))).thenReturn(List.of(other, manager()));
        when(clientRepository.countClientsByManager()).thenReturn(List.of(clientCount(MANAGER_ID, 3)));

        TopPendingManagersResponse top = overviewService.topPendingManagers(year, 10);

        assertThat(top.getFinancialYear()).isEqualTo("2026-27");
        assertThat(top.getLimit()).isEqualTo(10);
        assertThat(top.getManagers()).extracting(PendingManager::getRank).containsExactly(1, 2);
        assertThat(top.getManagers()).extracting(PendingManager::getManagerName)
                .containsExactly("Pranay Singhal", "Bhavna Desai");
        PendingManager first = top.getManagers().getFirst();
        assertThat(first.getManagerId()).isEqualTo(MANAGER_ID);
        assertThat(first.getClientCount()).isEqualTo(3L);
        assertThat(first.getTotalBilled()).isEqualTo(new BigDecimal("39235.00"));
        assertThat(first.getTotalPaymentReceived()).isEqualTo(new BigDecimal("18605.00"));
        assertThat(first.getPending()).isEqualTo(new BigDecimal("20630.00"));
        // A count the grouped query did not return is zero.
        assertThat(top.getManagers().get(1).getClientCount()).isZero();
    }

    @Test
    @DisplayName("returns no managers, without counting clients, when nobody owes anything")
    void ranksNoManagers() {
        when(ledgerService.topManagersByPending(null, 5)).thenReturn(List.of());

        TopPendingManagersResponse top = overviewService.topPendingManagers(null, 5);

        assertThat(top.getFinancialYear()).isNull();
        assertThat(top.getManagers()).isEmpty();
        verify(clientRepository, never()).countClientsByManager();
    }

    @Test
    @DisplayName("ranks the clients billed most in the ledger's order, naming each and their manager")
    void ranksTopBilledClients() {
        FinancialYear year = FinancialYears.parse("2026-27");
        Client rohan = client("Rohan", "Mehta");
        Client asha = client("Asha", "Kapoor");
        // Asha was billed less and has paid more than billed, so her pending is negative.
        when(ledgerService.topClientsByBilled(year, 10)).thenReturn(List.of(
                new ClientBalance(rohan.getId(), new BigDecimal("39235.00"), new BigDecimal("18605.00"),
                        new BigDecimal("20630.00")),
                new ClientBalance(asha.getId(), new BigDecimal("5605.00"), new BigDecimal("6000.00"),
                        new BigDecimal("-395.00"))));
        when(clientRepository.findAllById(List.of(rohan.getId(), asha.getId()))).thenReturn(List.of(asha, rohan));
        when(managerRepository.findAllById(Set.of(MANAGER_ID))).thenReturn(List.of(manager()));

        TopBilledClientsResponse top = overviewService.topBilledClients(year, 10);

        assertThat(top.getFinancialYear()).isEqualTo("2026-27");
        assertThat(top.getLimit()).isEqualTo(10);
        assertThat(top.getClients()).extracting(BilledClient::getRank).containsExactly(1, 2);
        assertThat(top.getClients()).extracting(BilledClient::getClientName)
                .containsExactly("Rohan Mehta", "Asha Kapoor");
        assertThat(top.getClients()).extracting(BilledClient::getTotalBilled)
                .containsExactly(new BigDecimal("39235.00"), new BigDecimal("5605.00"));
        assertThat(top.getClients()).allSatisfy(client -> {
            assertThat(client.getManagerId()).isEqualTo(MANAGER_ID);
            assertThat(client.getManagerName()).isEqualTo("Pranay Singhal");
        });
        BilledClient second = top.getClients().get(1);
        assertThat(second.getTotalPaymentReceived()).isEqualTo(new BigDecimal("6000.00"));
        assertThat(second.getPending()).isEqualTo(new BigDecimal("-395.00"));
    }

    @Test
    @DisplayName("returns no clients by billed, without looking anything up, when nothing was billed")
    void ranksNoBilledClients() {
        when(ledgerService.topClientsByBilled(null, 5)).thenReturn(List.of());

        TopBilledClientsResponse top = overviewService.topBilledClients(null, 5);

        assertThat(top.getFinancialYear()).isNull();
        assertThat(top.getClients()).isEmpty();
        verify(clientRepository, never()).findAllById(any());
        verify(managerRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("ranks the managers whose clients were billed most, in the ledger's order, with their counts")
    void ranksTopBilledManagers() {
        FinancialYear year = FinancialYears.parse("2026-27");
        UUID otherId = UUID.fromString("1f4a8c62-3b7d-4e19-a5c0-8d2e6f9b3a71");
        Manager other = manager();
        other.setId(otherId);
        other.setFirstName("Bhavna");
        other.setLastName("Desai");
        // Bhavna's clients were billed less but have paid in full, so pending is zero or below.
        when(ledgerService.topManagersByBilled(year, 10)).thenReturn(List.of(
                new ManagerBalance(MANAGER_ID, new BigDecimal("39235.00"), new BigDecimal("18605.00"),
                        new BigDecimal("20630.00")),
                new ManagerBalance(otherId, new BigDecimal("5605.00"), new BigDecimal("6000.00"),
                        new BigDecimal("-395.00"))));
        when(managerRepository.findAllById(List.of(MANAGER_ID, otherId))).thenReturn(List.of(other, manager()));
        when(clientRepository.countClientsByManager())
                .thenReturn(List.of(clientCount(MANAGER_ID, 3), clientCount(otherId, 1)));

        TopBilledManagersResponse top = overviewService.topBilledManagers(year, 10);

        assertThat(top.getFinancialYear()).isEqualTo("2026-27");
        assertThat(top.getLimit()).isEqualTo(10);
        assertThat(top.getManagers()).extracting(BilledManager::getRank).containsExactly(1, 2);
        assertThat(top.getManagers()).extracting(BilledManager::getManagerName)
                .containsExactly("Pranay Singhal", "Bhavna Desai");
        assertThat(top.getManagers()).extracting(BilledManager::getClientCount).containsExactly(3L, 1L);
        assertThat(top.getManagers()).extracting(BilledManager::getTotalBilled)
                .containsExactly(new BigDecimal("39235.00"), new BigDecimal("5605.00"));
        BilledManager second = top.getManagers().get(1);
        assertThat(second.getTotalPaymentReceived()).isEqualTo(new BigDecimal("6000.00"));
        assertThat(second.getPending()).isEqualTo(new BigDecimal("-395.00"));
    }

    @Test
    @DisplayName("returns no managers by billed, without looking anything up, when nothing was billed")
    void ranksNoBilledManagers() {
        when(ledgerService.topManagersByBilled(null, 5)).thenReturn(List.of());

        TopBilledManagersResponse top = overviewService.topBilledManagers(null, 5);

        assertThat(top.getFinancialYear()).isNull();
        assertThat(top.getManagers()).isEmpty();
        verify(managerRepository, never()).findAllById(any());
        verify(clientRepository, never()).countClientsByManager();
    }

    @Test
    @DisplayName("ranks the companies owing most in the ledger's order, naming each and its client")
    void ranksTopPendingCompanies() {
        FinancialYear year = FinancialYears.parse("2026-27");
        Client rohan = client("Rohan", "Mehta");
        ClientCompany huf = company("Rohan Mehta HUF");
        ClientCompany textiles = company("Mehta Textiles Pvt Ltd");
        when(ledgerService.topCompaniesByPending(year, 10)).thenReturn(List.of(
                new ClientCompanyBalance(huf.getId(), rohan.getId(), new BigDecimal("28025.00"),
                        new BigDecimal("10000.00"), new BigDecimal("18025.00")),
                new ClientCompanyBalance(textiles.getId(), rohan.getId(), new BigDecimal("11210.00"),
                        new BigDecimal("8605.00"), new BigDecimal("2605.00"))));
        when(clientCompanyRepository.findAllById(Set.of(huf.getId(), textiles.getId())))
                .thenReturn(List.of(textiles, huf));
        when(clientRepository.findAllById(Set.of(rohan.getId()))).thenReturn(List.of(rohan));

        TopPendingCompaniesResponse top = overviewService.topPendingCompanies(year, 10);

        assertThat(top.getFinancialYear()).isEqualTo("2026-27");
        assertThat(top.getLimit()).isEqualTo(10);
        assertThat(top.getCompanies()).extracting(PendingCompany::getRank).containsExactly(1, 2);
        assertThat(top.getCompanies()).extracting(PendingCompany::getCompanyName)
                .containsExactly("Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        PendingCompany first = top.getCompanies().getFirst();
        assertThat(first.getClientId()).isEqualTo(rohan.getId());
        assertThat(first.getClientName()).isEqualTo("Rohan Mehta");
        assertThat(first.getTotalBilled()).isEqualTo(new BigDecimal("28025.00"));
        assertThat(first.getTotalPaymentReceived()).isEqualTo(new BigDecimal("10000.00"));
        assertThat(first.getPending()).isEqualTo(new BigDecimal("18025.00"));
    }

    @Test
    @DisplayName("returns no companies, without looking up names, when nobody owes anything")
    void ranksNoCompanies() {
        when(ledgerService.topCompaniesByPending(null, 5)).thenReturn(List.of());

        TopPendingCompaniesResponse top = overviewService.topPendingCompanies(null, 5);

        assertThat(top.getFinancialYear()).isNull();
        assertThat(top.getCompanies()).isEmpty();
        verify(clientCompanyRepository, never()).findAllById(any());
        verify(clientRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("ranks the companies billed most in the ledger's order, naming each and its client")
    void ranksTopBilledCompanies() {
        FinancialYear year = FinancialYears.parse("2026-27");
        Client rohan = client("Rohan", "Mehta");
        ClientCompany huf = company("Rohan Mehta HUF");
        ClientCompany textiles = company("Mehta Textiles Pvt Ltd");
        // Mehta Textiles was billed less and has overpaid, so its pending is negative.
        when(ledgerService.topCompaniesByBilled(year, 10)).thenReturn(List.of(
                new ClientCompanyBalance(huf.getId(), rohan.getId(), new BigDecimal("28025.00"),
                        new BigDecimal("10000.00"), new BigDecimal("18025.00")),
                new ClientCompanyBalance(textiles.getId(), rohan.getId(), new BigDecimal("11210.00"),
                        new BigDecimal("12000.00"), new BigDecimal("-790.00"))));
        when(clientCompanyRepository.findAllById(Set.of(huf.getId(), textiles.getId())))
                .thenReturn(List.of(textiles, huf));
        when(clientRepository.findAllById(Set.of(rohan.getId()))).thenReturn(List.of(rohan));

        TopBilledCompaniesResponse top = overviewService.topBilledCompanies(year, 10);

        assertThat(top.getFinancialYear()).isEqualTo("2026-27");
        assertThat(top.getLimit()).isEqualTo(10);
        assertThat(top.getCompanies()).extracting(BilledCompany::getRank).containsExactly(1, 2);
        assertThat(top.getCompanies()).extracting(BilledCompany::getCompanyName)
                .containsExactly("Rohan Mehta HUF", "Mehta Textiles Pvt Ltd");
        assertThat(top.getCompanies()).extracting(BilledCompany::getTotalBilled)
                .containsExactly(new BigDecimal("28025.00"), new BigDecimal("11210.00"));
        assertThat(top.getCompanies()).allSatisfy(company -> {
            assertThat(company.getClientId()).isEqualTo(rohan.getId());
            assertThat(company.getClientName()).isEqualTo("Rohan Mehta");
        });
        BilledCompany second = top.getCompanies().get(1);
        assertThat(second.getTotalPaymentReceived()).isEqualTo(new BigDecimal("12000.00"));
        assertThat(second.getPending()).isEqualTo(new BigDecimal("-790.00"));
    }

    @Test
    @DisplayName("returns no companies by billed, without looking up names, when nothing was billed")
    void ranksNoBilledCompanies() {
        when(ledgerService.topCompaniesByBilled(null, 5)).thenReturn(List.of());

        TopBilledCompaniesResponse top = overviewService.topBilledCompanies(null, 5);

        assertThat(top.getFinancialYear()).isNull();
        assertThat(top.getCompanies()).isEmpty();
        verify(clientCompanyRepository, never()).findAllById(any());
        verify(clientRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("lists the latest payments in the ledger's order, amounts positive, with who paid")
    void listsRecentPayments() {
        Client rohan = client("Rohan", "Mehta");
        ClientCompany huf = company("Rohan Mehta HUF");
        LedgerEntryResponse cheque = LedgerEntryResponse.builder()
                .id(UUID.randomUUID())
                .clientId(rohan.getId())
                .companyId(huf.getId())
                .entryType(LedgerEntryType.PAYMENT)
                .dueDate(LocalDate.of(2026, 10, 10))
                .totalAmount(new BigDecimal("-10000.00"))
                .paymentMethod(PaymentMethod.CHEQUE)
                .paymentReference("CHQ 004512")
                .comment("Part payment")
                .createdAt(Instant.parse("2026-10-10T09:30:00Z"))
                .build();
        when(ledgerService.recentPayments(20)).thenReturn(List.of(cheque));
        when(clientCompanyRepository.findAllById(Set.of(huf.getId()))).thenReturn(List.of(huf));
        when(clientRepository.findAllById(Set.of(rohan.getId()))).thenReturn(List.of(rohan));

        RecentPaymentsResponse recent = overviewService.recentPayments(20);

        assertThat(recent.getLimit()).isEqualTo(20);
        assertThat(recent.getPayments()).hasSize(1);
        RecentPayment payment = recent.getPayments().getFirst();
        assertThat(payment.getPaymentId()).isEqualTo(cheque.getId());
        assertThat(payment.getDateReceived()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(payment.getAmount()).isEqualTo(new BigDecimal("10000.00"));
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CHEQUE);
        assertThat(payment.getReference()).isEqualTo("CHQ 004512");
        assertThat(payment.getComment()).isEqualTo("Part payment");
        assertThat(payment.getCompanyName()).isEqualTo("Rohan Mehta HUF");
        assertThat(payment.getClientName()).isEqualTo("Rohan Mehta");
        assertThat(payment.getRecordedAt()).isEqualTo(Instant.parse("2026-10-10T09:30:00Z"));
    }

    @Test
    @DisplayName("lists no payments when nothing has been received")
    void listsNoRecentPayments() {
        when(ledgerService.recentPayments(20)).thenReturn(List.of());

        assertThat(overviewService.recentPayments(20).getPayments()).isEmpty();
    }

    private static ClientCompany company(String name) {
        ClientCompany company = new ClientCompany();
        company.setId(UUID.randomUUID());
        company.setCompanyName(name);
        return company;
    }

    private static ManagerClientCount clientCount(UUID managerId, long count) {
        return new ManagerClientCount() {
            @Override
            public UUID getManagerId() {
                return managerId;
            }

            @Override
            public long getClientCount() {
                return count;
            }
        };
    }

    private static Client client(String firstName, String lastName) {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setFirstName(firstName);
        client.setLastName(lastName);
        client.setManagerId(MANAGER_ID);
        return client;
    }

    private static Manager manager() {
        Manager manager = new Manager();
        manager.setId(MANAGER_ID);
        manager.setFirstName("Pranay");
        manager.setLastName("Singhal");
        return manager;
    }
}
