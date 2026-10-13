package com.psc.cl.ledger.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.config.ApiPaths;
import com.psc.cl.ledger.dto.ClientLedgerStatusResponse;
import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyTotals;
import com.psc.cl.ledger.dto.CompanyYearStatus;
import com.psc.cl.ledger.dto.FinancialYearStatus;
import com.psc.cl.ledger.dto.FinancialYearTotals;
import com.psc.cl.ledger.dto.LastPayment;
import com.psc.cl.ledger.dto.LedgerEntryResponse;
import com.psc.cl.ledger.dto.LedgerEntryUpdateRequest;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.ledger.dto.LedgerListQuery;
import com.psc.cl.ledger.dto.LedgerListResponse;
import com.psc.cl.ledger.dto.LedgerSortField;
import com.psc.cl.ledger.dto.LedgerSummary;
import com.psc.cl.ledger.dto.LedgerYearStatus;
import com.psc.cl.ledger.dto.PageMetadata;
import com.psc.cl.ledger.dto.PaymentListResponse;
import com.psc.cl.ledger.dto.PaymentRequest;
import com.psc.cl.ledger.dto.PaymentSummary;
import com.psc.cl.ledger.exception.FuturePaymentDateException;
import com.psc.cl.ledger.exception.LedgerEntryNotFoundException;
import com.psc.cl.ledger.exception.NotAPaymentException;
import com.psc.cl.ledger.exception.PaymentNotCorrectableException;
import com.psc.cl.ledger.model.LedgerEntryType;
import com.psc.cl.ledger.model.PaymentMethod;
import com.psc.cl.ledger.service.FinancialYears;
import com.psc.cl.ledger.service.LedgerService;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@WebMvcTest(LedgerController.class)
class LedgerControllerTest {

    private static final UUID CLIENT_ID = UUID.fromString("7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35");

    private static final UUID COMPANY_ID = UUID.fromString("4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28");

    private static final UUID SERVICE_ID = UUID.fromString("3b8e5f21-9c4d-4a17-8e62-1f0d7a9c5b34");

    private static final String LEDGER_URL =
            ApiPaths.V1 + "/clients/" + CLIENT_ID + "/companies/" + COMPANY_ID + "/ledger-entries";

    private static final String PAYMENTS_URL =
            ApiPaths.V1 + "/clients/" + CLIENT_ID + "/companies/" + COMPANY_ID + "/payments";

    private static final String COMPANY_SUMMARY_URL =
            ApiPaths.V1 + "/clients/" + CLIENT_ID + "/companies/" + COMPANY_ID + "/ledger-summary";

    private static final String CLIENT_SUMMARY_URL = ApiPaths.V1 + "/clients/" + CLIENT_ID + "/ledger-summary";

    private static final String CLIENT_LEDGER_URL = ApiPaths.V1 + "/clients/" + CLIENT_ID + "/ledger-entries";

    private static final String CLIENT_EXPORT_URL = ApiPaths.V1 + "/clients/" + CLIENT_ID + "/ledger-export";

    private static final String COMPANY_EXPORT_URL =
            ApiPaths.V1 + "/clients/" + CLIENT_ID + "/companies/" + COMPANY_ID + "/ledger-export";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LedgerService ledgerService;

    @Test
    @DisplayName("returns 200 with the entries created and every financial figure")
    void postsDueServices() throws Exception {
        when(ledgerService.postDueServices(CLIENT_ID, COMPANY_ID)).thenReturn(List.of(response()));

        mockMvc.perform(post(LEDGER_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$[0].companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$[0].serviceId").value(SERVICE_ID.toString()))
                .andExpect(jsonPath("$[0].serviceType").value("MONTHLY"))
                .andExpect(jsonPath("$[0].dueDate").value("2026-10-07"))
                .andExpect(jsonPath("$[0].entryDate").value("2026-10-13"))
                .andExpect(jsonPath("$[0].serviceFee").value(5000.0))
                .andExpect(jsonPath("$[0].discountPercent").value(5.0))
                .andExpect(jsonPath("$[0].discountAmount").value(250.0))
                .andExpect(jsonPath("$[0].taxableAmount").value(4750.0))
                .andExpect(jsonPath("$[0].gstRatePercent").value(18.0))
                .andExpect(jsonPath("$[0].gstAmount").value(855.0))
                .andExpect(jsonPath("$[0].totalAmount").value(5605.0));
    }

    @Test
    @DisplayName("returns 200 with an empty array when nothing was due")
    void nothingDue() throws Exception {
        when(ledgerService.postDueServices(CLIENT_ID, COMPANY_ID)).thenReturn(List.of());

        mockMvc.perform(post(LEDGER_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("returns 404 when the client is unknown")
    void unknownClient() throws Exception {
        when(ledgerService.postDueServices(CLIENT_ID, COMPANY_ID)).thenThrow(new ClientNotFoundException(CLIENT_ID));

        mockMvc.perform(post(LEDGER_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 404 when the client holds no such company")
    void unknownCompany() throws Exception {
        when(ledgerService.postDueServices(CLIENT_ID, COMPANY_ID))
                .thenThrow(new CompanyNotFoundException(CLIENT_ID, COMPANY_ID));

        mockMvc.perform(post(LEDGER_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 400 when an identifier is not a UUID")
    void malformedId() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/clients/not-a-uuid/companies/" + COMPANY_ID + "/ledger-entries"))
                .andExpect(status().isBadRequest());

        verify(ledgerService, never()).postDueServices(any(), any());
    }

    @Test
    @DisplayName("returns 200 with the first page of a company's entries, the page position and the totals")
    void listsCompanyEntries() throws Exception {
        when(ledgerService.listCompanyEntries(CLIENT_ID, COMPANY_ID, query(null, null, 0, 50)))
                .thenReturn(listed(List.of(response()), page(0, 50, 1, 1, false), summary(1, null)));

        mockMvc.perform(get(LEDGER_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.entries[0].financialYear").value("2026-27"))
                .andExpect(jsonPath("$.entries[0].totalAmount").value(5605.0))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(50))
                .andExpect(jsonPath("$.page.numberOfElements").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.hasNext").value(false))
                .andExpect(jsonPath("$.summary.entryCount").value(1))
                .andExpect(jsonPath("$.summary.totalServiceFee").value(5000.0))
                .andExpect(jsonPath("$.summary.totalDiscountAmount").value(250.0))
                .andExpect(jsonPath("$.summary.totalTaxableAmount").value(4750.0))
                .andExpect(jsonPath("$.summary.totalGstAmount").value(855.0))
                .andExpect(jsonPath("$.summary.totalAmount").value(5605.0))
                .andExpect(jsonPath("$.summary.byFinancialYear.length()").value(1))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].financialYear").value("2026-27"))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].entryCount").value(1))
                .andExpect(jsonPath("$.summary.byFinancialYear[0].totalAmount").value(5605.0))
                .andExpect(jsonPath("$.summary.byCompany").doesNotExist());
    }

    @Test
    @DisplayName("passes the page and size asked for through to the service")
    void passesPageThrough() throws Exception {
        when(ledgerService.listClientEntries(CLIENT_ID, query(null, null, 2, 10)))
                .thenReturn(listed(List.of(response()), page(2, 10, 1, 21, false), summary(21, List.of())));

        mockMvc.perform(get(CLIENT_LEDGER_URL).param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(21))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    @DisplayName("accepts a page size of exactly the maximum")
    void acceptsLargestPageSize() throws Exception {
        when(ledgerService.listCompanyEntries(CLIENT_ID, COMPANY_ID, query(null, null, 0, LedgerApi.MAX_PAGE_SIZE)))
                .thenReturn(listed(List.of(), page(0, LedgerApi.MAX_PAGE_SIZE, 0, 0, false), summary(0, null)));

        mockMvc.perform(get(LEDGER_URL).param("size", String.valueOf(LedgerApi.MAX_PAGE_SIZE)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("returns 400 for a negative page")
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get(LEDGER_URL).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(ledgerService, never()).listCompanyEntries(any(), any(), any());
    }

    @Test
    @DisplayName("returns 400 for a page size of zero or above the maximum")
    void rejectsPageSizeOutOfRange() throws Exception {
        mockMvc.perform(get(CLIENT_LEDGER_URL).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(CLIENT_LEDGER_URL).param("size", String.valueOf(LedgerApi.MAX_PAGE_SIZE + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(ledgerService, never()).listClientEntries(any(), any());
    }

    @Test
    @DisplayName("returns 400 when the page is not a number")
    void rejectsMalformedPage() throws Exception {
        mockMvc.perform(get(LEDGER_URL).param("page", "first"))
                .andExpect(status().isBadRequest());

        verify(ledgerService, never()).listCompanyEntries(any(), any(), any());
    }

    @Test
    @DisplayName("returns 200 with a single ledger entry")
    void fetchesEntry() throws Exception {
        LedgerEntryResponse entry = response();
        when(ledgerService.getEntry(CLIENT_ID, COMPANY_ID, entry.getId())).thenReturn(entry);

        mockMvc.perform(get(LEDGER_URL + "/" + entry.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entry.getId().toString()))
                .andExpect(jsonPath("$.companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.totalAmount").value(5605.0));
    }

    @Test
    @DisplayName("returns 404 for an entry not billed to the company")
    void fetchesUnknownEntry() throws Exception {
        UUID missing = UUID.randomUUID();
        when(ledgerService.getEntry(CLIENT_ID, COMPANY_ID, missing))
                .thenThrow(new LedgerEntryNotFoundException(COMPANY_ID, missing));

        mockMvc.perform(get(LEDGER_URL + "/" + missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 200 with the company's summary for the financial year asked for")
    void summarisesCompanyYear() throws Exception {
        when(ledgerService.summarizeCompany(CLIENT_ID, COMPANY_ID, FinancialYears.parse("2026-27")))
                .thenReturn(CompanyLedgerSummaryResponse.builder()
                        .clientId(CLIENT_ID)
                        .companyId(COMPANY_ID)
                        .companyName("Mehta Textiles Pvt Ltd")
                        .financialYear("2026-27")
                        .fromDate(LocalDate.of(2026, 4, 1))
                        .toDate(LocalDate.of(2027, 3, 31))
                        .totalBilled(new BigDecimal("39235.00"))
                        .totalPaymentReceived(new BigDecimal("15000.00"))
                        .pending(new BigDecimal("24235.00"))
                        .totalDiscount(new BigDecimal("1750.00"))
                        .lastPayment(LastPayment.builder()
                                .date(LocalDate.of(2026, 10, 12))
                                .amount(new BigDecimal("10000.00"))
                                .build())
                        .build());

        mockMvc.perform(get(COMPANY_SUMMARY_URL).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.fromDate").value("2026-04-01"))
                .andExpect(jsonPath("$.toDate").value("2027-03-31"))
                .andExpect(jsonPath("$.totalBilled").value(39235.0))
                .andExpect(jsonPath("$.totalPaymentReceived").value(15000.0))
                .andExpect(jsonPath("$.pending").value(24235.0))
                .andExpect(jsonPath("$.totalDiscount").value(1750.0))
                .andExpect(jsonPath("$.lastPayment.date").value("2026-10-12"))
                .andExpect(jsonPath("$.lastPayment.amount").value(10000.0));
    }

    @Test
    @DisplayName("returns 400 when the financial year is left out of the summary request")
    void summaryRequiresFinancialYear() throws Exception {
        mockMvc.perform(get(COMPANY_SUMMARY_URL))
                .andExpect(status().isBadRequest());

        verify(ledgerService, never()).summarizeCompany(any(), any(), any());
    }

    @Test
    @DisplayName("returns 400 when the summary's financial year is not written as yyyy-yy")
    void summaryRejectsMalformedFinancialYear() throws Exception {
        mockMvc.perform(get(COMPANY_SUMMARY_URL).param("financialYear", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(ledgerService, never()).summarizeCompany(any(), any(), any());
    }

    @Test
    @DisplayName("returns 404 when summarising a company the client does not hold")
    void summarisesUnknownCompany() throws Exception {
        when(ledgerService.summarizeCompany(any(), any(), any()))
                .thenThrow(new CompanyNotFoundException(CLIENT_ID, COMPANY_ID));

        mockMvc.perform(get(COMPANY_SUMMARY_URL).param("financialYear", "2026-27"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 200 with the client's summary for the financial year asked for")
    void summarisesClientYear() throws Exception {
        when(ledgerService.summarizeClient(CLIENT_ID, FinancialYears.parse("2026-27")))
                .thenReturn(ClientLedgerSummaryResponse.builder()
                        .clientId(CLIENT_ID)
                        .clientName("Rohan Mehta")
                        .financialYear("2026-27")
                        .fromDate(LocalDate.of(2026, 4, 1))
                        .toDate(LocalDate.of(2027, 3, 31))
                        .totalBilled(new BigDecimal("11210.00"))
                        .totalPaymentReceived(new BigDecimal("2000.00"))
                        .pending(new BigDecimal("9210.00"))
                        .totalDiscount(new BigDecimal("500.00"))
                        .lastPayment(LastPayment.builder()
                                .date(LocalDate.of(2026, 10, 5))
                                .amount(new BigDecimal("2000.00"))
                                .companyId(COMPANY_ID)
                                .companyName("Mehta Textiles Pvt Ltd")
                                .build())
                        .build());

        mockMvc.perform(get(CLIENT_SUMMARY_URL).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.clientName").value("Rohan Mehta"))
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.fromDate").value("2026-04-01"))
                .andExpect(jsonPath("$.toDate").value("2027-03-31"))
                .andExpect(jsonPath("$.totalBilled").value(11210.0))
                .andExpect(jsonPath("$.totalPaymentReceived").value(2000.0))
                .andExpect(jsonPath("$.pending").value(9210.0))
                .andExpect(jsonPath("$.totalDiscount").value(500.0))
                .andExpect(jsonPath("$.lastPayment.date").value("2026-10-05"))
                .andExpect(jsonPath("$.lastPayment.amount").value(2000.0))
                .andExpect(jsonPath("$.lastPayment.companyName").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$.companies").doesNotExist());
    }

    @Test
    @DisplayName("returns 400 when the client summary's financial year is missing or malformed")
    void clientSummaryRequiresFinancialYear() throws Exception {
        mockMvc.perform(get(CLIENT_SUMMARY_URL))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CLIENT_SUMMARY_URL).param("financialYear", "26-27"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(ledgerService, never()).summarizeClient(any(), any());
    }

    @Test
    @DisplayName("returns 404 when summarising an unknown client")
    void summarisesUnknownClient() throws Exception {
        when(ledgerService.summarizeClient(any(), any())).thenThrow(new ClientNotFoundException(CLIENT_ID));

        mockMvc.perform(get(CLIENT_SUMMARY_URL).param("financialYear", "2026-27"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 201 with the payment recorded and a Location of its ledger entry")
    void recordsPayment() throws Exception {
        LedgerEntryResponse payment = paymentResponse();
        PaymentRequest received = PaymentRequest.builder()
                .amountReceived(new BigDecimal("28025.00"))
                .dateReceived(LocalDate.of(2026, 10, 12))
                .method(PaymentMethod.BANK_TRANSFER)
                .reference("HDFC0001234567890")
                .comment("Settles the incorporation invoice")
                .build();
        when(ledgerService.recordPayment(CLIENT_ID, COMPANY_ID, received)).thenReturn(payment);

        mockMvc.perform(post(PAYMENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amountReceived": 28025.00,
                                  "dateReceived": "2026-10-12",
                                  "method": "BANK_TRANSFER",
                                  "reference": "HDFC0001234567890",
                                  "comment": "Settles the incorporation invoice"
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith(LEDGER_URL + "/" + payment.getId())))
                .andExpect(jsonPath("$.entryType").value("PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(-28025.0))
                .andExpect(jsonPath("$.paymentMethod").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.paymentReference").value("HDFC0001234567890"))
                .andExpect(jsonPath("$.serviceFee").doesNotExist())
                .andExpect(jsonPath("$.serviceName").doesNotExist());
    }

    @Test
    @DisplayName("returns 400 listing every payment field that is missing or invalid")
    void rejectsInvalidPayment() throws Exception {
        mockMvc.perform(post(PAYMENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amountReceived": 0, "reference": "%s" }""".formatted("R".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("amountReceived", "dateReceived", "method", "reference")));

        verify(ledgerService, never()).recordPayment(any(), any(), any());
    }

    @Test
    @DisplayName("returns 400 for a payment method that is not offered")
    void rejectsUnknownPaymentMethod() throws Exception {
        mockMvc.perform(post(PAYMENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amountReceived": 500, "dateReceived": "2026-10-12", "method": "CRYPTO" }"""))
                .andExpect(status().isBadRequest());

        verify(ledgerService, never()).recordPayment(any(), any(), any());
    }

    @Test
    @DisplayName("returns 400 for a payment dated after today")
    void rejectsFuturePayment() throws Exception {
        when(ledgerService.recordPayment(any(), any(), any())).thenThrow(
                new FuturePaymentDateException(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 13)));

        mockMvc.perform(post(PAYMENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amountReceived": 500, "dateReceived": "2026-10-20", "method": "UPI" }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("returns 200 with a page of the company's payments and their totals")
    void listsPayments() throws Exception {
        LedgerEntryResponse payment = paymentResponse();
        when(ledgerService.listPayments(CLIENT_ID, COMPANY_ID, query(null, null, 0, 50)))
                .thenReturn(PaymentListResponse.builder()
                        .payments(List.of(payment))
                        .page(page(0, 50, 1, 1, false))
                        .summary(PaymentSummary.builder()
                                .paymentCount(1)
                                .totalReceived(new BigDecimal("28025.00"))
                                .build())
                        .build());

        mockMvc.perform(get(PAYMENTS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.payments[0].entryType").value("PAYMENT"))
                .andExpect(jsonPath("$.payments[0].totalAmount").value(-28025.0))
                .andExpect(jsonPath("$.payments[0].paymentMethod").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.summary.paymentCount").value(1))
                .andExpect(jsonPath("$.summary.totalReceived").value(28025.0));
    }

    @Test
    @DisplayName("passes the financial year, page and order asked for through when listing payments")
    void passesPaymentFiltersThrough() throws Exception {
        LedgerListQuery latestFirst = new LedgerListQuery(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), 1, 10,
                LedgerSortField.DUE_DATE, Sort.Direction.DESC);
        when(ledgerService.listPayments(CLIENT_ID, COMPANY_ID, latestFirst))
                .thenReturn(PaymentListResponse.builder()
                        .payments(List.of())
                        .page(page(1, 10, 0, 3, false))
                        .summary(PaymentSummary.builder().paymentCount(3).totalReceived(new BigDecimal("900.00"))
                                .build())
                        .build());

        mockMvc.perform(get(PAYMENTS_URL)
                        .param("financialYear", "2026-27")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(0))
                .andExpect(jsonPath("$.summary.paymentCount").value(3));
    }

    @Test
    @DisplayName("returns 400 when listing payments with an invalid filter, page or order")
    void rejectsInvalidPaymentListParameters() throws Exception {
        mockMvc.perform(get(PAYMENTS_URL).param("fromDate", "2026-10-01").param("toDate", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(PAYMENTS_URL).param("financialYear", "2026-28"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PAYMENTS_URL).param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PAYMENTS_URL).param("sortBy", "paymentMethod"))
                .andExpect(status().isBadRequest());

        verify(ledgerService, never()).listPayments(any(), any(), any());
    }

    @Test
    @DisplayName("returns 404 when listing the payments of a company the client does not hold")
    void listsPaymentsOfUnknownCompany() throws Exception {
        when(ledgerService.listPayments(any(), any(), any()))
                .thenThrow(new CompanyNotFoundException(CLIENT_ID, COMPANY_ID));

        mockMvc.perform(get(PAYMENTS_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 200 with the updated payment, passing its new details through")
    void updatesPayment() throws Exception {
        LedgerEntryResponse payment = paymentResponse();
        PaymentRequest corrected = PaymentRequest.builder()
                .amountReceived(new BigDecimal("28000.00"))
                .dateReceived(LocalDate.of(2026, 10, 11))
                .method(PaymentMethod.UPI)
                .reference("UPI-REF-1")
                .build();
        when(ledgerService.updatePayment(CLIENT_ID, COMPANY_ID, payment.getId(), corrected)).thenReturn(payment);

        mockMvc.perform(put(PAYMENTS_URL + "/" + payment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amountReceived": 28000.00,
                                  "dateReceived": "2026-10-11",
                                  "method": "UPI",
                                  "reference": "UPI-REF-1"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.entryType").value("PAYMENT"));
    }

    @Test
    @DisplayName("returns 400 listing every invalid detail when updating a payment")
    void rejectsInvalidPaymentUpdate() throws Exception {
        mockMvc.perform(put(PAYMENTS_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amountReceived": 0, "comment": "%s" }""".formatted("C".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("amountReceived", "comment", "dateReceived", "method")));

        verify(ledgerService, never()).updatePayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("returns 409 when updating a service charge as though it were a payment")
    void refusesToUpdateChargeAsPayment() throws Exception {
        UUID chargeId = UUID.randomUUID();
        when(ledgerService.updatePayment(any(), any(), any(), any())).thenThrow(new NotAPaymentException(chargeId));

        mockMvc.perform(put(PAYMENTS_URL + "/" + chargeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amountReceived": 500, "dateReceived": "2026-10-12", "method": "CASH" }"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("returns 404 when updating a payment not recorded for the company")
    void updatesUnknownPayment() throws Exception {
        UUID missing = UUID.randomUUID();
        when(ledgerService.updatePayment(any(), any(), any(), any()))
                .thenThrow(new LedgerEntryNotFoundException(COMPANY_ID, missing));

        mockMvc.perform(put(PAYMENTS_URL + "/" + missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amountReceived": 500, "dateReceived": "2026-10-12", "method": "CASH" }"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 409 when correcting the rates of a payment")
    void refusesToCorrectPayment() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(ledgerService.updateEntry(any(), any(), any(), any()))
                .thenThrow(new PaymentNotCorrectableException(paymentId));

        mockMvc.perform(put(LEDGER_URL + "/" + paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceFee": 500, "discountPercent": 0, "gstRatePercent": 18 }"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("returns 200 with the corrected entry, passing the rates through")
    void correctsEntry() throws Exception {
        LedgerEntryResponse entry = response();
        LedgerEntryUpdateRequest rates = LedgerEntryUpdateRequest.builder()
                .serviceFee(new BigDecimal("6000.00"))
                .discountPercent(new BigDecimal("10"))
                .gstRatePercent(new BigDecimal("12.5"))
                .build();
        when(ledgerService.updateEntry(CLIENT_ID, COMPANY_ID, entry.getId(), rates)).thenReturn(entry);

        mockMvc.perform(put(LEDGER_URL + "/" + entry.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceFee": 6000.00, "discountPercent": 10, "gstRatePercent": 12.5 }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entry.getId().toString()));
    }

    @Test
    @DisplayName("returns 400 listing every rate that is missing or out of range")
    void rejectsInvalidRates() throws Exception {
        mockMvc.perform(put(LEDGER_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceFee": -1, "discountPercent": 100.5 }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("discountPercent", "gstRatePercent", "serviceFee")));

        verify(ledgerService, never()).updateEntry(any(), any(), any(), any());
    }

    @Test
    @DisplayName("returns 400 for a rate with more than two decimal places")
    void rejectsOverPreciseRate() throws Exception {
        mockMvc.perform(put(LEDGER_URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceFee": 6000.001, "discountPercent": 10, "gstRatePercent": 18 }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("serviceFee"));

        verify(ledgerService, never()).updateEntry(any(), any(), any(), any());
    }

    @Test
    @DisplayName("returns 404 when correcting an entry not billed to the company")
    void correctsUnknownEntry() throws Exception {
        UUID missing = UUID.randomUUID();
        when(ledgerService.updateEntry(any(), any(), any(), any()))
                .thenThrow(new LedgerEntryNotFoundException(COMPANY_ID, missing));

        mockMvc.perform(put(LEDGER_URL + "/" + missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "serviceFee": 6000, "discountPercent": 10, "gstRatePercent": 18 }"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 204 once an entry is deleted")
    void deletesEntry() throws Exception {
        UUID entryId = UUID.randomUUID();

        mockMvc.perform(delete(LEDGER_URL + "/" + entryId))
                .andExpect(status().isNoContent());

        verify(ledgerService).deleteEntry(CLIENT_ID, COMPANY_ID, entryId);
    }

    @Test
    @DisplayName("returns 404 when deleting an entry not billed to the company")
    void deletesUnknownEntry() throws Exception {
        UUID missing = UUID.randomUUID();
        doThrow(new LedgerEntryNotFoundException(COMPANY_ID, missing))
                .when(ledgerService).deleteEntry(CLIENT_ID, COMPANY_ID, missing);

        mockMvc.perform(delete(LEDGER_URL + "/" + missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 400 when the entry id is not a UUID")
    void fetchesMalformedEntryId() throws Exception {
        mockMvc.perform(get(LEDGER_URL + "/not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(ledgerService, never()).getEntry(any(), any(), any());
    }

    @Test
    @DisplayName("passes the sort field and direction asked for through to the service")
    void passesSortThrough() throws Exception {
        LedgerListQuery byLargestTotal = new LedgerListQuery(null, null, 0, 50, LedgerSortField.TOTAL_AMOUNT,
                Sort.Direction.DESC);
        when(ledgerService.listCompanyEntries(CLIENT_ID, COMPANY_ID, byLargestTotal))
                .thenReturn(listed(List.of(response()), page(0, 50, 1, 1, false), summary(1, null)));

        mockMvc.perform(get(LEDGER_URL).param("sortBy", "totalAmount").param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1));
    }

    @Test
    @DisplayName("accepts the sort direction in either case")
    void acceptsSortDirectionInEitherCase() throws Exception {
        LedgerListQuery byLatestEntry = new LedgerListQuery(null, null, 0, 50, LedgerSortField.ENTRY_DATE,
                Sort.Direction.DESC);
        when(ledgerService.listClientEntries(CLIENT_ID, byLatestEntry))
                .thenReturn(listed(List.of(), page(0, 50, 0, 0, false), summary(0, List.of())));

        mockMvc.perform(get(CLIENT_LEDGER_URL).param("sortBy", "entryDate").param("sortDirection", "DESC"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("returns 400 when sorting by a field that cannot be sorted on")
    void rejectsUnknownSortField() throws Exception {
        mockMvc.perform(get(LEDGER_URL).param("sortBy", "clientId"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("dueDate, entryDate, serviceName, totalAmount")));

        verify(ledgerService, never()).listCompanyEntries(any(), any(), any());
    }

    @Test
    @DisplayName("returns 400 for a sort direction other than asc or desc")
    void rejectsUnknownSortDirection() throws Exception {
        mockMvc.perform(get(CLIENT_LEDGER_URL).param("sortDirection", "up"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(ledgerService, never()).listClientEntries(any(), any());
    }

    @Test
    @DisplayName("passes the due date range through when listing a company's entries")
    void listsCompanyEntriesInRange() throws Exception {
        when(ledgerService.listCompanyEntries(CLIENT_ID, COMPANY_ID,
                query(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), 0, 50)))
                .thenReturn(listed(List.of(response()), page(0, 50, 1, 1, false), summary(1, null)));

        mockMvc.perform(get(LEDGER_URL).param("fromDate", "2026-04-01").param("toDate", "2027-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1));
    }

    @Test
    @DisplayName("lists a financial year as its 1 April to 31 March range of due dates")
    void listsFinancialYear() throws Exception {
        when(ledgerService.listCompanyEntries(CLIENT_ID, COMPANY_ID,
                query(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), 0, 50)))
                .thenReturn(listed(List.of(response()), page(0, 50, 1, 1, false), summary(1, null)));

        mockMvc.perform(get(LEDGER_URL).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1));
    }

    @Test
    @DisplayName("keeps only the overlap of a financial year and a date range")
    void listsOverlapOfFinancialYearAndRange() throws Exception {
        when(ledgerService.listClientEntries(CLIENT_ID,
                query(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 3, 31), 0, 50)))
                .thenReturn(listed(List.of(response()), page(0, 50, 1, 1, false), summary(1, List.of())));

        mockMvc.perform(get(CLIENT_LEDGER_URL)
                        .param("financialYear", "2026-27")
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2027-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1));
    }

    @Test
    @DisplayName("passes a range that cannot match when a financial year and date range do not overlap")
    void listsDisjointFinancialYearAndRange() throws Exception {
        when(ledgerService.listCompanyEntries(CLIENT_ID, COMPANY_ID,
                query(LocalDate.of(2027, 4, 1), LocalDate.of(2027, 3, 31), 0, 50)))
                .thenReturn(listed(List.of(), page(0, 50, 0, 0, false), summary(0, null)));

        mockMvc.perform(get(LEDGER_URL).param("financialYear", "2026-27").param("fromDate", "2027-04-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0))
                .andExpect(jsonPath("$.summary.entryCount").value(0))
                .andExpect(jsonPath("$.summary.totalAmount").value(0.0))
                .andExpect(jsonPath("$.summary.byFinancialYear").isEmpty());
    }

    @Test
    @DisplayName("returns 400 when the financial year is not written as yyyy-yy with consecutive years")
    void rejectsMalformedFinancialYear() throws Exception {
        mockMvc.perform(get(CLIENT_LEDGER_URL).param("financialYear", "2026-28"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(ledgerService, never()).listClientEntries(any(), any());
    }

    @Test
    @DisplayName("returns 400 when the range ends before it starts")
    void rejectsReversedRange() throws Exception {
        mockMvc.perform(get(LEDGER_URL).param("fromDate", "2026-10-01").param("toDate", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(ledgerService, never()).listCompanyEntries(any(), any(), any());
    }

    @Test
    @DisplayName("returns 400 when a date is not an ISO date")
    void rejectsMalformedDate() throws Exception {
        mockMvc.perform(get(CLIENT_LEDGER_URL).param("fromDate", "01/10/2026"))
                .andExpect(status().isBadRequest());

        verify(ledgerService, never()).listClientEntries(any(), any());
    }

    @Test
    @DisplayName("returns 404 when listing a company the client does not hold")
    void listsCompanyEntriesOfUnknownCompany() throws Exception {
        when(ledgerService.listCompanyEntries(CLIENT_ID, COMPANY_ID, query(null, null, 0, 50)))
                .thenThrow(new CompanyNotFoundException(CLIENT_ID, COMPANY_ID));

        mockMvc.perform(get(LEDGER_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 200 with a page of the entries of every company of a client, with totals by company")
    void listsClientEntries() throws Exception {
        CompanyTotals textiles = CompanyTotals.builder()
                .companyId(COMPANY_ID)
                .companyName("Mehta Textiles Pvt Ltd")
                .entryCount(2)
                .totalServiceFee(new BigDecimal("10000.00"))
                .totalDiscountAmount(new BigDecimal("500.00"))
                .totalTaxableAmount(new BigDecimal("9500.00"))
                .totalGstAmount(new BigDecimal("1710.00"))
                .totalAmount(new BigDecimal("11210.00"))
                .build();
        when(ledgerService.listClientEntries(CLIENT_ID, query(LocalDate.of(2026, 10, 1), null, 0, 50)))
                .thenReturn(listed(List.of(response(), response()), page(0, 50, 2, 2, false),
                        summary(2, List.of(textiles))));

        mockMvc.perform(get(CLIENT_LEDGER_URL).param("fromDate", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.summary.entryCount").value(2))
                .andExpect(jsonPath("$.summary.byCompany.length()").value(1))
                .andExpect(jsonPath("$.summary.byCompany[0].companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.summary.byCompany[0].companyName").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$.summary.byCompany[0].entryCount").value(2))
                .andExpect(jsonPath("$.summary.byCompany[0].totalAmount").value(11210.0));
    }

    @Test
    @DisplayName("returns a client's years, each with its figures, status and companies")
    void givesClientLedgerStatus() throws Exception {
        when(ledgerService.clientLedgerStatus(CLIENT_ID)).thenReturn(ClientLedgerStatusResponse.builder()
                .clientId(CLIENT_ID)
                .clientName("Rohan Mehta")
                .clientSince(LocalDate.of(2025, 4, 1))
                .yearCount(2)
                .years(List.of(
                        FinancialYearStatus.builder()
                                .financialYear("2026-27")
                                .current(true)
                                .companyCount(1)
                                .billed(new BigDecimal("184500.00"))
                                .discount(new BigDecimal("6350.00"))
                                .received(new BigDecimal("80000.00"))
                                .closingBalance(new BigDecimal("98150.00"))
                                .status(LedgerYearStatus.PENDING)
                                .companies(List.of(CompanyYearStatus.builder()
                                        .companyId(COMPANY_ID)
                                        .companyName("Mehta Textiles Pvt Ltd")
                                        .closingBalance(new BigDecimal("98150.00"))
                                        .status(LedgerYearStatus.PENDING)
                                        .build()))
                                .build(),
                        FinancialYearStatus.builder()
                                .financialYear("2025-26")
                                .closedOn(LocalDate.of(2026, 3, 31))
                                .closingBalance(new BigDecimal("21000.00"))
                                .status(LedgerYearStatus.CARRIED)
                                .companies(List.of())
                                .build()))
                .build());

        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + CLIENT_ID + "/ledger-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("Rohan Mehta"))
                .andExpect(jsonPath("$.clientSince").value("2025-04-01"))
                .andExpect(jsonPath("$.yearCount").value(2))
                .andExpect(jsonPath("$.years[0].financialYear").value("2026-27"))
                .andExpect(jsonPath("$.years[0].current").value(true))
                .andExpect(jsonPath("$.years[0].closedOn").doesNotExist())
                .andExpect(jsonPath("$.years[0].billed").value(184500.0))
                .andExpect(jsonPath("$.years[0].discount").value(6350.0))
                .andExpect(jsonPath("$.years[0].received").value(80000.0))
                .andExpect(jsonPath("$.years[0].closingBalance").value(98150.0))
                .andExpect(jsonPath("$.years[0].status").value("PENDING"))
                .andExpect(jsonPath("$.years[0].companies[0].companyName").value("Mehta Textiles Pvt Ltd"))
                .andExpect(jsonPath("$.years[1].current").value(false))
                .andExpect(jsonPath("$.years[1].closedOn").value("2026-03-31"))
                .andExpect(jsonPath("$.years[1].status").value("CARRIED"));
    }

    @Test
    @DisplayName("returns 404 for the ledger status of an unknown client")
    void givesLedgerStatusOfUnknownClient() throws Exception {
        when(ledgerService.clientLedgerStatus(CLIENT_ID)).thenThrow(new ClientNotFoundException(CLIENT_ID));

        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + CLIENT_ID + "/ledger-status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 404 when listing the entries of an unknown client")
    void listsClientEntriesOfUnknownClient() throws Exception {
        when(ledgerService.listClientEntries(CLIENT_ID, query(null, null, 0, 50)))
                .thenThrow(new ClientNotFoundException(CLIENT_ID));

        mockMvc.perform(get(CLIENT_LEDGER_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("downloads a client's year as an .xlsx attachment named after them")
    void exportsClientYear() throws Exception {
        byte[] workbook = {0x50, 0x4b, 0x03, 0x04};
        when(ledgerService.exportClient(CLIENT_ID, FinancialYears.parse("2026-27")))
                .thenReturn(new LedgerExport("Rohan_Mehta_Ledger_FY2026-27.xlsx", workbook));

        mockMvc.perform(get(CLIENT_EXPORT_URL).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(LedgerExport.XLSX_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Rohan_Mehta_Ledger_FY2026-27.xlsx\""))
                .andExpect(content().bytes(workbook));
    }

    @Test
    @DisplayName("returns 400 when exporting without a financial year, or with a malformed one")
    void rejectsExportWithoutValidFinancialYear() throws Exception {
        mockMvc.perform(get(CLIENT_EXPORT_URL))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CLIENT_EXPORT_URL).param("financialYear", "2026-28"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(ledgerService, never()).exportClient(any(), any());
    }

    @Test
    @DisplayName("returns 404 as JSON when exporting an unknown client")
    void exportsUnknownClient() throws Exception {
        when(ledgerService.exportClient(CLIENT_ID, FinancialYears.parse("2026-27")))
                .thenThrow(new ClientNotFoundException(CLIENT_ID));

        mockMvc.perform(get(CLIENT_EXPORT_URL).param("financialYear", "2026-27"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("downloads a company's year as an .xlsx attachment named after it")
    void exportsCompanyYear() throws Exception {
        byte[] workbook = {0x50, 0x4b, 0x03, 0x04};
        when(ledgerService.exportCompany(CLIENT_ID, COMPANY_ID, FinancialYears.parse("2026-27")))
                .thenReturn(new LedgerExport("Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.xlsx", workbook));

        mockMvc.perform(get(COMPANY_EXPORT_URL).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(LedgerExport.XLSX_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.xlsx\""))
                .andExpect(content().bytes(workbook));
    }

    @Test
    @DisplayName("returns 400 when exporting a company without a valid financial year or under a malformed id")
    void rejectsInvalidCompanyExport() throws Exception {
        mockMvc.perform(get(COMPANY_EXPORT_URL))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(COMPANY_EXPORT_URL).param("financialYear", "26-27"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(ApiPaths.V1 + "/clients/" + CLIENT_ID + "/companies/not-a-uuid/ledger-export")
                        .param("financialYear", "2026-27"))
                .andExpect(status().isBadRequest());

        verify(ledgerService, never()).exportCompany(any(), any(), any());
    }

    @Test
    @DisplayName("returns 404 as JSON when exporting a company the client does not hold")
    void exportsUnknownCompany() throws Exception {
        when(ledgerService.exportCompany(CLIENT_ID, COMPANY_ID, FinancialYears.parse("2026-27")))
                .thenThrow(new CompanyNotFoundException(CLIENT_ID, COMPANY_ID));

        mockMvc.perform(get(COMPANY_EXPORT_URL).param("financialYear", "2026-27"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 400 when listing a client's entries under a malformed id")
    void listsClientEntriesOfMalformedId() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/clients/not-a-uuid/ledger-entries"))
                .andExpect(status().isBadRequest());

        verify(ledgerService, never()).listClientEntries(any(), any());
    }

    /** A list query in the default order, earliest due first. */
    private static LedgerListQuery query(LocalDate fromDate, LocalDate toDate, int page, int size) {
        return new LedgerListQuery(fromDate, toDate, page, size, LedgerSortField.DUE_DATE, Sort.Direction.ASC);
    }

    private LedgerListResponse listed(List<LedgerEntryResponse> entries, PageMetadata page, LedgerSummary summary) {
        return LedgerListResponse.builder().entries(entries).page(page).summary(summary).build();
    }

    private PageMetadata page(int number, int size, int numberOfElements, long totalElements, boolean hasNext) {
        return PageMetadata.builder()
                .number(number)
                .size(size)
                .numberOfElements(numberOfElements)
                .totalElements(totalElements)
                .totalPages((int) Math.ceil((double) totalElements / size))
                .hasNext(hasNext)
                .build();
    }

    /**
     * Totals of entries like {@link #response()}, all in 2026-27.
     *
     * @param byCompany the company breakdown, or null for a company's own ledger
     */
    private LedgerSummary summary(long entryCount, List<CompanyTotals> byCompany) {
        BigDecimal count = BigDecimal.valueOf(entryCount);
        List<FinancialYearTotals> byFinancialYear = entryCount == 0 ? List.of() : List.of(
                FinancialYearTotals.builder()
                        .financialYear("2026-27")
                        .entryCount(entryCount)
                        .totalServiceFee(new BigDecimal("5000.00").multiply(count))
                        .totalDiscountAmount(new BigDecimal("250.00").multiply(count))
                        .totalTaxableAmount(new BigDecimal("4750.00").multiply(count))
                        .totalGstAmount(new BigDecimal("855.00").multiply(count))
                        .totalAmount(new BigDecimal("5605.00").multiply(count))
                        .build());
        return LedgerSummary.builder()
                .entryCount(entryCount)
                .totalServiceFee(new BigDecimal("5000.00").multiply(count))
                .totalDiscountAmount(new BigDecimal("250.00").multiply(count))
                .totalTaxableAmount(new BigDecimal("4750.00").multiply(count))
                .totalGstAmount(new BigDecimal("855.00").multiply(count))
                .totalAmount(new BigDecimal("5605.00").multiply(count))
                .byFinancialYear(byFinancialYear)
                .byCompany(byCompany)
                .build();
    }

    private LedgerEntryResponse paymentResponse() {
        return LedgerEntryResponse.builder()
                .id(UUID.randomUUID())
                .clientId(CLIENT_ID)
                .companyId(COMPANY_ID)
                .entryType(LedgerEntryType.PAYMENT)
                .dueDate(LocalDate.of(2026, 10, 12))
                .financialYear("2026-27")
                .entryDate(LocalDate.of(2026, 10, 13))
                .totalAmount(new BigDecimal("-28025.00"))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentReference("HDFC0001234567890")
                .comment("Settles the incorporation invoice")
                .createdAt(Instant.now())
                .build();
    }

    private LedgerEntryResponse response() {
        return LedgerEntryResponse.builder()
                .id(UUID.randomUUID())
                .clientId(CLIENT_ID)
                .companyId(COMPANY_ID)
                .entryType(LedgerEntryType.SERVICE_CHARGE)
                .companyServiceId(UUID.randomUUID())
                .serviceId(SERVICE_ID)
                .serviceName("GST Monthly Return Filing")
                .serviceType(BillingFrequency.MONTHLY)
                .dueDate(LocalDate.of(2026, 10, 7))
                .financialYear("2026-27")
                .entryDate(LocalDate.of(2026, 10, 13))
                .serviceFee(new BigDecimal("5000.00"))
                .discountPercent(new BigDecimal("5.00"))
                .discountAmount(new BigDecimal("250.00"))
                .taxableAmount(new BigDecimal("4750.00"))
                .gstRatePercent(new BigDecimal("18.00"))
                .gstAmount(new BigDecimal("855.00"))
                .totalAmount(new BigDecimal("5605.00"))
                .createdAt(Instant.now())
                .build();
    }
}
