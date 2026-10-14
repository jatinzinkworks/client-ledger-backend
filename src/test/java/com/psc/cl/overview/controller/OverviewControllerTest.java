package com.psc.cl.overview.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psc.cl.ledger.model.PaymentMethod;
import com.psc.cl.ledger.service.FinancialYears;
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
import com.psc.cl.overview.service.OverviewService;
import com.psc.cl.servicecatalog.model.ServiceCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@WebMvcTest(OverviewController.class)
class OverviewControllerTest {

    private static final String OVERVIEW_URL = OverviewApi.BASE_PATH;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OverviewService overviewService;

    @Test
    @DisplayName("returns the overview for the financial year asked for")
    void givesOverview() throws Exception {
        when(overviewService.overview(FinancialYears.parse("2026-27"))).thenReturn(OverviewResponse.builder()
                .financialYear("2026-27")
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2027, 3, 31))
                .totalBilled(new BigDecimal("1845000.00"))
                .totalPaymentReceived(new BigDecimal("1571500.00"))
                .pending(new BigDecimal("273500.00"))
                .totalDiscount(new BigDecimal("63500.00"))
                .previousToDate(PeriodTotals.builder()
                        .financialYear("2025-26")
                        .fromDate(LocalDate.of(2025, 4, 1))
                        .toDate(LocalDate.of(2025, 10, 14))
                        .totalBilled(new BigDecimal("1647000.00"))
                        .totalPaymentReceived(new BigDecimal("1402000.00"))
                        .pending(new BigDecimal("245000.00"))
                        .totalDiscount(new BigDecimal("58000.00"))
                        .build())
                .totalClients(42)
                .totalCompanies(97)
                .totalServices(268)
                .build());

        mockMvc.perform(get(OVERVIEW_URL).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.fromDate").value("2026-04-01"))
                .andExpect(jsonPath("$.toDate").value("2027-03-31"))
                .andExpect(jsonPath("$.totalBilled").value(1845000.0))
                .andExpect(jsonPath("$.totalPaymentReceived").value(1571500.0))
                .andExpect(jsonPath("$.pending").value(273500.0))
                .andExpect(jsonPath("$.totalDiscount").value(63500.0))
                .andExpect(jsonPath("$.totalClients").value(42))
                .andExpect(jsonPath("$.totalCompanies").value(97))
                .andExpect(jsonPath("$.totalServices").value(268))
                .andExpect(jsonPath("$.previousToDate.financialYear").value("2025-26"))
                .andExpect(jsonPath("$.previousToDate.toDate").value("2025-10-14"))
                .andExpect(jsonPath("$.previousToDate.totalBilled").value(1647000.0))
                .andExpect(jsonPath("$.previousToDate.totalPaymentReceived").value(1402000.0))
                .andExpect(jsonPath("$.previousToDate.pending").value(245000.0))
                .andExpect(jsonPath("$.previousToDate.totalDiscount").value(58000.0));
    }

    @Test
    @DisplayName("ranks the top ten clients across every year when nothing is asked for")
    void ranksTopPendingClientsByDefault() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(overviewService.topPendingClients(null, 10)).thenReturn(TopPendingClientsResponse.builder()
                .limit(10)
                .clients(List.of(PendingClient.builder()
                        .rank(1)
                        .clientId(clientId)
                        .clientName("Rohan Mehta")
                        .managerName("Pranay Singhal")
                        .totalBilled(new BigDecimal("39235.00"))
                        .totalPaymentReceived(new BigDecimal("19605.00"))
                        .pending(new BigDecimal("19630.00"))
                        .build()))
                .build());

        mockMvc.perform(get(OVERVIEW_URL + "/top-pending-clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").doesNotExist())
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.clients[0].rank").value(1))
                .andExpect(jsonPath("$.clients[0].clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.clients[0].clientName").value("Rohan Mehta"))
                .andExpect(jsonPath("$.clients[0].managerName").value("Pranay Singhal"))
                .andExpect(jsonPath("$.clients[0].pending").value(19630.0));
    }

    @Test
    @DisplayName("passes the financial year and limit through to the ranking")
    void ranksTopPendingClientsForYear() throws Exception {
        when(overviewService.topPendingClients(FinancialYears.parse("2026-27"), 5))
                .thenReturn(TopPendingClientsResponse.builder().financialYear("2026-27").limit(5)
                        .clients(List.of()).build());

        mockMvc.perform(get(OVERVIEW_URL + "/top-pending-clients")
                        .param("financialYear", "2026-27")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.clients").isEmpty());
    }

    @Test
    @DisplayName("returns 400 for a ranking with a limit out of range or a malformed financial year")
    void rejectsInvalidRankingParameters() throws Exception {
        String url = OVERVIEW_URL + "/top-pending-clients";
        mockMvc.perform(get(url).param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("limit", "ten"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(url).param("financialYear", "26-27"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(overviewService, never()).topPendingClients(any(), anyInt());
    }

    @Test
    @DisplayName("ranks the top ten managers across every year when nothing is asked for")
    void ranksTopPendingManagersByDefault() throws Exception {
        UUID managerId = UUID.randomUUID();
        when(overviewService.topPendingManagers(null, 10)).thenReturn(TopPendingManagersResponse.builder()
                .limit(10)
                .managers(List.of(PendingManager.builder()
                        .rank(1)
                        .managerId(managerId)
                        .managerName("Pranay Singhal")
                        .clientCount(12)
                        .totalBilled(new BigDecimal("39235.00"))
                        .totalPaymentReceived(new BigDecimal("19605.00"))
                        .pending(new BigDecimal("19630.00"))
                        .build()))
                .build());

        mockMvc.perform(get(OVERVIEW_URL + "/top-pending-managers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").doesNotExist())
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.managers[0].rank").value(1))
                .andExpect(jsonPath("$.managers[0].managerId").value(managerId.toString()))
                .andExpect(jsonPath("$.managers[0].managerName").value("Pranay Singhal"))
                .andExpect(jsonPath("$.managers[0].clientCount").value(12))
                .andExpect(jsonPath("$.managers[0].pending").value(19630.0));
    }

    @Test
    @DisplayName("passes the financial year and limit through to the managers' ranking")
    void ranksTopPendingManagersForYear() throws Exception {
        when(overviewService.topPendingManagers(FinancialYears.parse("2026-27"), 3))
                .thenReturn(TopPendingManagersResponse.builder().financialYear("2026-27").limit(3)
                        .managers(List.of()).build());

        mockMvc.perform(get(OVERVIEW_URL + "/top-pending-managers")
                        .param("financialYear", "2026-27")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.managers").isEmpty());
    }

    @Test
    @DisplayName("returns 400 for a managers' ranking with a limit out of range or a malformed financial year")
    void rejectsInvalidManagerRankingParameters() throws Exception {
        String url = OVERVIEW_URL + "/top-pending-managers";
        mockMvc.perform(get(url).param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("financialYear", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(overviewService, never()).topPendingManagers(any(), anyInt());
    }

    @Test
    @DisplayName("ranks the top ten clients by billed across every year when nothing is asked for")
    void ranksTopBilledClientsByDefault() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(overviewService.topBilledClients(null, 10)).thenReturn(TopBilledClientsResponse.builder()
                .limit(10)
                .clients(List.of(BilledClient.builder()
                        .rank(1)
                        .clientId(clientId)
                        .clientName("Rohan Mehta")
                        .managerName("Pranay Singhal")
                        .totalBilled(new BigDecimal("39235.00"))
                        .totalPaymentReceived(new BigDecimal("19605.00"))
                        .pending(new BigDecimal("19630.00"))
                        .build()))
                .build());

        mockMvc.perform(get(OVERVIEW_URL + "/top-billed-clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").doesNotExist())
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.clients[0].rank").value(1))
                .andExpect(jsonPath("$.clients[0].clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.clients[0].clientName").value("Rohan Mehta"))
                .andExpect(jsonPath("$.clients[0].managerName").value("Pranay Singhal"))
                .andExpect(jsonPath("$.clients[0].totalBilled").value(39235.0))
                .andExpect(jsonPath("$.clients[0].pending").value(19630.0));
    }

    @Test
    @DisplayName("passes the financial year and limit through to the clients' billed ranking, rejecting bad ones")
    void ranksTopBilledClientsForYear() throws Exception {
        when(overviewService.topBilledClients(FinancialYears.parse("2026-27"), 3))
                .thenReturn(TopBilledClientsResponse.builder().financialYear("2026-27").limit(3)
                        .clients(List.of()).build());
        String url = OVERVIEW_URL + "/top-billed-clients";

        mockMvc.perform(get(url).param("financialYear", "2026-27").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.clients").isEmpty());
        mockMvc.perform(get(url).param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("financialYear", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("ranks the top ten managers by billed across every year when nothing is asked for")
    void ranksTopBilledManagersByDefault() throws Exception {
        UUID managerId = UUID.randomUUID();
        when(overviewService.topBilledManagers(null, 10)).thenReturn(TopBilledManagersResponse.builder()
                .limit(10)
                .managers(List.of(BilledManager.builder()
                        .rank(1)
                        .managerId(managerId)
                        .managerName("Pranay Singhal")
                        .clientCount(12)
                        .totalBilled(new BigDecimal("39235.00"))
                        .totalPaymentReceived(new BigDecimal("19605.00"))
                        .pending(new BigDecimal("19630.00"))
                        .build()))
                .build());

        mockMvc.perform(get(OVERVIEW_URL + "/top-billed-managers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").doesNotExist())
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.managers[0].rank").value(1))
                .andExpect(jsonPath("$.managers[0].managerId").value(managerId.toString()))
                .andExpect(jsonPath("$.managers[0].managerName").value("Pranay Singhal"))
                .andExpect(jsonPath("$.managers[0].clientCount").value(12))
                .andExpect(jsonPath("$.managers[0].totalBilled").value(39235.0))
                .andExpect(jsonPath("$.managers[0].pending").value(19630.0));
    }

    @Test
    @DisplayName("passes the financial year and limit through to the managers' billed ranking, rejecting bad ones")
    void ranksTopBilledManagersForYear() throws Exception {
        when(overviewService.topBilledManagers(FinancialYears.parse("2026-27"), 3))
                .thenReturn(TopBilledManagersResponse.builder().financialYear("2026-27").limit(3)
                        .managers(List.of()).build());
        String url = OVERVIEW_URL + "/top-billed-managers";

        mockMvc.perform(get(url).param("financialYear", "2026-27").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.managers").isEmpty());
        mockMvc.perform(get(url).param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("financialYear", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("ranks the top ten companies across every year when nothing is asked for")
    void ranksTopPendingCompaniesByDefault() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(overviewService.topPendingCompanies(null, 10)).thenReturn(TopPendingCompaniesResponse.builder()
                .limit(10)
                .companies(List.of(PendingCompany.builder()
                        .rank(1)
                        .companyId(companyId)
                        .companyName("Rohan Mehta HUF")
                        .clientName("Rohan Mehta")
                        .pending(new BigDecimal("18025.00"))
                        .build()))
                .build());

        mockMvc.perform(get(OVERVIEW_URL + "/top-pending-companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").doesNotExist())
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.companies[0].rank").value(1))
                .andExpect(jsonPath("$.companies[0].companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.companies[0].companyName").value("Rohan Mehta HUF"))
                .andExpect(jsonPath("$.companies[0].clientName").value("Rohan Mehta"))
                .andExpect(jsonPath("$.companies[0].pending").value(18025.0));
    }

    @Test
    @DisplayName("passes the financial year and limit through to the companies' ranking, rejecting bad ones")
    void ranksTopPendingCompaniesForYear() throws Exception {
        when(overviewService.topPendingCompanies(FinancialYears.parse("2026-27"), 3))
                .thenReturn(TopPendingCompaniesResponse.builder().financialYear("2026-27").limit(3)
                        .companies(List.of()).build());
        String url = OVERVIEW_URL + "/top-pending-companies";

        mockMvc.perform(get(url).param("financialYear", "2026-27").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.companies").isEmpty());
        mockMvc.perform(get(url).param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("financialYear", "2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ranks the top ten companies by billed across every year when nothing is asked for")
    void ranksTopBilledCompaniesByDefault() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(overviewService.topBilledCompanies(null, 10)).thenReturn(TopBilledCompaniesResponse.builder()
                .limit(10)
                .companies(List.of(BilledCompany.builder()
                        .rank(1)
                        .companyId(companyId)
                        .companyName("Rohan Mehta HUF")
                        .clientName("Rohan Mehta")
                        .totalBilled(new BigDecimal("28025.00"))
                        .totalPaymentReceived(new BigDecimal("10000.00"))
                        .pending(new BigDecimal("18025.00"))
                        .build()))
                .build());

        mockMvc.perform(get(OVERVIEW_URL + "/top-billed-companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").doesNotExist())
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.companies[0].rank").value(1))
                .andExpect(jsonPath("$.companies[0].companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.companies[0].companyName").value("Rohan Mehta HUF"))
                .andExpect(jsonPath("$.companies[0].clientName").value("Rohan Mehta"))
                .andExpect(jsonPath("$.companies[0].totalBilled").value(28025.0))
                .andExpect(jsonPath("$.companies[0].pending").value(18025.0));
    }

    @Test
    @DisplayName("passes the financial year and limit through to the companies' billed ranking, rejecting bad ones")
    void ranksTopBilledCompaniesForYear() throws Exception {
        when(overviewService.topBilledCompanies(FinancialYears.parse("2026-27"), 3))
                .thenReturn(TopBilledCompaniesResponse.builder().financialYear("2026-27").limit(3)
                        .companies(List.of()).build());
        String url = OVERVIEW_URL + "/top-billed-companies";

        mockMvc.perform(get(url).param("financialYear", "2026-27").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.companies").isEmpty());
        mockMvc.perform(get(url).param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("financialYear", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("lists the latest twenty payments when no limit is given")
    void listsRecentPaymentsByDefault() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(overviewService.recentPayments(20)).thenReturn(RecentPaymentsResponse.builder()
                .limit(20)
                .payments(List.of(RecentPayment.builder()
                        .paymentId(paymentId)
                        .dateReceived(LocalDate.of(2026, 10, 10))
                        .amount(new BigDecimal("10000.00"))
                        .method(PaymentMethod.CHEQUE)
                        .companyName("Rohan Mehta HUF")
                        .clientName("Rohan Mehta")
                        .build()))
                .build());

        mockMvc.perform(get(OVERVIEW_URL + "/recent-payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.payments[0].paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.payments[0].dateReceived").value("2026-10-10"))
                .andExpect(jsonPath("$.payments[0].amount").value(10000.0))
                .andExpect(jsonPath("$.payments[0].method").value("CHEQUE"))
                .andExpect(jsonPath("$.payments[0].companyName").value("Rohan Mehta HUF"))
                .andExpect(jsonPath("$.payments[0].clientName").value("Rohan Mehta"))
                .andExpect(jsonPath("$.payments[0].reference").doesNotExist());
    }

    @Test
    @DisplayName("passes the limit through to the latest payments, rejecting one out of range")
    void listsRecentPaymentsWithLimit() throws Exception {
        when(overviewService.recentPayments(5)).thenReturn(RecentPaymentsResponse.builder().limit(5)
                .payments(List.of()).build());
        String url = OVERVIEW_URL + "/recent-payments";

        mockMvc.perform(get(url).param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments").isEmpty());
        mockMvc.perform(get(url).param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(overviewService, never()).recentPayments(0);
        verify(overviewService, never()).recentPayments(101);
    }

    @Test
    @DisplayName("returns 400 when the financial year is missing or malformed")
    void rejectsMissingOrMalformedFinancialYear() throws Exception {
        mockMvc.perform(get(OVERVIEW_URL))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(OVERVIEW_URL).param("financialYear", "2026-28"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(overviewService, never()).overview(any());
    }

    @Test
    @DisplayName("returns the receivables ageing, leaving out the end of the open range")
    void givesAgeing() throws Exception {
        when(overviewService.ageing()).thenReturn(AgeingResponse.builder()
                .asOf(LocalDate.of(2026, 10, 14))
                .overdueAfterDays(60)
                .buckets(List.of(
                        AgeingBucket.builder().label("0-30").fromDays(0).toDays(30)
                                .amount(new BigDecimal("500.00")).companyCount(2).build(),
                        AgeingBucket.builder().label("90+").fromDays(91)
                                .amount(new BigDecimal("1000.00")).companyCount(1).build()))
                .total(new BigDecimal("1500.00"))
                .overdue(new BigDecimal("1000.00"))
                .companyCount(2)
                .build());

        mockMvc.perform(get(OVERVIEW_URL + "/ageing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value("2026-10-14"))
                .andExpect(jsonPath("$.overdueAfterDays").value(60))
                .andExpect(jsonPath("$.buckets[0].label").value("0-30"))
                .andExpect(jsonPath("$.buckets[0].toDays").value(30))
                .andExpect(jsonPath("$.buckets[0].amount").value(500.0))
                .andExpect(jsonPath("$.buckets[0].companyCount").value(2))
                .andExpect(jsonPath("$.buckets[1].fromDays").value(91))
                .andExpect(jsonPath("$.buckets[1].toDays").doesNotExist())
                .andExpect(jsonPath("$.total").value(1500.0))
                .andExpect(jsonPath("$.overdue").value(1000.0));
    }

    @Test
    @DisplayName("returns the monthly trend for the financial year asked for, rejecting a malformed one")
    void givesMonthlyTrend() throws Exception {
        when(overviewService.monthlyTrend(FinancialYears.parse("2026-27"))).thenReturn(MonthlyTrendResponse.builder()
                .financialYear("2026-27")
                .months(List.of(MonthlyTrend.builder()
                        .month("2026-04")
                        .billed(new BigDecimal("11800.00"))
                        .received(new BigDecimal("5000.00"))
                        .discount(new BigDecimal("1000.00"))
                        .build()))
                .build());
        String url = OVERVIEW_URL + "/monthly";

        mockMvc.perform(get(url).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.months[0].month").value("2026-04"))
                .andExpect(jsonPath("$.months[0].billed").value(11800.0))
                .andExpect(jsonPath("$.months[0].received").value(5000.0))
                .andExpect(jsonPath("$.months[0].discount").value(1000.0));
        mockMvc.perform(get(url))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(url).param("financialYear", "2026-28"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("returns the billing by category, listing no services unless asked")
    void givesBilledByCategory() throws Exception {
        UUID serviceId = UUID.randomUUID();
        when(overviewService.billedByCategory(FinancialYears.parse("2026-27"), 0)).thenReturn(
                BilledByCategoryResponse.builder()
                        .financialYear("2026-27")
                        .categories(List.of(CategoryBilling.builder()
                                .category(ServiceCategory.COMPLIANCE)
                                .billed(new BigDecimal("11000.00"))
                                .serviceCount(2)
                                .build()))
                        .build());
        when(overviewService.billedByCategory(FinancialYears.parse("2026-27"), 3)).thenReturn(
                BilledByCategoryResponse.builder()
                        .financialYear("2026-27")
                        .categories(List.of(CategoryBilling.builder()
                                .category(ServiceCategory.COMPLIANCE)
                                .billed(new BigDecimal("11000.00"))
                                .serviceCount(2)
                                .topServices(List.of(CategoryService.builder()
                                        .catalogServiceId(serviceId)
                                        .serviceName("GST Return Filing")
                                        .billed(new BigDecimal("8000.00"))
                                        .build()))
                                .build()))
                        .build());
        String url = OVERVIEW_URL + "/billed-by-category";

        mockMvc.perform(get(url).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].category").value("COMPLIANCE"))
                .andExpect(jsonPath("$.categories[0].billed").value(11000.0))
                .andExpect(jsonPath("$.categories[0].serviceCount").value(2))
                .andExpect(jsonPath("$.categories[0].topServices").doesNotExist());
        mockMvc.perform(get(url).param("financialYear", "2026-27").param("topServices", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].topServices[0].catalogServiceId").value(serviceId.toString()))
                .andExpect(jsonPath("$.categories[0].topServices[0].serviceName").value("GST Return Filing"))
                .andExpect(jsonPath("$.categories[0].topServices[0].billed").value(8000.0));
    }

    @Test
    @DisplayName("rejects billing by category with a missing year or top services out of range")
    void rejectsInvalidBilledByCategory() throws Exception {
        String url = OVERVIEW_URL + "/billed-by-category";

        mockMvc.perform(get(url))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(url).param("financialYear", "2026-27").param("topServices", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(url).param("financialYear", "2026-27").param("topServices", "11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(overviewService, never()).billedByCategory(any(), anyInt());
    }
}
