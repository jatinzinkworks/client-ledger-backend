package com.psc.cl.overview.controller;

import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.ledger.service.FinancialYears;
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
import com.psc.cl.overview.exception.InvalidLimitException;
import com.psc.cl.overview.service.OverviewService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single entry point for every Overview endpoint.
 */
@RestController
@RequiredArgsConstructor
public class OverviewController implements OverviewApi {

    private static final Logger log = LoggerFactory.getLogger(OverviewController.class);

    private final OverviewService overviewService;

    @Override
    public ResponseEntity<OverviewResponse> overview(String financialYear) {
        log.debug("Received request for the overview of FY {}", financialYear);
        FinancialYear year = FinancialYears.parse(financialYear);
        return ResponseEntity.ok(overviewService.overview(year));
    }

    @Override
    public ResponseEntity<TopPendingClientsResponse> topPendingClients(String financialYear, int limit) {
        log.debug("Received request for the top {} clients by pending for FY {}", limit,
                financialYear == null ? "all" : financialYear);
        FinancialYear year = financialYear == null ? null : FinancialYears.parse(financialYear);
        requireLimit(limit, OverviewService.MAX_TOP_CLIENTS);
        return ResponseEntity.ok(overviewService.topPendingClients(year, limit));
    }

    @Override
    public ResponseEntity<TopPendingManagersResponse> topPendingManagers(String financialYear, int limit) {
        log.debug("Received request for the top {} managers by pending for FY {}", limit,
                financialYear == null ? "all" : financialYear);
        FinancialYear year = financialYear == null ? null : FinancialYears.parse(financialYear);
        requireLimit(limit, OverviewService.MAX_TOP_MANAGERS);
        return ResponseEntity.ok(overviewService.topPendingManagers(year, limit));
    }

    @Override
    public ResponseEntity<TopBilledClientsResponse> topBilledClients(String financialYear, int limit) {
        log.debug("Received request for the top {} clients by billed for FY {}", limit,
                financialYear == null ? "all" : financialYear);
        FinancialYear year = financialYear == null ? null : FinancialYears.parse(financialYear);
        requireLimit(limit, OverviewService.MAX_TOP_CLIENTS);
        return ResponseEntity.ok(overviewService.topBilledClients(year, limit));
    }

    @Override
    public ResponseEntity<TopBilledManagersResponse> topBilledManagers(String financialYear, int limit) {
        log.debug("Received request for the top {} managers by billed for FY {}", limit,
                financialYear == null ? "all" : financialYear);
        FinancialYear year = financialYear == null ? null : FinancialYears.parse(financialYear);
        requireLimit(limit, OverviewService.MAX_TOP_MANAGERS);
        return ResponseEntity.ok(overviewService.topBilledManagers(year, limit));
    }

    @Override
    public ResponseEntity<TopPendingCompaniesResponse> topPendingCompanies(String financialYear, int limit) {
        log.debug("Received request for the top {} companies by pending for FY {}", limit,
                financialYear == null ? "all" : financialYear);
        FinancialYear year = financialYear == null ? null : FinancialYears.parse(financialYear);
        requireLimit(limit, OverviewService.MAX_TOP_COMPANIES);
        return ResponseEntity.ok(overviewService.topPendingCompanies(year, limit));
    }

    @Override
    public ResponseEntity<TopBilledCompaniesResponse> topBilledCompanies(String financialYear, int limit) {
        log.debug("Received request for the top {} companies by billed for FY {}", limit,
                financialYear == null ? "all" : financialYear);
        FinancialYear year = financialYear == null ? null : FinancialYears.parse(financialYear);
        requireLimit(limit, OverviewService.MAX_TOP_COMPANIES);
        return ResponseEntity.ok(overviewService.topBilledCompanies(year, limit));
    }

    @Override
    public ResponseEntity<RecentPaymentsResponse> recentPayments(int limit) {
        log.debug("Received request for the latest {} payments", limit);
        requireLimit(limit, OverviewService.MAX_RECENT_PAYMENTS);
        return ResponseEntity.ok(overviewService.recentPayments(limit));
    }

    @Override
    public ResponseEntity<AgeingResponse> ageing() {
        log.debug("Received request for the receivables ageing");
        return ResponseEntity.ok(overviewService.ageing());
    }

    @Override
    public ResponseEntity<MonthlyTrendResponse> monthlyTrend(String financialYear) {
        log.debug("Received request for the monthly trend of FY {}", financialYear);
        FinancialYear year = FinancialYears.parse(financialYear);
        return ResponseEntity.ok(overviewService.monthlyTrend(year));
    }

    @Override
    public ResponseEntity<BilledByCategoryResponse> billedByCategory(String financialYear, int topServices) {
        log.debug("Received request for the billing by category of FY {}, top {} services", financialYear,
                topServices);
        FinancialYear year = FinancialYears.parse(financialYear);
        if (topServices < 0 || topServices > OverviewService.MAX_TOP_SERVICES_PER_CATEGORY) {
            throw new InvalidLimitException("topServices", topServices, 0,
                    OverviewService.MAX_TOP_SERVICES_PER_CATEGORY);
        }
        return ResponseEntity.ok(overviewService.billedByCategory(year, topServices));
    }

    private static void requireLimit(int limit, int max) {
        if (limit < 1 || limit > max) {
            throw new InvalidLimitException(limit, max);
        }
    }
}
