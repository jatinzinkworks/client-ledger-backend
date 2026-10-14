package com.psc.cl.notification.controller;

import com.psc.cl.notification.dto.BulkLedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.LedgerSummaryEmailResponse;
import com.psc.cl.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public ResponseEntity<BulkLedgerSummaryEmailResponse> sendAllClientLedgerSummaries() {
        log.debug("Received request to email every client their ledger summary");
        return ResponseEntity.ok(notificationService.sendAllClientLedgerSummaries());
    }
}
