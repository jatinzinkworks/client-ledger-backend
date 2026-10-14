package com.psc.cl.ledger.controller;

import com.psc.cl.ledger.dto.BulkPostingResponse;
import com.psc.cl.ledger.dto.ClientLedgerStatusResponse;
import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LedgerEntryResponse;
import com.psc.cl.ledger.dto.LedgerEntryUpdateRequest;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.ledger.dto.LedgerListQuery;
import com.psc.cl.ledger.dto.LedgerListResponse;
import com.psc.cl.ledger.dto.LedgerSortField;
import com.psc.cl.ledger.dto.PaymentListResponse;
import com.psc.cl.ledger.dto.PaymentRequest;
import com.psc.cl.ledger.exception.InvalidDateRangeException;
import com.psc.cl.ledger.exception.InvalidPageException;
import com.psc.cl.ledger.exception.InvalidSortException;
import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.ledger.service.BulkPostingService;
import com.psc.cl.ledger.service.FinancialYears;
import com.psc.cl.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Single entry point for the ledger of a company of a client.
 */
@RestController
@RequiredArgsConstructor
public class LedgerController implements LedgerApi {

    private static final Logger log = LoggerFactory.getLogger(LedgerController.class);

    private final LedgerService ledgerService;

    private final BulkPostingService bulkPostingService;

    @Override
    public ResponseEntity<List<LedgerEntryResponse>> postDueServices(UUID clientId, UUID companyId) {
        log.debug("Received request to post due services of company {} of client {}", companyId, clientId);
        return ResponseEntity.ok(ledgerService.postDueServices(clientId, companyId));
    }

    @Override
    public ResponseEntity<BulkPostingResponse> postAllDueServices() {
        log.debug("Received request to post due services of every company");
        return ResponseEntity.ok(bulkPostingService.postAllDueServices());
    }

    @Override
    public ResponseEntity<LedgerEntryResponse> getEntry(UUID clientId, UUID companyId, UUID entryId) {
        log.debug("Received request to fetch ledger entry {} of company {} of client {}", entryId, companyId,
                clientId);
        return ResponseEntity.ok(ledgerService.getEntry(clientId, companyId, entryId));
    }

    @Override
    public ResponseEntity<LedgerEntryResponse> updateEntry(UUID clientId, UUID companyId, UUID entryId,
                                                           LedgerEntryUpdateRequest request) {
        log.debug("Received request to correct ledger entry {} of company {} of client {}", entryId, companyId,
                clientId);
        return ResponseEntity.ok(ledgerService.updateEntry(clientId, companyId, entryId, request));
    }

    @Override
    public ResponseEntity<CompanyLedgerSummaryResponse> summarizeCompany(UUID clientId, UUID companyId,
                                                                         String financialYear) {
        log.debug("Received request to summarise company {} of client {} for FY {}", companyId, clientId,
                financialYear);
        FinancialYear year = FinancialYears.parse(financialYear);
        return ResponseEntity.ok(ledgerService.summarizeCompany(clientId, companyId, year));
    }

    @Override
    public ResponseEntity<ClientLedgerSummaryResponse> summarizeClient(UUID clientId, String financialYear) {
        log.debug("Received request to summarise client {} for FY {}", clientId, financialYear);
        FinancialYear year = FinancialYears.parse(financialYear);
        return ResponseEntity.ok(ledgerService.summarizeClient(clientId, year));
    }

    @Override
    public ResponseEntity<ClientLedgerStatusResponse> clientLedgerStatus(UUID clientId, String financialYear,
                                                                         boolean pendingOnly) {
        log.debug("Received request for the ledger status of client {} for FY {}, pending only {}", clientId,
                financialYear == null ? "all" : financialYear, pendingOnly);
        FinancialYear year = financialYear == null ? null : FinancialYears.parse(financialYear);
        return ResponseEntity.ok(ledgerService.clientLedgerStatus(clientId, year, pendingOnly));
    }

    @Override
    public ResponseEntity<byte[]> exportClient(UUID clientId, String financialYear) {
        log.debug("Received request to export the ledger of client {} for FY {}", clientId, financialYear);
        FinancialYear year = FinancialYears.parse(financialYear);
        return download(ledgerService.exportClient(clientId, year));
    }

    @Override
    public ResponseEntity<byte[]> exportCompany(UUID clientId, UUID companyId, String financialYear) {
        log.debug("Received request to export the ledger of company {} of client {} for FY {}", companyId,
                clientId, financialYear);
        FinancialYear year = FinancialYears.parse(financialYear);
        return download(ledgerService.exportCompany(clientId, companyId, year));
    }

    /** An export as a download, saved under its own file name. */
    private static ResponseEntity<byte[]> download(LedgerExport export) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(LedgerExport.XLSX_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .body(export.content());
    }

    @Override
    public ResponseEntity<LedgerEntryResponse> recordPayment(UUID clientId, UUID companyId, PaymentRequest request) {
        log.debug("Received request to record a payment by {} from company {} of client {}", request.method(),
                companyId, clientId);
        LedgerEntryResponse payment = ledgerService.recordPayment(clientId, companyId, request);
        URI location = UriComponentsBuilder.fromPath(ENTRY_PATH)
                .buildAndExpand(clientId, companyId, payment.getId())
                .toUri();
        return ResponseEntity.created(location).body(payment);
    }

    @Override
    public ResponseEntity<PaymentListResponse> listPayments(UUID clientId, UUID companyId, LocalDate fromDate,
                                                            LocalDate toDate, String financialYear, int page,
                                                            int size, String sortBy, String sortDirection) {
        log.debug("Received request to list payments of company {} of client {} received {} to {} in FY {}, "
                        + "page {} of size {}, sorted by {} {}", companyId, clientId, fromDate, toDate, financialYear,
                page, size, sortBy, sortDirection);
        LedgerListQuery query = query(fromDate, toDate, financialYear, page, size, sortBy, sortDirection);
        return ResponseEntity.ok(ledgerService.listPayments(clientId, companyId, query));
    }

    @Override
    public ResponseEntity<LedgerEntryResponse> updatePayment(UUID clientId, UUID companyId, UUID paymentId,
                                                             PaymentRequest request) {
        log.debug("Received request to update payment {} of company {} of client {}", paymentId, companyId,
                clientId);
        return ResponseEntity.ok(ledgerService.updatePayment(clientId, companyId, paymentId, request));
    }

    @Override
    public ResponseEntity<Void> deleteEntry(UUID clientId, UUID companyId, UUID entryId) {
        log.debug("Received request to delete ledger entry {} of company {} of client {}", entryId, companyId,
                clientId);
        ledgerService.deleteEntry(clientId, companyId, entryId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<LedgerListResponse> listCompanyEntries(UUID clientId, UUID companyId, LocalDate fromDate,
                                                                 LocalDate toDate, String financialYear, int page,
                                                                 int size, String sortBy, String sortDirection) {
        log.debug("Received request to list ledger entries of company {} of client {} due {} to {} in FY {}, "
                        + "page {} of size {}, sorted by {} {}", companyId, clientId, fromDate, toDate, financialYear,
                page, size, sortBy, sortDirection);
        LedgerListQuery query = query(fromDate, toDate, financialYear, page, size, sortBy, sortDirection);
        return ResponseEntity.ok(ledgerService.listCompanyEntries(clientId, companyId, query));
    }

    @Override
    public ResponseEntity<LedgerListResponse> listClientEntries(UUID clientId, LocalDate fromDate, LocalDate toDate,
                                                                String financialYear, int page, int size,
                                                                String sortBy, String sortDirection) {
        log.debug("Received request to list ledger entries of client {} due {} to {} in FY {}, page {} of size {}, "
                + "sorted by {} {}", clientId, fromDate, toDate, financialYear, page, size, sortBy, sortDirection);
        LedgerListQuery query = query(fromDate, toDate, financialYear, page, size, sortBy, sortDirection);
        return ResponseEntity.ok(ledgerService.listClientEntries(clientId, query));
    }

    /** Validates every list parameter and gathers them into the query the service takes. */
    private LedgerListQuery query(LocalDate fromDate, LocalDate toDate, String financialYear, int page, int size,
                                  String sortBy, String sortDirection) {
        DueDates dueDates = DueDates.of(fromDate, toDate, financialYear);
        requireValidPage(page, size);
        Sort.Direction direction = Sort.Direction.fromOptionalString(sortDirection)
                .orElseThrow(() -> new InvalidSortException(
                        "sortDirection '" + sortDirection + "' is not asc or desc."));
        return new LedgerListQuery(dueDates.from(), dueDates.to(), page, size, LedgerSortField.fromParam(sortBy),
                direction);
    }

    private void requireValidPage(int page, int size) {
        if (page < 0) {
            throw new InvalidPageException("page must not be negative, but was " + page + ".");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPageException("size must be between 1 and " + MAX_PAGE_SIZE + ", but was " + size + ".");
        }
    }

    /**
     * The due dates a list is narrowed to, either end of which may be open.
     *
     * <p>A financial year is the range of due dates from its 1 April to its 31 March, since an
     * entry's financial year is worked out from its due date. Given alongside fromDate and toDate,
     * only the overlap of the two is kept; when they do not overlap, the range ends before it
     * starts and matches nothing.
     */
    private record DueDates(LocalDate from, LocalDate to) {

        static DueDates of(LocalDate fromDate, LocalDate toDate, String financialYear) {
            if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
                throw new InvalidDateRangeException(fromDate, toDate);
            }
            if (financialYear == null) {
                return new DueDates(fromDate, toDate);
            }
            FinancialYear year = FinancialYears.parse(financialYear);
            return new DueDates(later(fromDate, year.firstDay()), earlier(toDate, year.lastDay()));
        }

        private static LocalDate later(LocalDate date, LocalDate bound) {
            return date == null || date.isBefore(bound) ? bound : date;
        }

        private static LocalDate earlier(LocalDate date, LocalDate bound) {
            return date == null || date.isAfter(bound) ? bound : date;
        }
    }
}
