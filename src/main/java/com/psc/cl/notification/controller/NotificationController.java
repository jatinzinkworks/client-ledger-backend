package com.psc.cl.notification.controller;

import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.ledger.model.FinancialYear;
import com.psc.cl.ledger.service.FinancialYears;
import com.psc.cl.notification.dto.BulkLedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.LedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.PaymentReminderResponse;
import com.psc.cl.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Single entry point for every Notification endpoint.
 */
@RestController
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    @Override
    public ResponseEntity<LedgerSummaryEmailResponse> sendLedgerSummary(UUID clientId, UUID companyId) {
        log.debug("Received request to email the ledger summary of company {} to client {}", companyId, clientId);
        return ResponseEntity.ok(notificationService.sendLedgerSummary(clientId, companyId));
    }

    @Override
    public ResponseEntity<LedgerSummaryEmailResponse> sendClientLedgerSummary(UUID clientId) {
        log.debug("Received request to email the ledger summary across all companies to client {}", clientId);
        return ResponseEntity.ok(notificationService.sendClientLedgerSummary(clientId));
    }

    @Override
    public ResponseEntity<byte[]> downloadLedgerSummaryPdf(UUID clientId, UUID companyId, String financialYear) {
        log.debug("Received request to download the ledger summary PDF of company {} of client {} for FY {}",
                companyId, clientId, financialYear == null ? "all years" : financialYear);
        return download(notificationService.companyLedgerSummaryPdf(clientId, companyId, year(financialYear)));
    }

    @Override
    public ResponseEntity<byte[]> downloadClientLedgerSummaryPdf(UUID clientId, String financialYear) {
        log.debug("Received request to download the ledger summary PDF of client {} for FY {}", clientId,
                financialYear == null ? "all years" : financialYear);
        return download(notificationService.clientLedgerSummaryPdf(clientId, year(financialYear)));
    }

    /** The financial year asked for; null, for every year, when none is. */
    private static FinancialYear year(String financialYear) {
        return financialYear == null ? null : FinancialYears.parse(financialYear);
    }

    /** A statement as a download, saved under its own file name. */
    private static ResponseEntity<byte[]> download(LedgerExport statement) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(LedgerExport.PDF_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(statement.fileName()).build().toString())
                .body(statement.content());
    }

    @Override
    public ResponseEntity<BulkLedgerSummaryEmailResponse> sendAllClientLedgerSummaries() {
        log.debug("Received request to email every client their ledger summary");
        return ResponseEntity.ok(notificationService.sendAllClientLedgerSummaries());
    }

    @Override
    public ResponseEntity<PaymentReminderResponse> sendPaymentReminders() {
        log.debug("Received request to send payment reminders to every client with an amount pending");
        return ResponseEntity.ok(notificationService.sendPaymentReminders());
    }
}
