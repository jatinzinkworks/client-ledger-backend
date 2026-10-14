package com.psc.cl.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.ServiceAssignment;
import com.psc.cl.client.repository.ClientCompanyRepository;
import com.psc.cl.client.repository.ClientRepository;
import com.psc.cl.client.repository.ServiceAssignmentRepository;
import com.psc.cl.ledger.dto.ClientBalance;
import com.psc.cl.ledger.dto.ClientCompanyBalance;
import com.psc.cl.ledger.dto.ClientLedgerStatusResponse;
import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyBalance;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyYearStatus;
import com.psc.cl.ledger.dto.FinancialYearStatus;
import com.psc.cl.ledger.dto.LedgerEntryResponse;
import com.psc.cl.ledger.dto.LedgerEntryUpdateRequest;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.ledger.dto.LedgerListQuery;
import com.psc.cl.ledger.dto.LedgerListResponse;
import com.psc.cl.ledger.dto.LedgerSortField;
import com.psc.cl.ledger.dto.LedgerSummary;
import com.psc.cl.ledger.dto.LedgerYearStatus;
import com.psc.cl.ledger.dto.ManagerBalance;
import com.psc.cl.ledger.dto.MonthlyTotals;
import com.psc.cl.ledger.dto.PaymentListResponse;
import com.psc.cl.ledger.dto.PaymentRequest;
import com.psc.cl.ledger.dto.UnpaidCharge;
import com.psc.cl.ledger.exception.FuturePaymentDateException;
import com.psc.cl.ledger.exception.LedgerEntryNotFoundException;
import com.psc.cl.ledger.exception.NotAPaymentException;
import com.psc.cl.ledger.exception.PaymentNotCorrectableException;
import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.ledger.model.LedgerEntry;
import com.psc.cl.ledger.model.LedgerEntryType;
import com.psc.cl.ledger.model.PaymentMethod;
import com.psc.cl.ledger.repository.CatalogServiceBilledRow;
import com.psc.cl.ledger.repository.ClientBalanceRow;
import com.psc.cl.ledger.repository.ClientPendingRow;
import com.psc.cl.ledger.repository.CompanyBalanceRow;
import com.psc.cl.ledger.repository.CompanyChargeRow;
import com.psc.cl.ledger.repository.CompanyPaidRow;
import com.psc.cl.ledger.repository.DailyTotalsRow;
import com.psc.cl.ledger.repository.LedgerEntryRepository;
import com.psc.cl.ledger.repository.ManagerBalanceRow;
import com.psc.cl.ledger.repository.ManagerPendingRow;
import com.psc.cl.ledger.repository.PostedPeriod;
import com.psc.cl.ledger.repository.ServiceBilledRow;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.repository.CatalogServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTest {

    private static final UUID CLIENT_ID = UUID.fromString("7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35");

    private static final UUID COMPANY_ID = UUID.fromString("4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28");

    private static final UUID MONTHLY_ID = UUID.fromString("3b8e5f21-9c4d-4a17-8e62-1f0d7a9c5b34");

    private static final UUID ONE_OFF_ID = UUID.fromString("8f2d6a14-7e3b-4c95-a1d8-5b0e9c7f3a26");

    /** Still 13 October in UTC, but 01:30 on 14 October in India, whose calendar decides "today". */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T20:00:00Z"),
            ZoneId.of("Asia/Kolkata"));

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private static final Sort EARLIEST_DUE_FIRST = Sort.by("dueDate", "serviceName", "createdAt", "id");

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientCompanyRepository clientCompanyRepository;

    @Mock
    private ServiceAssignmentRepository serviceAssignmentRepository;

    @Mock
    private CatalogServiceRepository catalogServiceRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private LedgerServiceImpl ledgerService;

    private Client client;

    private ClientCompany company;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerServiceImpl(clientRepository, clientCompanyRepository,
                serviceAssignmentRepository, catalogServiceRepository, ledgerEntryRepository, CLOCK);

        client = new Client();
        client.setId(CLIENT_ID);
        company = new ClientCompany();
        company.setId(COMPANY_ID);
        company.setCompanyName("Mehta Textiles Pvt Ltd");
        client.addCompany(company);
    }

    @Test
    @DisplayName("bills a recurring service for every date it fell due and moves it past today")
    void billsEveryMissedDateOfRecurringService() {
        stubCompany();
        ServiceAssignment assignment = assignment(MONTHLY_ID, BillingFrequency.MONTHLY, LocalDate.of(2026, 8, 7));
        stubDue(List.of(assignment), monthlyService());
        stubSave();

        List<LedgerEntryResponse> entries = ledgerService.postDueServices(CLIENT_ID, COMPANY_ID);

        assertThat(entries).extracting(LedgerEntryResponse::getDueDate).containsExactly(
                LocalDate.of(2026, 8, 7), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 7));
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.getClientId()).isEqualTo(CLIENT_ID);
            assertThat(entry.getCompanyId()).isEqualTo(COMPANY_ID);
            assertThat(entry.getCompanyServiceId()).isEqualTo(assignment.getId());
            assertThat(entry.getServiceId()).isEqualTo(MONTHLY_ID);
            assertThat(entry.getServiceName()).isEqualTo("GST Monthly Return Filing");
            assertThat(entry.getServiceType()).isEqualTo(BillingFrequency.MONTHLY);
            assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.SERVICE_CHARGE);
            assertThat(entry.getFinancialYear()).isEqualTo("2026-27");
            assertThat(entry.getEntryDate()).isEqualTo(TODAY);
            assertThat(entry.getServiceFee()).isEqualByComparingTo("5000.00");
            assertThat(entry.getDiscountPercent()).isEqualByComparingTo("5.00");
            assertThat(entry.getDiscountAmount()).isEqualByComparingTo("250.00");
            assertThat(entry.getTaxableAmount()).isEqualByComparingTo("4750.00");
            assertThat(entry.getGstRatePercent()).isEqualByComparingTo("18.00");
            assertThat(entry.getGstAmount()).isEqualByComparingTo("855.00");
            assertThat(entry.getTotalAmount()).isEqualByComparingTo("5605.00");
        });
        assertThat(assignment.getNextDueDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(assignment.isLedgerPosted()).isFalse();
    }

    @Test
    @DisplayName("bills a service due today, and moves it to its next due date")
    void billsServiceDueToday() {
        stubCompany();
        ServiceAssignment assignment = assignment(MONTHLY_ID, BillingFrequency.MONTHLY, TODAY);
        CatalogService service = monthlyService();
        service.setInvoiceDayOfMonth(14);
        stubDue(List.of(assignment), service);
        stubSave();

        List<LedgerEntryResponse> entries = ledgerService.postDueServices(CLIENT_ID, COMPANY_ID);

        assertThat(entries).extracting(LedgerEntryResponse::getDueDate).containsExactly(TODAY);
        assertThat(assignment.getNextDueDate()).isEqualTo(LocalDate.of(2026, 11, 14));
    }

    @Test
    @DisplayName("bills a ONE_OFF service once and marks it as posted, leaving its due date alone")
    void billsOneOffServiceOnce() {
        stubCompany();
        LocalDate deliveryDate = LocalDate.of(2026, 10, 1);
        ServiceAssignment assignment = assignment(ONE_OFF_ID, BillingFrequency.ONE_OFF, deliveryDate);
        stubDue(List.of(assignment), oneOffService());
        stubSave();

        List<LedgerEntryResponse> entries = ledgerService.postDueServices(CLIENT_ID, COMPANY_ID);

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.getDueDate()).isEqualTo(deliveryDate);
            assertThat(entry.getServiceType()).isEqualTo(BillingFrequency.ONE_OFF);
            assertThat(entry.getServiceFee()).isEqualByComparingTo("25000.00");
            assertThat(entry.getDiscountAmount()).isEqualByComparingTo("1250.00");
            assertThat(entry.getTaxableAmount()).isEqualByComparingTo("23750.00");
            assertThat(entry.getGstAmount()).isEqualByComparingTo("4275.00");
            assertThat(entry.getTotalAmount()).isEqualByComparingTo("28025.00");
        });
        assertThat(assignment.isLedgerPosted()).isTrue();
        assertThat(assignment.getNextDueDate()).isEqualTo(deliveryDate);
    }

    @Test
    @DisplayName("skips a due date already billed")
    void skipsDateAlreadyBilled() {
        stubCompany();
        ServiceAssignment assignment = assignment(MONTHLY_ID, BillingFrequency.MONTHLY, LocalDate.of(2026, 9, 7));
        stubDue(List.of(assignment), monthlyService(),
                posted(assignment.getId(), LocalDate.of(2026, 9, 7)));
        stubSave();

        List<LedgerEntryResponse> entries = ledgerService.postDueServices(CLIENT_ID, COMPANY_ID);

        assertThat(entries).extracting(LedgerEntryResponse::getDueDate)
                .containsExactly(LocalDate.of(2026, 10, 7));
        assertThat(assignment.getNextDueDate()).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    @DisplayName("returns entries of every service earliest due first")
    void ordersEntriesEarliestDueFirst() {
        stubCompany();
        ServiceAssignment monthly = assignment(MONTHLY_ID, BillingFrequency.MONTHLY, LocalDate.of(2026, 9, 7));
        ServiceAssignment oneOff = assignment(ONE_OFF_ID, BillingFrequency.ONE_OFF, LocalDate.of(2026, 9, 20));
        when(serviceAssignmentRepository.findDueForUpdate(COMPANY_ID, TODAY)).thenReturn(List.of(monthly, oneOff));
        when(catalogServiceRepository.findAllById(any())).thenReturn(List.of(monthlyService(), oneOffService()));
        when(ledgerEntryRepository.findPostedPeriods(any())).thenReturn(List.of());
        stubSave();

        List<LedgerEntryResponse> entries = ledgerService.postDueServices(CLIENT_ID, COMPANY_ID);

        assertThat(entries).extracting(LedgerEntryResponse::getDueDate).containsExactly(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 7));
    }

    @Test
    @DisplayName("takes the billing frequency from the catalog's current schedule")
    void followsCurrentCatalogSchedule() {
        stubCompany();
        // Assigned while the catalog billed it monthly; the catalog now bills it once.
        ServiceAssignment assignment = assignment(ONE_OFF_ID, BillingFrequency.MONTHLY, LocalDate.of(2026, 10, 7));
        stubDue(List.of(assignment), oneOffService());
        stubSave();

        List<LedgerEntryResponse> entries = ledgerService.postDueServices(CLIENT_ID, COMPANY_ID);

        assertThat(entries).hasSize(1);
        assertThat(assignment.getServiceType()).isEqualTo(BillingFrequency.ONE_OFF);
        assertThat(assignment.isLedgerPosted()).isTrue();
    }

    @Test
    @DisplayName("creates nothing when no service is due")
    void nothingDue() {
        stubCompany();
        when(serviceAssignmentRepository.findDueForUpdate(COMPANY_ID, TODAY)).thenReturn(List.of());

        assertThat(ledgerService.postDueServices(CLIENT_ID, COMPANY_ID)).isEmpty();

        verify(ledgerEntryRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("raises not found for an unknown client")
    void unknownClient() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> ledgerService.postDueServices(CLIENT_ID, COMPANY_ID))
                .isInstanceOf(ClientNotFoundException.class)
                .hasMessageContaining(CLIENT_ID.toString());

        verify(serviceAssignmentRepository, never()).findDueForUpdate(any(), any());
    }

    @Test
    @DisplayName("raises not found for a company the client does not hold")
    void unknownCompany() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.postDueServices(CLIENT_ID, COMPANY_ID))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(COMPANY_ID.toString());

        verify(serviceAssignmentRepository, never()).findDueForUpdate(any(), any());
    }

    @Test
    @DisplayName("lists a page of a company's entries due within the range, with the totals of them all")
    void listsCompanyEntries() {
        stubCompany();
        LedgerEntry entry = storedEntry();
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2027, 3, 31);
        PageRequest firstPage = PageRequest.of(0, 50, EARLIEST_DUE_FIRST);
        when(ledgerEntryRepository.findByCompanyIdAndDueDateBetween(COMPANY_ID, from, to, firstPage))
                .thenReturn(new PageImpl<>(List.of(entry), firstPage, 1));
        when(ledgerEntryRepository.totalCompanyEntries(COMPANY_ID, from, to)).thenReturn(List.of(
                LedgerSummariesTest.row(COMPANY_ID, "2026-27", 1, "5000.00", "250.00", "4750.00", "855.00",
                        "5605.00")));

        LedgerListResponse listed = ledgerService.listCompanyEntries(CLIENT_ID, COMPANY_ID, query(from, to, 0, 50));

        assertThat(listed.getEntries()).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(entry.getId());
            assertThat(response.getCompanyId()).isEqualTo(COMPANY_ID);
            assertThat(response.getFinancialYear()).isEqualTo("2026-27");
            assertThat(response.getTotalAmount()).isEqualByComparingTo("5605.00");
        });
        assertThat(listed.getPage().getNumber()).isZero();
        assertThat(listed.getPage().getSize()).isEqualTo(50);
        assertThat(listed.getPage().getTotalElements()).isEqualTo(1);
        assertThat(listed.getPage().getTotalPages()).isEqualTo(1);
        assertThat(listed.getPage().isHasNext()).isFalse();
        assertThat(listed.getSummary().getEntryCount()).isEqualTo(1);
        assertThat(listed.getSummary().getTotalAmount()).isEqualByComparingTo("5605.00");
        assertThat(listed.getSummary().getByCompany()).isNull();
    }

    @Test
    @DisplayName("totals every matching entry, not only those on the page returned")
    void totalsCoverEveryPage() {
        stubCompany();
        LocalDate openStart = LocalDate.of(1, 1, 1);
        LocalDate openEnd = LocalDate.of(9999, 12, 31);
        PageRequest secondPage = PageRequest.of(1, 2, EARLIEST_DUE_FIRST);
        when(ledgerEntryRepository.findByCompanyIdAndDueDateBetween(COMPANY_ID, openStart, openEnd, secondPage))
                .thenReturn(new PageImpl<>(List.of(storedEntry(), storedEntry()), secondPage, 5));
        when(ledgerEntryRepository.totalCompanyEntries(COMPANY_ID, openStart, openEnd)).thenReturn(List.of(
                LedgerSummariesTest.row(COMPANY_ID, "2026-27", 5, "25000.00", "1250.00", "23750.00", "4275.00",
                        "28025.00")));

        LedgerListResponse listed = ledgerService.listCompanyEntries(CLIENT_ID, COMPANY_ID, query(null, null, 1, 2));

        assertThat(listed.getEntries()).hasSize(2);
        assertThat(listed.getPage().getNumber()).isEqualTo(1);
        assertThat(listed.getPage().getNumberOfElements()).isEqualTo(2);
        assertThat(listed.getPage().getTotalElements()).isEqualTo(5);
        assertThat(listed.getPage().getTotalPages()).isEqualTo(3);
        assertThat(listed.getPage().isHasNext()).isTrue();
        assertThat(listed.getSummary().getEntryCount()).isEqualTo(5);
        assertThat(listed.getSummary().getTotalAmount()).isEqualByComparingTo("28025.00");
    }

    @Test
    @DisplayName("raises not found when listing a company the client does not hold")
    void listsCompanyEntriesOfUnknownCompany() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.listCompanyEntries(CLIENT_ID, COMPANY_ID, query(null, null, 0, 50)))
                .isInstanceOf(CompanyNotFoundException.class);

        verify(ledgerEntryRepository, never()).findByCompanyIdAndDueDateBetween(any(), any(), any(), any());
        verify(ledgerEntryRepository, never()).totalCompanyEntries(any(), any(), any());
    }

    @Test
    @DisplayName("lists a page of a client's entries, with totals by company")
    void listsClientEntries() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        LedgerEntry entry = storedEntry();
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate openEnd = LocalDate.of(9999, 12, 31);
        PageRequest firstPage = PageRequest.of(0, 50, EARLIEST_DUE_FIRST);
        when(ledgerEntryRepository.findByClientIdAndDueDateBetween(CLIENT_ID, from, openEnd, firstPage))
                .thenReturn(new PageImpl<>(List.of(entry), firstPage, 1));
        when(ledgerEntryRepository.totalClientEntries(CLIENT_ID, from, openEnd)).thenReturn(List.of(
                LedgerSummariesTest.row(COMPANY_ID, "2026-27", 1, "5000.00", "250.00", "4750.00", "855.00",
                        "5605.00")));

        LedgerListResponse listed = ledgerService.listClientEntries(CLIENT_ID, query(from, null, 0, 50));

        assertThat(listed.getEntries()).extracting(LedgerEntryResponse::getId).containsExactly(entry.getId());
        assertThat(listed.getPage().getTotalElements()).isEqualTo(1);
        assertThat(listed.getSummary().getEntryCount()).isEqualTo(1);
        assertThat(listed.getSummary().getByCompany()).singleElement().satisfies(totals -> {
            assertThat(totals.getCompanyId()).isEqualTo(COMPANY_ID);
            assertThat(totals.getCompanyName()).isEqualTo("Mehta Textiles Pvt Ltd");
            assertThat(totals.getEntryCount()).isEqualTo(1);
            assertThat(totals.getTotalAmount()).isEqualByComparingTo("5605.00");
        });
    }

    @Test
    @DisplayName("raises not found when listing the entries of an unknown client")
    void listsClientEntriesOfUnknownClient() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.listClientEntries(CLIENT_ID, query(null, null, 0, 50)))
                .isInstanceOf(ClientNotFoundException.class)
                .hasMessageContaining(CLIENT_ID.toString());

        verify(ledgerEntryRepository, never()).findByClientIdAndDueDateBetween(any(), any(), any(), any());
        verify(ledgerEntryRepository, never()).totalClientEntries(any(), any(), any());
    }

    @Test
    @DisplayName("fetches an entry billed to the company")
    void fetchesEntry() {
        stubCompany();
        LedgerEntry entry = storedEntry();
        when(ledgerEntryRepository.findByIdAndCompanyId(entry.getId(), COMPANY_ID)).thenReturn(Optional.of(entry));

        LedgerEntryResponse response = ledgerService.getEntry(CLIENT_ID, COMPANY_ID, entry.getId());

        assertThat(response.getId()).isEqualTo(entry.getId());
        assertThat(response.getFinancialYear()).isEqualTo("2026-27");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("5605.00");
    }

    @Test
    @DisplayName("raises not found for an entry not billed to the company")
    void fetchesUnknownEntry() {
        stubCompany();
        UUID missing = UUID.randomUUID();
        when(ledgerEntryRepository.findByIdAndCompanyId(missing, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.getEntry(CLIENT_ID, COMPANY_ID, missing))
                .isInstanceOf(LedgerEntryNotFoundException.class)
                .hasMessageContaining(missing.toString())
                .hasMessageContaining(COMPANY_ID.toString());
    }

    @Test
    @DisplayName("corrects the rates of an entry and works its amounts out again, leaving the rest alone")
    void correctsEntry() {
        stubCompany();
        LedgerEntry entry = storedEntry();
        when(ledgerEntryRepository.findByIdAndCompanyId(entry.getId(), COMPANY_ID)).thenReturn(Optional.of(entry));

        LedgerEntryResponse corrected = ledgerService.updateEntry(CLIENT_ID, COMPANY_ID, entry.getId(),
                LedgerEntryUpdateRequest.builder()
                        .serviceFee(new BigDecimal("6000"))
                        .discountPercent(new BigDecimal("10"))
                        .gstRatePercent(new BigDecimal("12.5"))
                        .build());

        // 6000 less 10% is 5400; 12.5% GST on 5400 is 675.
        assertThat(entry.getServiceFee()).isEqualTo(new BigDecimal("6000.00"));
        assertThat(entry.getDiscountPercent()).isEqualTo(new BigDecimal("10.00"));
        assertThat(entry.getDiscountAmount()).isEqualTo(new BigDecimal("600.00"));
        assertThat(entry.getTaxableAmount()).isEqualTo(new BigDecimal("5400.00"));
        assertThat(entry.getGstRatePercent()).isEqualTo(new BigDecimal("12.50"));
        assertThat(entry.getGstAmount()).isEqualTo(new BigDecimal("675.00"));
        assertThat(entry.getTotalAmount()).isEqualTo(new BigDecimal("6075.00"));
        assertThat(corrected.getTotalAmount()).isEqualTo(new BigDecimal("6075.00"));

        // What the entry bills, and when, is untouched.
        assertThat(corrected.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(corrected.getFinancialYear()).isEqualTo("2026-27");
        assertThat(corrected.getEntryDate()).isEqualTo(TODAY);
        assertThat(corrected.getServiceName()).isEqualTo("GST Monthly Return Filing");
        verify(ledgerEntryRepository).flush();
    }

    @Test
    @DisplayName("raises not found when correcting an entry not billed to the company")
    void correctsUnknownEntry() {
        stubCompany();
        UUID missing = UUID.randomUUID();
        when(ledgerEntryRepository.findByIdAndCompanyId(missing, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.updateEntry(CLIENT_ID, COMPANY_ID, missing,
                LedgerEntryUpdateRequest.builder()
                        .serviceFee(new BigDecimal("6000"))
                        .discountPercent(new BigDecimal("10"))
                        .gstRatePercent(new BigDecimal("18"))
                        .build()))
                .isInstanceOf(LedgerEntryNotFoundException.class);

        verify(ledgerEntryRepository, never()).flush();
    }

    @Test
    @DisplayName("summarises a company's financial year: billed, received, pending, discount and last payment")
    void summarisesCompanyYear() {
        stubCompany();
        FinancialYear year = FinancialYears.parse("2026-27");
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2027, 3, 31);
        when(ledgerEntryRepository.totalCompanyEntries(COMPANY_ID, from, to)).thenReturn(List.of(
                LedgerSummariesTest.row(COMPANY_ID, "2026-27", 2, "35000.00", "1750.00", "33250.00", "5985.00",
                        "39235.00"),
                LedgerSummariesTest.payment(COMPANY_ID, "2026-27", 2, "-15000.00")));
        LedgerEntry lastPayment = new LedgerEntry();
        lastPayment.setEntryType(LedgerEntryType.PAYMENT);
        lastPayment.setDueDate(LocalDate.of(2026, 10, 12));
        lastPayment.setTotalAmount(new BigDecimal("-10000.00"));
        when(ledgerEntryRepository.findFirstByCompanyIdAndEntryTypeAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(
                COMPANY_ID, LedgerEntryType.PAYMENT, from, to)).thenReturn(Optional.of(lastPayment));

        CompanyLedgerSummaryResponse summary = ledgerService.summarizeCompany(CLIENT_ID, COMPANY_ID, year);

        assertThat(summary.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(summary.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(summary.getCompanyName()).isEqualTo("Mehta Textiles Pvt Ltd");
        assertThat(summary.getFinancialYear()).isEqualTo("2026-27");
        assertThat(summary.getFromDate()).isEqualTo(from);
        assertThat(summary.getToDate()).isEqualTo(to);
        assertThat(summary.getTotalBilled()).isEqualTo(new BigDecimal("39235.00"));
        assertThat(summary.getTotalPaymentReceived()).isEqualTo(new BigDecimal("15000.00"));
        assertThat(summary.getPending()).isEqualTo(new BigDecimal("24235.00"));
        assertThat(summary.getTotalDiscount()).isEqualTo(new BigDecimal("1750.00"));
        assertThat(summary.getLastPayment().getDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(summary.getLastPayment().getAmount()).isEqualTo(new BigDecimal("10000.00"));
    }

    @Test
    @DisplayName("summarises a year with no entries as zeros, with no last payment")
    void summarisesEmptyYear() {
        stubCompany();
        when(ledgerEntryRepository.totalCompanyEntries(any(), any(), any())).thenReturn(List.of());
        when(ledgerEntryRepository.findFirstByCompanyIdAndEntryTypeAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        CompanyLedgerSummaryResponse summary =
                ledgerService.summarizeCompany(CLIENT_ID, COMPANY_ID, FinancialYears.parse("2027-28"));

        assertThat(summary.getTotalBilled()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getTotalPaymentReceived()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getPending()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getTotalDiscount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getLastPayment()).isNull();
    }

    @Test
    @DisplayName("raises not found when summarising a company the client does not hold")
    void summarisesUnknownCompany() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.summarizeCompany(CLIENT_ID, COMPANY_ID, FinancialYears.parse("2026-27")))
                .isInstanceOf(CompanyNotFoundException.class);

        verify(ledgerEntryRepository, never()).totalCompanyEntries(any(), any(), any());
    }

    @Test
    @DisplayName("summarises a client's year across every company together, naming who paid last")
    void summarisesClientYear() {
        UUID hufId = UUID.fromString("1e7b3c90-4a2d-4f68-b5c1-8d9e0f2a3b47");
        client.setFirstName("Rohan");
        client.setLastName("Mehta");
        client.addCompany(company(hufId, "Rohan Mehta HUF"));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2027, 3, 31);
        when(ledgerEntryRepository.totalClientEntries(CLIENT_ID, from, to)).thenReturn(List.of(
                LedgerSummariesTest.row(COMPANY_ID, "2026-27", 2, "10000.00", "500.00", "9500.00", "1710.00",
                        "11210.00"),
                LedgerSummariesTest.payment(COMPANY_ID, "2026-27", 1, "-5605.00"),
                LedgerSummariesTest.payment(hufId, "2026-27", 1, "-2000.00")));
        when(ledgerEntryRepository.findFirstByClientIdAndEntryTypeAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(
                CLIENT_ID, LedgerEntryType.PAYMENT, from, to))
                .thenReturn(Optional.of(storedPayment(hufId, LocalDate.of(2026, 10, 5), "-2000.00")));

        ClientLedgerSummaryResponse summary = ledgerService.summarizeClient(CLIENT_ID, FinancialYears.parse("2026-27"));

        assertThat(summary.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(summary.getClientName()).isEqualTo("Rohan Mehta");
        assertThat(summary.getFinancialYear()).isEqualTo("2026-27");
        assertThat(summary.getFromDate()).isEqualTo(from);
        assertThat(summary.getToDate()).isEqualTo(to);
        // Both companies together.
        assertThat(summary.getTotalBilled()).isEqualTo(new BigDecimal("11210.00"));
        assertThat(summary.getTotalPaymentReceived()).isEqualTo(new BigDecimal("7605.00"));
        assertThat(summary.getPending()).isEqualTo(new BigDecimal("3605.00"));
        assertThat(summary.getTotalDiscount()).isEqualTo(new BigDecimal("500.00"));
        assertThat(summary.getLastPayment().getDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(summary.getLastPayment().getAmount()).isEqualTo(new BigDecimal("2000.00"));
        assertThat(summary.getLastPayment().getCompanyId()).isEqualTo(hufId);
        assertThat(summary.getLastPayment().getCompanyName()).isEqualTo("Rohan Mehta HUF");
    }

    @Test
    @DisplayName("summarises a client's year with no entries as zeros, with no last payment")
    void summarisesClientYearWithoutPayments() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(ledgerEntryRepository.totalClientEntries(any(), any(), any())).thenReturn(List.of());
        when(ledgerEntryRepository.findFirstByClientIdAndEntryTypeAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        ClientLedgerSummaryResponse summary = ledgerService.summarizeClient(CLIENT_ID, FinancialYears.parse("2027-28"));

        assertThat(summary.getTotalBilled()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getTotalPaymentReceived()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getPending()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getTotalDiscount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.getLastPayment()).isNull();
    }

    @Test
    @DisplayName("gives each company of a client its balance for the financial year, from one totals query")
    void givesCompanyBalances() {
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2027, 3, 31);
        when(ledgerEntryRepository.totalClientEntries(CLIENT_ID, from, to)).thenReturn(List.of(
                LedgerSummariesTest.row(COMPANY_ID, "2026-27", 2, "10000.00", "500.00", "9500.00", "1710.00",
                        "11210.00"),
                LedgerSummariesTest.payment(COMPANY_ID, "2026-27", 1, "-5605.00")));

        Map<UUID, CompanyBalance> balances = ledgerService.companyBalances(CLIENT_ID, FinancialYears.parse("2026-27"));

        assertThat(balances).containsOnlyKeys(COMPANY_ID);
        assertThat(balances.get(COMPANY_ID).totalBilled()).isEqualTo(new BigDecimal("11210.00"));
        assertThat(balances.get(COMPANY_ID).totalPaymentReceived()).isEqualTo(new BigDecimal("5605.00"));
        assertThat(balances.get(COMPANY_ID).pending()).isEqualTo(new BigDecimal("5605.00"));
    }

    @Test
    @DisplayName("gives what each service of a company was billed in the financial year, from one query")
    void givesBilledByService() {
        UUID monthlyAssignment = UUID.randomUUID();
        UUID oneOffAssignment = UUID.randomUUID();
        when(ledgerEntryRepository.totalBilledByService(COMPANY_ID, LedgerEntryType.SERVICE_CHARGE,
                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31))).thenReturn(List.of(
                billed(monthlyAssignment, "11210.00"), billed(oneOffAssignment, "28025.00")));

        Map<UUID, BigDecimal> billed = ledgerService.billedByService(COMPANY_ID, FinancialYears.parse("2026-27"));

        assertThat(billed).containsOnly(Map.entry(monthlyAssignment, new BigDecimal("11210.00")),
                Map.entry(oneOffAssignment, new BigDecimal("28025.00")));
    }

    @Test
    @DisplayName("gives what each catalog service was billed in the financial year across every client")
    void givesBilledByCatalogService() {
        when(ledgerEntryRepository.totalBilledByCatalogService(LedgerEntryType.SERVICE_CHARGE,
                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31))).thenReturn(List.of(
                catalogBilled(MONTHLY_ID, "39235.00"), catalogBilled(ONE_OFF_ID, "28025.00")));

        Map<UUID, BigDecimal> billed = ledgerService.billedByCatalogService(FinancialYears.parse("2026-27"));

        assertThat(billed).containsOnly(Map.entry(MONTHLY_ID, new BigDecimal("39235.00")),
                Map.entry(ONE_OFF_ID, new BigDecimal("28025.00")));
    }

    @Test
    @DisplayName("gives what each manager's clients still owe across every year, from one query")
    void givesPendingByManager() {
        UUID managerId = UUID.randomUUID();
        when(ledgerEntryRepository.totalPendingByManager()).thenReturn(List.of(new ManagerPendingRow() {
            @Override
            public UUID getManagerId() {
                return managerId;
            }

            @Override
            public BigDecimal getTotalPending() {
                return new BigDecimal("20630.00");
            }
        }));

        assertThat(ledgerService.pendingByManager()).containsOnly(Map.entry(managerId, new BigDecimal("20630.00")));
    }

    @Test
    @DisplayName("gives what each listed client still owes across every year, from one query")
    void givesPendingByClient() {
        when(ledgerEntryRepository.totalPendingByClient(List.of(CLIENT_ID))).thenReturn(List.of(new ClientPendingRow() {
            @Override
            public UUID getClientId() {
                return CLIENT_ID;
            }

            @Override
            public BigDecimal getTotalPending() {
                return new BigDecimal("19630.00");
            }
        }));

        assertThat(ledgerService.pendingByClient(List.of(CLIENT_ID)))
                .containsOnly(Map.entry(CLIENT_ID, new BigDecimal("19630.00")));
    }

    @Test
    @DisplayName("gives no pending amounts, without querying, for no clients")
    void givesPendingForNoClients() {
        assertThat(ledgerService.pendingByClient(List.of())).isEmpty();

        verify(ledgerEntryRepository, never()).totalPendingByClient(any());
    }

    @Test
    @DisplayName("gives one manager's pending, zero when their clients have no entries")
    void givesPendingForManager() {
        UUID busy = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        when(ledgerEntryRepository.totalPendingForManager(busy)).thenReturn(new BigDecimal("-500.00"));
        when(ledgerEntryRepository.totalPendingForManager(idle)).thenReturn(null);

        assertThat(ledgerService.pendingForManager(busy)).isEqualTo(new BigDecimal("-500.00"));
        assertThat(ledgerService.pendingForManager(idle)).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("gives what each catalog service was billed across every year up to the firm's today")
    void givesBilledByCatalogServiceToDate() {
        when(ledgerEntryRepository.totalBilledByCatalogService(LedgerEntryType.SERVICE_CHARGE,
                LocalDate.of(1, 1, 1), TODAY)).thenReturn(List.of(catalogBilled(MONTHLY_ID, "61650.00")));

        assertThat(ledgerService.billedByCatalogServiceToDate())
                .containsOnly(Map.entry(MONTHLY_ID, new BigDecimal("61650.00")));
    }

    private static CatalogServiceBilledRow catalogBilled(UUID catalogServiceId, String totalBilled) {
        return new CatalogServiceBilledRow() {
            @Override
            public UUID getCatalogServiceId() {
                return catalogServiceId;
            }

            @Override
            public BigDecimal getTotalBilled() {
                return new BigDecimal(totalBilled);
            }
        };
    }

    private static ServiceBilledRow billed(UUID companyServiceId, String totalBilled) {
        return new ServiceBilledRow() {
            @Override
            public UUID getCompanyServiceId() {
                return companyServiceId;
            }

            @Override
            public BigDecimal getTotalBilled() {
                return new BigDecimal(totalBilled);
            }
        };
    }

    @Test
    @DisplayName("lists a client's years latest first, back to an entry from before they were added")
    void givesClientLedgerStatus() {
        client.setFirstName("Rohan");
        client.setLastName("Mehta");
        // Added in 2024-25, with a payment migrated from 2022-23.
        client.setCreatedAt(Instant.parse("2024-06-01T04:30:00Z"));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(ledgerEntryRepository.totalClientEntries(CLIENT_ID, LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)))
                .thenReturn(List.of(
                        LedgerSummariesTest.row(COMPANY_ID, "2026-27", 1, "5000.00", "250.00", "4750.00", "855.00",
                                "5605.00"),
                        LedgerSummariesTest.payment(COMPANY_ID, "2022-23", 1, "-500.00")));

        ClientLedgerStatusResponse status = ledgerService.clientLedgerStatus(CLIENT_ID, null, false);

        assertThat(status.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(status.getClientName()).isEqualTo("Rohan Mehta");
        assertThat(status.getClientSince()).isEqualTo(LocalDate.of(2022, 4, 1));
        assertThat(status.getYearCount()).isEqualTo(5);
        assertThat(status.getYears()).extracting(FinancialYearStatus::getFinancialYear)
                .containsExactly("2026-27", "2025-26", "2024-25", "2023-24", "2022-23");
        // Today, 14 October 2026, falls in 2026-27.
        assertThat(status.getYears()).extracting(FinancialYearStatus::isCurrent)
                .containsExactly(true, false, false, false, false);
        assertThat(status.getYears()).extracting(FinancialYearStatus::getStatus).containsExactly(
                LedgerYearStatus.PENDING, LedgerYearStatus.SETTLED, LedgerYearStatus.SETTLED,
                LedgerYearStatus.SETTLED, LedgerYearStatus.SETTLED);
        assertThat(status.getYears().getFirst().getCompanies())
                .extracting(CompanyYearStatus::getCompanyName).containsExactly("Mehta Textiles Pvt Ltd");
    }

    @Test
    @DisplayName("lists only the year asked for, keeping the client's whole span in clientSince and yearCount")
    void givesClientLedgerStatusForOneYear() {
        client.setFirstName("Rohan");
        client.setLastName("Mehta");
        client.setCreatedAt(Instant.parse("2024-06-01T04:30:00Z"));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(ledgerEntryRepository.totalClientEntries(CLIENT_ID, LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)))
                .thenReturn(List.of(
                        LedgerSummariesTest.row(COMPANY_ID, "2026-27", 1, "5000.00", "250.00", "4750.00", "855.00",
                                "5605.00"),
                        LedgerSummariesTest.payment(COMPANY_ID, "2022-23", 1, "-500.00")));

        ClientLedgerStatusResponse status = ledgerService.clientLedgerStatus(CLIENT_ID,
                FinancialYears.parse("2022-23"), false);

        assertThat(status.getYears()).extracting(FinancialYearStatus::getFinancialYear).containsExactly("2022-23");
        assertThat(status.getYears().getFirst().getReceived()).isEqualTo(new BigDecimal("500.00"));
        assertThat(status.getYears().getFirst().isCurrent()).isFalse();
        assertThat(status.getClientSince()).isEqualTo(LocalDate.of(2022, 4, 1));
        assertThat(status.getYearCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("lists every year since a client was added, even with no entries")
    void givesClientLedgerStatusWithoutEntries() {
        client.setFirstName("Asha");
        client.setLastName("Kapoor");
        client.setCreatedAt(Instant.parse("2024-06-01T04:30:00Z"));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(ledgerEntryRepository.totalClientEntries(any(), any(), any())).thenReturn(List.of());

        ClientLedgerStatusResponse status = ledgerService.clientLedgerStatus(CLIENT_ID, null, false);

        assertThat(status.getClientSince()).isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(status.getYears()).extracting(FinancialYearStatus::getFinancialYear)
                .containsExactly("2026-27", "2025-26", "2024-25");
        assertThat(status.getYears()).allSatisfy(year -> {
            assertThat(year.getStatus()).isEqualTo(LedgerYearStatus.SETTLED);
            assertThat(year.getClosingBalance()).isEqualTo(new BigDecimal("0.00"));
        });
    }

    @Test
    @DisplayName("raises not found for the ledger status of an unknown client")
    void givesLedgerStatusOfUnknownClient() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.clientLedgerStatus(CLIENT_ID, null, false))
                .isInstanceOf(ClientNotFoundException.class);

        verify(ledgerEntryRepository, never()).totalClientEntries(any(), any(), any());
    }

    @Test
    @DisplayName("ranks clients owing most in the year, received turned positive, at two places")
    void ranksClientsByPendingInYear() {
        when(ledgerEntryRepository.topClientsByPending(LedgerEntryType.SERVICE_CHARGE, LedgerEntryType.PAYMENT,
                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), PageRequest.of(0, 10)))
                .thenReturn(List.of(clientBalance(CLIENT_ID, "39235.00", "-18605.00", "20630.00")));

        List<ClientBalance> top = ledgerService.topClientsByPending(FinancialYears.parse("2026-27"), 10);

        assertThat(top).containsExactly(new ClientBalance(CLIENT_ID, new BigDecimal("39235.00"),
                new BigDecimal("18605.00"), new BigDecimal("20630.00")));
    }

    @Test
    @DisplayName("ranks clients owing most across every year when no year is given")
    void ranksClientsByPendingAcrossEveryYear() {
        // A client never paid sums the CASE's 0, which comes back without decimal places.
        when(ledgerEntryRepository.topClientsByPending(LedgerEntryType.SERVICE_CHARGE, LedgerEntryType.PAYMENT,
                LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), PageRequest.of(0, 3)))
                .thenReturn(List.of(clientBalance(CLIENT_ID, "5605.00", "0", "5605.00")));

        List<ClientBalance> top = ledgerService.topClientsByPending(null, 3);

        assertThat(top.getFirst().totalPaymentReceived()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("ranks managers by what their clients owe in the year, received turned positive")
    void ranksManagersByPending() {
        UUID managerId = UUID.randomUUID();
        when(ledgerEntryRepository.topManagersByPending(LedgerEntryType.SERVICE_CHARGE, LedgerEntryType.PAYMENT,
                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), PageRequest.of(0, 10)))
                .thenReturn(List.of(new ManagerBalanceRow() {
                    @Override
                    public UUID getManagerId() {
                        return managerId;
                    }

                    @Override
                    public BigDecimal getTotalBilled() {
                        return new BigDecimal("39235.00");
                    }

                    @Override
                    public BigDecimal getTotalPaid() {
                        return new BigDecimal("-18605.00");
                    }

                    @Override
                    public BigDecimal getPending() {
                        return new BigDecimal("20630.00");
                    }
                }));

        List<ManagerBalance> top = ledgerService.topManagersByPending(FinancialYears.parse("2026-27"), 10);

        assertThat(top).containsExactly(new ManagerBalance(managerId, new BigDecimal("39235.00"),
                new BigDecimal("18605.00"), new BigDecimal("20630.00")));
    }

    @Test
    @DisplayName("ranks clients by what they were billed in the year, received turned positive, even in credit")
    void ranksClientsByBilled() {
        when(ledgerEntryRepository.topClientsByBilled(LedgerEntryType.SERVICE_CHARGE, LedgerEntryType.PAYMENT,
                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), PageRequest.of(0, 10)))
                .thenReturn(List.of(clientBalance(CLIENT_ID, "5605.00", "-6000.00", "-395.00")));

        List<ClientBalance> top = ledgerService.topClientsByBilled(FinancialYears.parse("2026-27"), 10);

        assertThat(top).containsExactly(new ClientBalance(CLIENT_ID, new BigDecimal("5605.00"),
                new BigDecimal("6000.00"), new BigDecimal("-395.00")));
    }

    @Test
    @DisplayName("ranks clients by billed across every year when no year is given")
    void ranksClientsByBilledAcrossEveryYear() {
        when(ledgerEntryRepository.topClientsByBilled(LedgerEntryType.SERVICE_CHARGE, LedgerEntryType.PAYMENT,
                LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), PageRequest.of(0, 5))).thenReturn(List.of());

        assertThat(ledgerService.topClientsByBilled(null, 5)).isEmpty();
    }

    @Test
    @DisplayName("ranks managers by what was billed to their clients, received turned positive")
    void ranksManagersByBilled() {
        UUID managerId = UUID.randomUUID();
        when(ledgerEntryRepository.topManagersByBilled(LedgerEntryType.SERVICE_CHARGE, LedgerEntryType.PAYMENT,
                LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), PageRequest.of(0, 10)))
                .thenReturn(List.of(new ManagerBalanceRow() {
                    @Override
                    public UUID getManagerId() {
                        return managerId;
                    }

                    @Override
                    public BigDecimal getTotalBilled() {
                        return new BigDecimal("5605.00");
                    }

                    @Override
                    public BigDecimal getTotalPaid() {
                        return new BigDecimal("-6000.00");
                    }

                    @Override
                    public BigDecimal getPending() {
                        return new BigDecimal("-395.00");
                    }
                }));

        List<ManagerBalance> top = ledgerService.topManagersByBilled(null, 10);

        // Ranked by billed, so a manager whose clients are in credit is still listed.
        assertThat(top).containsExactly(new ManagerBalance(managerId, new BigDecimal("5605.00"),
                new BigDecimal("6000.00"), new BigDecimal("-395.00")));
    }

    @Test
    @DisplayName("ranks managers across every year when no year is given")
    void ranksManagersByPendingAcrossEveryYear() {
        when(ledgerEntryRepository.topManagersByPending(LedgerEntryType.SERVICE_CHARGE, LedgerEntryType.PAYMENT,
                LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), PageRequest.of(0, 5))).thenReturn(List.of());

        assertThat(ledgerService.topManagersByPending(null, 5)).isEmpty();
    }

    @Test
    @DisplayName("ranks companies owing most in the year, with their client, received turned positive")
    void ranksCompaniesByPending() {
        when(ledgerEntryRepository.topCompaniesByPending(LedgerEntryType.SERVICE_CHARGE, LedgerEntryType.PAYMENT,
                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), PageRequest.of(0, 10)))
                .thenReturn(List.of(new CompanyBalanceRow() {
                    @Override
                    public UUID getCompanyId() {
                        return COMPANY_ID;
                    }

                    @Override
                    public UUID getClientId() {
                        return CLIENT_ID;
                    }

                    @Override
                    public BigDecimal getTotalBilled() {
                        return new BigDecimal("11210.00");
                    }

                    @Override
                    public BigDecimal getTotalPaid() {
                        return new BigDecimal("-8605.00");
                    }

                    @Override
                    public BigDecimal getPending() {
                        return new BigDecimal("2605.00");
                    }
                }));

        List<ClientCompanyBalance> top = ledgerService.topCompaniesByPending(FinancialYears.parse("2026-27"), 10);

        assertThat(top).containsExactly(new ClientCompanyBalance(COMPANY_ID, CLIENT_ID, new BigDecimal("11210.00"),
                new BigDecimal("8605.00"), new BigDecimal("2605.00")));
    }

    @Test
    @DisplayName("ranks companies by what they were billed, with their client, received turned positive")
    void ranksCompaniesByBilled() {
        when(ledgerEntryRepository.topCompaniesByBilled(LedgerEntryType.SERVICE_CHARGE, LedgerEntryType.PAYMENT,
                LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), PageRequest.of(0, 10)))
                .thenReturn(List.of(new CompanyBalanceRow() {
                    @Override
                    public UUID getCompanyId() {
                        return COMPANY_ID;
                    }

                    @Override
                    public UUID getClientId() {
                        return CLIENT_ID;
                    }

                    @Override
                    public BigDecimal getTotalBilled() {
                        return new BigDecimal("11210.00");
                    }

                    @Override
                    public BigDecimal getTotalPaid() {
                        return new BigDecimal("-12000.00");
                    }

                    @Override
                    public BigDecimal getPending() {
                        return new BigDecimal("-790.00");
                    }
                }));

        List<ClientCompanyBalance> top = ledgerService.topCompaniesByBilled(null, 10);

        // Ranked by billed, so a company in credit is still listed.
        assertThat(top).containsExactly(new ClientCompanyBalance(COMPANY_ID, CLIENT_ID, new BigDecimal("11210.00"),
                new BigDecimal("12000.00"), new BigDecimal("-790.00")));
    }

    @Test
    @DisplayName("lists the latest payments, latest received then latest recorded first")
    void listsRecentPayments() {
        LedgerEntry payment = storedPayment(COMPANY_ID, LocalDate.of(2026, 10, 10), "-10000.00");
        when(ledgerEntryRepository.findByEntryType(LedgerEntryType.PAYMENT,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "dueDate", "createdAt", "id"))))
                .thenReturn(List.of(payment));

        List<LedgerEntryResponse> recent = ledgerService.recentPayments(20);

        assertThat(recent).extracting(LedgerEntryResponse::getId).containsExactly(payment.getId());
        assertThat(recent.getFirst().getTotalAmount()).isEqualTo(new BigDecimal("-10000.00"));
    }

    @Test
    @DisplayName("applies each company's payments to its oldest charges first, leaving the rest unpaid")
    void appliesPaymentsOldestChargeFirst() {
        UUID otherCompany = UUID.randomUUID();
        UUID settledCompany = UUID.randomUUID();
        when(ledgerEntryRepository.totalPaidByCompany(LedgerEntryType.PAYMENT, TODAY)).thenReturn(List.of(
                companyPaid(COMPANY_ID, "-15000.00"), companyPaid(settledCompany, "-9000.00")));
        when(ledgerEntryRepository.chargesByCompanyAndDueDate(LedgerEntryType.SERVICE_CHARGE, TODAY))
                .thenReturn(List.of(
                        companyCharge(COMPANY_ID, LocalDate.of(2026, 6, 1), "10000.00"),
                        companyCharge(settledCompany, LocalDate.of(2026, 6, 15), "8000.00"),
                        companyCharge(otherCompany, LocalDate.of(2026, 7, 1), "3000.00"),
                        companyCharge(COMPANY_ID, LocalDate.of(2026, 7, 1), "10000.00"),
                        companyCharge(COMPANY_ID, LocalDate.of(2026, 8, 1), "10000.00")));

        List<UnpaidCharge> unpaid = ledgerService.unpaidCharges(TODAY);

        assertThat(unpaid).containsExactly(
                new UnpaidCharge(otherCompany, LocalDate.of(2026, 7, 1), new BigDecimal("3000.00")),
                new UnpaidCharge(COMPANY_ID, LocalDate.of(2026, 7, 1), new BigDecimal("5000.00")),
                new UnpaidCharge(COMPANY_ID, LocalDate.of(2026, 8, 1), new BigDecimal("10000.00")));
    }

    @Test
    @DisplayName("totals every month of the year, April first, with zeros for months without entries")
    void totalsEveryMonthOfYear() {
        when(ledgerEntryRepository.totalsByDay(LedgerEntryType.SERVICE_CHARGE, LedgerEntryType.PAYMENT,
                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31))).thenReturn(List.of(
                dailyTotals(LocalDate.of(2026, 4, 1), "11800.00", "0.00", "1000.00"),
                dailyTotals(LocalDate.of(2026, 4, 20), "0.00", "-5000.00", "0.00"),
                dailyTotals(LocalDate.of(2027, 3, 31), "2360.00", "-1000.00", "0.00")));

        List<MonthlyTotals> months = ledgerService.monthlyTotals(FinancialYears.parse("2026-27"));

        assertThat(months).hasSize(12);
        assertThat(months.getFirst()).isEqualTo(new MonthlyTotals(YearMonth.of(2026, 4),
                new BigDecimal("11800.00"), new BigDecimal("5000.00"), new BigDecimal("1000.00")));
        assertThat(months.get(1)).isEqualTo(new MonthlyTotals(YearMonth.of(2026, 5),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")));
        assertThat(months.getLast()).isEqualTo(new MonthlyTotals(YearMonth.of(2027, 3),
                new BigDecimal("2360.00"), new BigDecimal("1000.00"), new BigDecimal("0.00")));
    }

    private static CompanyPaidRow companyPaid(UUID companyId, String paid) {
        return new CompanyPaidRow() {
            @Override
            public UUID getCompanyId() {
                return companyId;
            }

            @Override
            public BigDecimal getTotalPaid() {
                return new BigDecimal(paid);
            }
        };
    }

    private static CompanyChargeRow companyCharge(UUID companyId, LocalDate dueDate, String charged) {
        return new CompanyChargeRow() {
            @Override
            public UUID getCompanyId() {
                return companyId;
            }

            @Override
            public LocalDate getDueDate() {
                return dueDate;
            }

            @Override
            public BigDecimal getTotalCharged() {
                return new BigDecimal(charged);
            }
        };
    }

    private static DailyTotalsRow dailyTotals(LocalDate date, String billed, String paid, String discount) {
        return new DailyTotalsRow() {
            @Override
            public LocalDate getDueDate() {
                return date;
            }

            @Override
            public BigDecimal getTotalBilled() {
                return new BigDecimal(billed);
            }

            @Override
            public BigDecimal getTotalPaid() {
                return new BigDecimal(paid);
            }

            @Override
            public BigDecimal getTotalDiscount() {
                return new BigDecimal(discount);
            }
        };
    }

    private static ClientBalanceRow clientBalance(UUID clientId, String billed, String paid, String pending) {
        return new ClientBalanceRow() {
            @Override
            public UUID getClientId() {
                return clientId;
            }

            @Override
            public BigDecimal getTotalBilled() {
                return new BigDecimal(billed);
            }

            @Override
            public BigDecimal getTotalPaid() {
                return new BigDecimal(paid);
            }

            @Override
            public BigDecimal getPending() {
                return new BigDecimal(pending);
            }
        };
    }

    @Test
    @DisplayName("totals every client's entries in the financial year, as a client's summary does")
    void totalsYearAcrossClients() {
        UUID otherCompany = UUID.randomUUID();
        when(ledgerEntryRepository.totalEntries(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31)))
                .thenReturn(List.of(
                        LedgerSummariesTest.row(COMPANY_ID, "2026-27", 2, "10000.00", "500.00", "9500.00", "1710.00",
                                "11210.00"),
                        LedgerSummariesTest.row(otherCompany, "2026-27", 1, "25000.00", "1250.00", "23750.00",
                                "4275.00", "28025.00"),
                        LedgerSummariesTest.payment(otherCompany, "2026-27", 1, "-10000.00")));

        LedgerSummary totals = ledgerService.totalsForYear(FinancialYears.parse("2026-27"));

        assertThat(totals.getTotalAmount()).isEqualTo(new BigDecimal("39235.00"));
        assertThat(totals.getTotalPaid()).isEqualTo(new BigDecimal("10000.00"));
        assertThat(totals.getBalance()).isEqualTo(new BigDecimal("29235.00"));
        assertThat(totals.getTotalDiscountAmount()).isEqualTo(new BigDecimal("1750.00"));
    }

    @Test
    @DisplayName("raises not found when summarising an unknown client")
    void summarisesUnknownClient() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.summarizeClient(CLIENT_ID, FinancialYears.parse("2026-27")))
                .isInstanceOf(ClientNotFoundException.class)
                .hasMessageContaining(CLIENT_ID.toString());

        verify(ledgerEntryRepository, never()).totalClientEntries(any(), any(), any());
    }

    @Test
    @DisplayName("exports a client's year as a workbook named after them, with the summary and every entry")
    void exportsClientYear() {
        client.setFirstName("Rohan");
        client.setLastName("Mehta");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2027, 3, 31);
        when(ledgerEntryRepository.totalClientEntries(CLIENT_ID, from, to)).thenReturn(List.of(
                LedgerSummariesTest.row(COMPANY_ID, "2026-27", 1, "5000.00", "250.00", "4750.00", "855.00",
                        "5605.00")));
        when(ledgerEntryRepository.findFirstByClientIdAndEntryTypeAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(
                CLIENT_ID, LedgerEntryType.PAYMENT, from, to)).thenReturn(Optional.empty());
        when(ledgerEntryRepository.findAllByClientIdAndDueDateBetween(CLIENT_ID, from, to, EARLIEST_DUE_FIRST))
                .thenReturn(List.of(storedEntry()));

        LedgerExport export = ledgerService.exportClient(CLIENT_ID, FinancialYears.parse("2026-27"));

        assertThat(export.fileName()).isEqualTo("Rohan_Mehta_Ledger_FY2026-27.xlsx");
        Map<String, String> parts = XlsxWorkbookTest.parts(export.content());
        assertThat(parts.get("xl/worksheets/sheet1.xml"))
                .contains(">Rohan Mehta<")
                .contains("<v>5605.00</v>")
                // Generated on the firm's today, 14 October 2026.
                .contains("<v>46309</v>");
        assertThat(parts.get("xl/worksheets/sheet2.xml"))
                .contains(">Mehta Textiles Pvt Ltd<")
                .contains(">GST Monthly Return Filing<");
        // The client's only company gets its own sheet too.
        assertThat(parts.get("xl/workbook.xml")).contains("<sheet name=\"Mehta Textiles Pvt Ltd\" sheetId=\"3\"");
        assertThat(parts.get("xl/worksheets/sheet3.xml")).contains(">GST Monthly Return Filing<");
    }

    @Test
    @DisplayName("raises not found when exporting an unknown client")
    void exportsUnknownClient() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.exportClient(CLIENT_ID, FinancialYears.parse("2026-27")))
                .isInstanceOf(ClientNotFoundException.class);

        verify(ledgerEntryRepository, never()).findAllByClientIdAndDueDateBetween(any(), any(), any(), any());
    }

    @Test
    @DisplayName("names an export with only letters, digits and underscores, falling back when none survive")
    void namesExportSafely() {
        FinancialYear year = FinancialYears.parse("2026-27");
        assertThat(LedgerServiceImpl.exportFileName(" Anil  D'Souza-Pinto Jr.", "Client", year))
                .isEqualTo("Anil_D_Souza_Pinto_Jr_Ledger_FY2026-27.xlsx");
        assertThat(LedgerServiceImpl.exportFileName("M/s Mehta & Sons (HUF)", "Company", year))
                .isEqualTo("M_s_Mehta_Sons_HUF_Ledger_FY2026-27.xlsx");
        assertThat(LedgerServiceImpl.exportFileName("\u0930\u093e\u092e \u0936\u0930\u094d\u092e\u093e",
                "Client", year)).isEqualTo("Client_Ledger_FY2026-27.xlsx");
    }

    @Test
    @DisplayName("exports a company's year as a workbook named after it, with its summary and entries")
    void exportsCompanyYear() {
        client.setFirstName("Rohan");
        client.setLastName("Mehta");
        stubCompany();
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2027, 3, 31);
        when(ledgerEntryRepository.totalCompanyEntries(COMPANY_ID, from, to)).thenReturn(List.of(
                LedgerSummariesTest.row(COMPANY_ID, "2026-27", 1, "5000.00", "250.00", "4750.00", "855.00",
                        "5605.00")));
        when(ledgerEntryRepository.findFirstByCompanyIdAndEntryTypeAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(
                COMPANY_ID, LedgerEntryType.PAYMENT, from, to)).thenReturn(Optional.empty());
        when(ledgerEntryRepository.findAllByCompanyIdAndDueDateBetween(COMPANY_ID, from, to, EARLIEST_DUE_FIRST))
                .thenReturn(List.of(storedEntry()));

        LedgerExport export = ledgerService.exportCompany(CLIENT_ID, COMPANY_ID, FinancialYears.parse("2026-27"));

        assertThat(export.fileName()).isEqualTo("Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.xlsx");
        Map<String, String> parts = XlsxWorkbookTest.parts(export.content());
        assertThat(parts.get("xl/worksheets/sheet1.xml"))
                .contains(">Company Ledger<")
                .contains(">Rohan Mehta<")
                .contains(">Mehta Textiles Pvt Ltd<")
                .contains("<v>5605.00</v>")
                .contains("<v>46309</v>");
        assertThat(parts.get("xl/worksheets/sheet2.xml"))
                .contains(">GST Monthly Return Filing<")
                .doesNotContain(">Mehta Textiles Pvt Ltd<");
    }

    @Test
    @DisplayName("raises not found when exporting a company the client does not hold")
    void exportsUnknownCompany() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.exportCompany(CLIENT_ID, COMPANY_ID, FinancialYears.parse("2026-27")))
                .isInstanceOf(CompanyNotFoundException.class);

        verify(ledgerEntryRepository, never()).findAllByCompanyIdAndDueDateBetween(any(), any(), any(), any());
    }

    private ClientCompany company(UUID id, String name) {
        ClientCompany other = new ClientCompany();
        other.setId(id);
        other.setCompanyName(name);
        return other;
    }

    private LedgerEntry storedPayment(UUID companyId, LocalDate dateReceived, String total) {
        LedgerEntry payment = new LedgerEntry();
        payment.setId(UUID.randomUUID());
        payment.setClientId(CLIENT_ID);
        payment.setCompanyId(companyId);
        payment.setEntryType(LedgerEntryType.PAYMENT);
        payment.setDueDate(dateReceived);
        payment.setTotalAmount(new BigDecimal(total));
        payment.setCreatedAt(Instant.now());
        return payment;
    }

    @Test
    @DisplayName("records a payment as a negative ledger entry dated when it was received")
    void recordsPayment() {
        stubCompany();
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> {
            LedgerEntry saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        LedgerEntryResponse payment = ledgerService.recordPayment(CLIENT_ID, COMPANY_ID, PaymentRequest.builder()
                .amountReceived(new BigDecimal("28025"))
                .dateReceived(LocalDate.of(2026, 3, 30))
                .method(PaymentMethod.BANK_TRANSFER)
                .reference("  HDFC0001234567890  ")
                .comment("  Settles the incorporation invoice  ")
                .build());

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        LedgerEntry stored = captor.getValue();
        assertThat(stored.getEntryType()).isEqualTo(LedgerEntryType.PAYMENT);
        assertThat(stored.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(stored.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(stored.getTotalAmount()).isEqualTo(new BigDecimal("-28025.00"));
        assertThat(stored.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 30));
        // Received before 1 April, so it falls in the earlier financial year.
        assertThat(stored.getFinancialYear()).isEqualTo("2025-26");
        assertThat(stored.getEntryDate()).isEqualTo(TODAY);
        assertThat(stored.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(stored.getPaymentReference()).isEqualTo("HDFC0001234567890");
        assertThat(stored.getComment()).isEqualTo("Settles the incorporation invoice");
        // A payment carries none of the service figures.
        assertThat(stored.getCompanyServiceId()).isNull();
        assertThat(stored.getCatalogServiceId()).isNull();
        assertThat(stored.getServiceName()).isNull();
        assertThat(stored.getServiceFee()).isNull();
        assertThat(stored.getGstAmount()).isNull();

        assertThat(payment.getEntryType()).isEqualTo(LedgerEntryType.PAYMENT);
        assertThat(payment.getTotalAmount()).isEqualTo(new BigDecimal("-28025.00"));
        verifyNoInteractions(serviceAssignmentRepository);
    }

    @Test
    @DisplayName("stores a blank reference and comment as absent")
    void recordsPaymentWithoutReference() {
        stubCompany();
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ledgerService.recordPayment(CLIENT_ID, COMPANY_ID, PaymentRequest.builder()
                .amountReceived(new BigDecimal("500"))
                .dateReceived(TODAY)
                .method(PaymentMethod.CASH)
                .reference("   ")
                .build());

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentReference()).isNull();
        assertThat(captor.getValue().getComment()).isNull();
    }

    @Test
    @DisplayName("accepts a payment dated today in India, though it is still the day before in UTC")
    void acceptsPaymentDatedTodayInIndia() {
        stubCompany();
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LedgerEntryResponse payment = ledgerService.recordPayment(CLIENT_ID, COMPANY_ID, PaymentRequest.builder()
                .amountReceived(new BigDecimal("500"))
                .dateReceived(LocalDate.of(2026, 10, 14))
                .method(PaymentMethod.UPI)
                .build());

        assertThat(payment.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    @DisplayName("refuses a payment dated after today")
    void rejectsFuturePayment() {
        stubCompany();

        assertThatThrownBy(() -> ledgerService.recordPayment(CLIENT_ID, COMPANY_ID, PaymentRequest.builder()
                .amountReceived(new BigDecimal("500"))
                .dateReceived(LocalDate.of(2026, 10, 15))
                .method(PaymentMethod.CHEQUE)
                .build()))
                .isInstanceOf(FuturePaymentDateException.class)
                .hasMessageContaining("2026-10-15");

        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("raises not found when recording a payment for a company the client does not hold")
    void recordsPaymentForUnknownCompany() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.recordPayment(CLIENT_ID, COMPANY_ID, PaymentRequest.builder()
                .amountReceived(new BigDecimal("500"))
                .dateReceived(TODAY)
                .method(PaymentMethod.CASH)
                .build()))
                .isInstanceOf(CompanyNotFoundException.class);

        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("lists a page of a company's payments, with the count and total of every matching payment")
    void listsPayments() {
        stubCompany();
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2027, 3, 31);
        LedgerListQuery latestFirst = new LedgerListQuery(from, to, 0, 2, LedgerSortField.DUE_DATE,
                Sort.Direction.DESC);
        PageRequest firstPage = LedgerServiceImpl.pageRequest(latestFirst);
        LedgerEntry latest = storedPayment(COMPANY_ID, LocalDate.of(2026, 10, 5), "-2000.00");
        LedgerEntry earlier = storedPayment(COMPANY_ID, LocalDate.of(2026, 9, 1), "-5605.00");
        when(ledgerEntryRepository.findByCompanyIdAndEntryTypeAndDueDateBetween(COMPANY_ID, LedgerEntryType.PAYMENT,
                from, to, firstPage)).thenReturn(new PageImpl<>(List.of(latest, earlier), firstPage, 3));
        when(ledgerEntryRepository.totalCompanyEntries(COMPANY_ID, from, to)).thenReturn(List.of(
                LedgerSummariesTest.row(COMPANY_ID, "2026-27", 2, "10000.00", "500.00", "9500.00", "1710.00",
                        "11210.00"),
                LedgerSummariesTest.payment(COMPANY_ID, "2026-27", 3, "-8605.00")));

        PaymentListResponse listed = ledgerService.listPayments(CLIENT_ID, COMPANY_ID, latestFirst);

        assertThat(listed.getPayments()).extracting(LedgerEntryResponse::getId)
                .containsExactly(latest.getId(), earlier.getId());
        assertThat(listed.getPayments()).allSatisfy(payment ->
                assertThat(payment.getEntryType()).isEqualTo(LedgerEntryType.PAYMENT));
        assertThat(listed.getPage().getTotalElements()).isEqualTo(3);
        assertThat(listed.getPage().getTotalPages()).isEqualTo(2);
        assertThat(listed.getPage().isHasNext()).isTrue();
        // Every payment in the range, not just the two on this page; the service charges are left out.
        assertThat(listed.getSummary().getPaymentCount()).isEqualTo(3);
        assertThat(listed.getSummary().getTotalReceived()).isEqualTo(new BigDecimal("8605.00"));
    }

    @Test
    @DisplayName("raises not found when listing the payments of a company the client does not hold")
    void listsPaymentsOfUnknownCompany() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.listPayments(CLIENT_ID, COMPANY_ID, query(null, null, 0, 50)))
                .isInstanceOf(CompanyNotFoundException.class);

        verify(ledgerEntryRepository, never())
                .findByCompanyIdAndEntryTypeAndDueDateBetween(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("replaces every detail of a payment, moving it to the financial year of its new date")
    void updatesPayment() {
        stubCompany();
        LedgerEntry payment = storedPayment(COMPANY_ID, LocalDate.of(2026, 4, 2), "-5000.00");
        payment.setEntryDate(LocalDate.of(2026, 4, 3));
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setPaymentReference("OLD-REF");
        payment.setComment("Old note");
        when(ledgerEntryRepository.findByIdAndCompanyId(payment.getId(), COMPANY_ID))
                .thenReturn(Optional.of(payment));

        LedgerEntryResponse updated = ledgerService.updatePayment(CLIENT_ID, COMPANY_ID, payment.getId(),
                PaymentRequest.builder()
                        .amountReceived(new BigDecimal("5500"))
                        .dateReceived(LocalDate.of(2026, 3, 28))
                        .method(PaymentMethod.CHEQUE)
                        .reference("  000123  ")
                        .build());

        assertThat(payment.getTotalAmount()).isEqualTo(new BigDecimal("-5500.00"));
        assertThat(payment.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 28));
        // Moved back before 1 April, so it now falls in the earlier financial year.
        assertThat(payment.getFinancialYear()).isEqualTo("2025-26");
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CHEQUE);
        assertThat(payment.getPaymentReference()).isEqualTo("000123");
        // An omitted comment is cleared.
        assertThat(payment.getComment()).isNull();
        // When it was recorded does not change.
        assertThat(payment.getEntryDate()).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(payment.getEntryType()).isEqualTo(LedgerEntryType.PAYMENT);
        assertThat(updated.getTotalAmount()).isEqualTo(new BigDecimal("-5500.00"));
        verify(ledgerEntryRepository).flush();
    }

    @Test
    @DisplayName("refuses to update a service charge as though it were a payment")
    void refusesToUpdateChargeAsPayment() {
        stubCompany();
        LedgerEntry charge = storedEntry();
        when(ledgerEntryRepository.findByIdAndCompanyId(charge.getId(), COMPANY_ID)).thenReturn(Optional.of(charge));

        assertThatThrownBy(() -> ledgerService.updatePayment(CLIENT_ID, COMPANY_ID, charge.getId(),
                PaymentRequest.builder()
                        .amountReceived(new BigDecimal("500"))
                        .dateReceived(TODAY)
                        .method(PaymentMethod.CASH)
                        .build()))
                .isInstanceOf(NotAPaymentException.class)
                .hasMessageContaining(charge.getId().toString());

        assertThat(charge.getTotalAmount()).isEqualByComparingTo("5605.00");
        assertThat(charge.getPaymentMethod()).isNull();
        verify(ledgerEntryRepository, never()).flush();
    }

    @Test
    @DisplayName("refuses to move a payment to a date after today")
    void refusesToUpdatePaymentIntoFuture() {
        stubCompany();
        LedgerEntry payment = storedPayment(COMPANY_ID, LocalDate.of(2026, 10, 1), "-5000.00");
        when(ledgerEntryRepository.findByIdAndCompanyId(payment.getId(), COMPANY_ID))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> ledgerService.updatePayment(CLIENT_ID, COMPANY_ID, payment.getId(),
                PaymentRequest.builder()
                        .amountReceived(new BigDecimal("5000"))
                        .dateReceived(LocalDate.of(2026, 10, 15))
                        .method(PaymentMethod.UPI)
                        .build()))
                .isInstanceOf(FuturePaymentDateException.class);

        assertThat(payment.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        verify(ledgerEntryRepository, never()).flush();
    }

    @Test
    @DisplayName("raises not found when updating a payment not recorded for the company")
    void updatesUnknownPayment() {
        stubCompany();
        UUID missing = UUID.randomUUID();
        when(ledgerEntryRepository.findByIdAndCompanyId(missing, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.updatePayment(CLIENT_ID, COMPANY_ID, missing,
                PaymentRequest.builder()
                        .amountReceived(new BigDecimal("500"))
                        .dateReceived(TODAY)
                        .method(PaymentMethod.CASH)
                        .build()))
                .isInstanceOf(LedgerEntryNotFoundException.class);
    }

    @Test
    @DisplayName("refuses to correct the rates of a payment")
    void refusesToCorrectPayment() {
        stubCompany();
        LedgerEntry payment = new LedgerEntry();
        payment.setId(UUID.randomUUID());
        payment.setEntryType(LedgerEntryType.PAYMENT);
        payment.setTotalAmount(new BigDecimal("-500.00"));
        when(ledgerEntryRepository.findByIdAndCompanyId(payment.getId(), COMPANY_ID))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> ledgerService.updateEntry(CLIENT_ID, COMPANY_ID, payment.getId(),
                LedgerEntryUpdateRequest.builder()
                        .serviceFee(new BigDecimal("500"))
                        .discountPercent(new BigDecimal("0"))
                        .gstRatePercent(new BigDecimal("18"))
                        .build()))
                .isInstanceOf(PaymentNotCorrectableException.class)
                .hasMessageContaining(payment.getId().toString());

        assertThat(payment.getServiceFee()).isNull();
        assertThat(payment.getTotalAmount()).isEqualTo(new BigDecimal("-500.00"));
        verify(ledgerEntryRepository, never()).flush();
    }

    @Test
    @DisplayName("deletes an entry and leaves the service it billed as it was")
    void deletesEntry() {
        stubCompany();
        LedgerEntry entry = storedEntry();
        when(ledgerEntryRepository.findByIdAndCompanyId(entry.getId(), COMPANY_ID)).thenReturn(Optional.of(entry));

        ledgerService.deleteEntry(CLIENT_ID, COMPANY_ID, entry.getId());

        verify(ledgerEntryRepository).delete(entry);
        verifyNoInteractions(serviceAssignmentRepository);
    }

    @Test
    @DisplayName("raises not found when deleting an entry not billed to the company")
    void deletesUnknownEntry() {
        stubCompany();
        UUID missing = UUID.randomUUID();
        when(ledgerEntryRepository.findByIdAndCompanyId(missing, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.deleteEntry(CLIENT_ID, COMPANY_ID, missing))
                .isInstanceOf(LedgerEntryNotFoundException.class);

        verify(ledgerEntryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("raises not found when fetching under a company the client does not hold")
    void fetchesEntryOfUnknownCompany() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.getEntry(CLIENT_ID, COMPANY_ID, UUID.randomUUID()))
                .isInstanceOf(CompanyNotFoundException.class);

        verify(ledgerEntryRepository, never()).findByIdAndCompanyId(any(), any());
    }

    @Test
    @DisplayName("sorts by the field asked for, then breaks ties in due date order and by identifier")
    void sortsByChosenFieldThenTieBreakers() {
        PageRequest request = LedgerServiceImpl.pageRequest(new LedgerListQuery(null, null, 3, 20,
                LedgerSortField.TOTAL_AMOUNT, Sort.Direction.DESC));

        assertThat(request.getPageNumber()).isEqualTo(3);
        assertThat(request.getPageSize()).isEqualTo(20);
        assertThat(request.getSort()).containsExactly(
                Sort.Order.desc("totalAmount"),
                Sort.Order.asc("dueDate"),
                Sort.Order.asc("serviceName"),
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("does not repeat the chosen field among the tie breakers")
    void doesNotRepeatChosenField() {
        PageRequest request = LedgerServiceImpl.pageRequest(new LedgerListQuery(null, null, 0, 50,
                LedgerSortField.DUE_DATE, Sort.Direction.DESC));

        assertThat(request.getSort()).containsExactly(
                Sort.Order.desc("dueDate"),
                Sort.Order.asc("serviceName"),
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("sorts earliest due first by default")
    void sortsEarliestDueFirstByDefault() {
        assertThat(LedgerServiceImpl.pageRequest(query(null, null, 0, 50)).getSort()).isEqualTo(EARLIEST_DUE_FIRST);
    }

    /** A list query in the default order, earliest due first. */
    private static LedgerListQuery query(LocalDate fromDate, LocalDate toDate, int page, int size) {
        return new LedgerListQuery(fromDate, toDate, page, size, LedgerSortField.DUE_DATE, Sort.Direction.ASC);
    }

    private LedgerEntry storedEntry() {
        LedgerEntry entry = new LedgerEntry();
        entry.setId(UUID.randomUUID());
        entry.setClientId(CLIENT_ID);
        entry.setCompanyId(COMPANY_ID);
        entry.setEntryType(LedgerEntryType.SERVICE_CHARGE);
        entry.setCompanyServiceId(UUID.randomUUID());
        entry.setCatalogServiceId(MONTHLY_ID);
        entry.setServiceName("GST Monthly Return Filing");
        entry.setServiceType(BillingFrequency.MONTHLY);
        entry.setDueDate(LocalDate.of(2026, 10, 7));
        entry.setFinancialYear("2026-27");
        entry.setEntryDate(TODAY);
        entry.setServiceFee(new BigDecimal("5000.00"));
        entry.setDiscountPercent(new BigDecimal("5.00"));
        entry.setDiscountAmount(new BigDecimal("250.00"));
        entry.setTaxableAmount(new BigDecimal("4750.00"));
        entry.setGstRatePercent(new BigDecimal("18.00"));
        entry.setGstAmount(new BigDecimal("855.00"));
        entry.setTotalAmount(new BigDecimal("5605.00"));
        entry.setCreatedAt(Instant.now());
        return entry;
    }

    private ServiceAssignment assignment(UUID serviceId, BillingFrequency serviceType, LocalDate nextDueDate) {
        ServiceAssignment assignment = new ServiceAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setCompany(company);
        assignment.setCatalogServiceId(serviceId);
        assignment.setServiceType(serviceType);
        assignment.setDeliveryDate(nextDueDate);
        assignment.setNextDueDate(nextDueDate);
        assignment.setDiscountPercent(new BigDecimal("5.00"));
        return assignment;
    }

    /** Billed monthly on the 7th. */
    private CatalogService monthlyService() {
        CatalogService service = new CatalogService();
        service.setId(MONTHLY_ID);
        service.setServiceName("GST Monthly Return Filing");
        service.setStandardFee(new BigDecimal("5000.00"));
        service.setGstRatePercent(new BigDecimal("18.00"));
        service.setBillingFrequency(BillingFrequency.MONTHLY);
        service.setInvoiceDayOfMonth(7);
        return service;
    }

    private CatalogService oneOffService() {
        CatalogService service = new CatalogService();
        service.setId(ONE_OFF_ID);
        service.setServiceName("Company Incorporation");
        service.setStandardFee(new BigDecimal("25000.00"));
        service.setGstRatePercent(new BigDecimal("18.00"));
        service.setBillingFrequency(BillingFrequency.ONE_OFF);
        return service;
    }

    private PostedPeriod posted(UUID companyServiceId, LocalDate dueDate) {
        return new PostedPeriod() {
            @Override
            public UUID getCompanyServiceId() {
                return companyServiceId;
            }

            @Override
            public LocalDate getDueDate() {
                return dueDate;
            }
        };
    }

    private void stubCompany() {
        when(clientRepository.existsById(CLIENT_ID)).thenReturn(true);
        when(clientCompanyRepository.findByIdAndClientId(COMPANY_ID, CLIENT_ID)).thenReturn(Optional.of(company));
    }

    private void stubDue(List<ServiceAssignment> due, CatalogService service, PostedPeriod... posted) {
        when(serviceAssignmentRepository.findDueForUpdate(COMPANY_ID, TODAY)).thenReturn(due);
        when(catalogServiceRepository.findAllById(any())).thenReturn(List.of(service));
        when(ledgerEntryRepository.findPostedPeriods(any())).thenReturn(List.of(posted));
    }

    /** Hands back what was saved, with the identifier and timestamp persisting would assign. */
    private void stubSave() {
        when(ledgerEntryRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<LedgerEntry> entries = invocation.getArgument(0);
            entries.forEach(entry -> {
                entry.setId(UUID.randomUUID());
                entry.setCreatedAt(Instant.now());
            });
            return entries;
        });
    }
}
