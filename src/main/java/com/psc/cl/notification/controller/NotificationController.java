package com.psc.cl.notification.controller;

import com.psc.cl.notification.dto.BulkLedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.ClientWhatsAppResult;
import com.psc.cl.notification.dto.LedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.PaymentReminderResponse;
import com.psc.cl.notification.dto.WhatsAppReminderResponse;
import com.psc.cl.notification.service.NotificationService;
import com.psc.cl.notification.service.WhatsAppNotificationService;
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

    private final WhatsAppNotificationService whatsAppNotificationService;

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

    @Override
    public ResponseEntity<PaymentReminderResponse> sendPaymentReminders() {
        log.debug("Received request to send payment reminders to every client with an amount pending");
        return ResponseEntity.ok(notificationService.sendPaymentReminders());
    }

    @Override
    public ResponseEntity<WhatsAppReminderResponse> sendWhatsAppPaymentReminders() {
        log.debug("Received request to send WhatsApp payment reminders to every client with an amount pending");
        return ResponseEntity.ok(whatsAppNotificationService.sendPaymentReminders());
    }

    @Override
    public ResponseEntity<ClientWhatsAppResult> sendWhatsAppPaymentReminder(UUID clientId) {
        log.debug("Received request to send a WhatsApp payment reminder to client {}", clientId);
        return ResponseEntity.ok(whatsAppNotificationService.sendPaymentReminder(clientId));
    }
}
