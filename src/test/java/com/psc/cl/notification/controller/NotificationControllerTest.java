package com.psc.cl.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.config.ApiPaths;
import com.psc.cl.notification.dto.BulkLedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.ClientEmailFailure;
import com.psc.cl.notification.dto.ClientEmailResult;
import com.psc.cl.notification.dto.ClientReminderResult;
import com.psc.cl.notification.dto.ClientWhatsAppFailure;
import com.psc.cl.notification.dto.ClientWhatsAppResult;
import com.psc.cl.notification.dto.LedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.PaymentReminderResponse;
import com.psc.cl.notification.dto.WhatsAppReminderResponse;
import com.psc.cl.notification.exception.EmailDeliveryException;
import com.psc.cl.notification.exception.WhatsAppDeliveryException;
import com.psc.cl.notification.exception.WhatsAppNotOptedInException;
import com.psc.cl.notification.service.NotificationService;
import com.psc.cl.notification.service.WhatsAppNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    private static final UUID CLIENT_ID = UUID.fromString("7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35");

    private static final UUID COMPANY_ID = UUID.fromString("4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28");

    private static final String URL = ApiPaths.V1 + "/clients/" + CLIENT_ID + "/companies/" + COMPANY_ID
            + "/ledger-summary-email";

    private static final String CLIENT_URL = ApiPaths.V1 + "/clients/" + CLIENT_ID + "/ledger-summary-email";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private WhatsAppNotificationService whatsAppNotificationService;

    @Test
    @DisplayName("emails the ledger summary and says who it went to")
    void sendsLedgerSummary() throws Exception {
        when(notificationService.sendLedgerSummary(CLIENT_ID, COMPANY_ID)).thenReturn(
                LedgerSummaryEmailResponse.builder()
                        .clientId(CLIENT_ID)
                        .companyId(COMPANY_ID)
                        .financialYear("2026-27")
                        .to("rohan@example.com")
                        .cc(List.of("pranay@sharma.example"))
                        .subject("Your ledger summary for Mehta Textiles Pvt Ltd, FY 2026-27")
                        .attachmentName("Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.xlsx")
                        .sentAt(Instant.parse("2026-10-14T05:30:00Z"))
                        .build());

        mockMvc.perform(post(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financialYear").value("2026-27"))
                .andExpect(jsonPath("$.to").value("rohan@example.com"))
                .andExpect(jsonPath("$.cc[0]").value("pranay@sharma.example"))
                .andExpect(jsonPath("$.attachmentName").value("Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.xlsx"));
    }

    @Test
    @DisplayName("returns 404 when the client holds no such company")
    void rejectsUnknownCompany() throws Exception {
        when(notificationService.sendLedgerSummary(CLIENT_ID, COMPANY_ID))
                .thenThrow(new CompanyNotFoundException(CLIENT_ID, COMPANY_ID));

        mockMvc.perform(post(URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("returns 502 when the email could not be sent")
    void reportsDeliveryFailure() throws Exception {
        when(notificationService.sendLedgerSummary(CLIENT_ID, COMPANY_ID))
                .thenThrow(new EmailDeliveryException("The ledger summary email could not be sent"));

        mockMvc.perform(post(URL))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_SERVICE_ERROR"))
                .andExpect(jsonPath("$.message").value("The ledger summary email could not be sent"));
    }

    @Test
    @DisplayName("emails the client summary across all companies, with no company in the response")
    void sendsClientLedgerSummary() throws Exception {
        when(notificationService.sendClientLedgerSummary(CLIENT_ID)).thenReturn(
                LedgerSummaryEmailResponse.builder()
                        .clientId(CLIENT_ID)
                        .financialYear("2026-27")
                        .to("rohan@example.com")
                        .cc(List.of("pranay@sharma.example"))
                        .subject("Your ledger summary for all of your companies, FY 2026-27")
                        .attachmentName("Rohan_Mehta_Ledger_FY2026-27.xlsx")
                        .sentAt(Instant.parse("2026-10-14T05:30:00Z"))
                        .build());

        mockMvc.perform(post(CLIENT_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.companyId").doesNotExist())
                .andExpect(jsonPath("$.attachmentName").value("Rohan_Mehta_Ledger_FY2026-27.xlsx"));
    }

    @Test
    @DisplayName("returns 404 when emailing the summary of an unknown client")
    void rejectsUnknownClient() throws Exception {
        when(notificationService.sendClientLedgerSummary(CLIENT_ID))
                .thenThrow(new ClientNotFoundException(CLIENT_ID));

        mockMvc.perform(post(CLIENT_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("emails every client without any input, listing who was emailed and who failed")
    void sendsToEveryClient() throws Exception {
        UUID failedClient = UUID.randomUUID();
        when(notificationService.sendAllClientLedgerSummaries()).thenReturn(
                BulkLedgerSummaryEmailResponse.builder()
                        .financialYear("2026-27")
                        .clientCount(2)
                        .sentCount(1)
                        .sent(List.of(ClientEmailResult.builder().clientId(CLIENT_ID).clientName("Rohan Mehta")
                                .build()))
                        .failures(List.of(ClientEmailFailure.builder().clientId(failedClient)
                                .clientName("Anita Shah").error("The mail server could not be reached").build()))
                        .build());

        mockMvc.perform(post(ApiPaths.V1 + "/notifications/ledger-summary-emails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientCount").value(2))
                .andExpect(jsonPath("$.sentCount").value(1))
                .andExpect(jsonPath("$.sent[0].clientName").value("Rohan Mehta"))
                .andExpect(jsonPath("$.failures[0].clientId").value(failedClient.toString()))
                .andExpect(jsonPath("$.failures[0].error").value("The mail server could not be reached"));
    }

    @Test
    @DisplayName("returns 502 when emailing every client while email is not configured")
    void reportsBulkNotConfigured() throws Exception {
        when(notificationService.sendAllClientLedgerSummaries())
                .thenThrow(new EmailDeliveryException("Email is not configured"));

        mockMvc.perform(post(ApiPaths.V1 + "/notifications/ledger-summary-emails"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_SERVICE_ERROR"));
    }

    @Test
    @DisplayName("sends payment reminders without any input, listing who was reminded and how many were skipped")
    void sendsPaymentReminders() throws Exception {
        when(notificationService.sendPaymentReminders()).thenReturn(PaymentReminderResponse.builder()
                .financialYear("2026-27")
                .clientCount(3)
                .sentCount(1)
                .skippedCount(2)
                .sent(List.of(ClientReminderResult.builder()
                        .clientId(CLIENT_ID)
                        .clientName("Rohan Mehta")
                        .pending(new BigDecimal("24235.00"))
                        .build()))
                .failures(List.of())
                .build());

        mockMvc.perform(post(ApiPaths.V1 + "/notifications/payment-reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientCount").value(3))
                .andExpect(jsonPath("$.sentCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(2))
                .andExpect(jsonPath("$.sent[0].clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.sent[0].pending").value(24235.0))
                .andExpect(jsonPath("$.failures").isEmpty());
    }

    @Test
    @DisplayName("returns 502 when sending payment reminders while email is not configured")
    void reportsRemindersNotConfigured() throws Exception {
        when(notificationService.sendPaymentReminders())
                .thenThrow(new EmailDeliveryException("Email is not configured"));

        mockMvc.perform(post(ApiPaths.V1 + "/notifications/payment-reminders"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_SERVICE_ERROR"));
    }

    @Test
    @DisplayName("sends WhatsApp payment reminders without any input, listing who was reminded and who failed")
    void sendsWhatsAppPaymentReminders() throws Exception {
        UUID failedClient = UUID.randomUUID();
        when(whatsAppNotificationService.sendPaymentReminders()).thenReturn(WhatsAppReminderResponse.builder()
                .clientCount(3)
                .sentCount(1)
                .notOptedInCount(1)
                .skippedCount(1)
                .sent(List.of(ClientWhatsAppResult.builder()
                        .clientId(CLIENT_ID)
                        .clientName("Rohan Mehta")
                        .pending(new BigDecimal("24235.00"))
                        .messageId("wamid.ABC123")
                        .build()))
                .failures(List.of(ClientWhatsAppFailure.builder()
                        .clientId(failedClient)
                        .clientName("Anita Shah")
                        .error("The client's phone number cannot be used for WhatsApp")
                        .build()))
                .build());

        mockMvc.perform(post(ApiPaths.V1 + "/notifications/whatsapp-payment-reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentCount").value(1))
                .andExpect(jsonPath("$.notOptedInCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.sent[0].messageId").value("wamid.ABC123"))
                .andExpect(jsonPath("$.sent[0].pending").value(24235.0))
                .andExpect(jsonPath("$.failures[0].clientId").value(failedClient.toString()));
    }

    @Test
    @DisplayName("sends one client a WhatsApp payment reminder")
    void sendsWhatsAppPaymentReminderToOneClient() throws Exception {
        when(whatsAppNotificationService.sendPaymentReminder(CLIENT_ID)).thenReturn(ClientWhatsAppResult.builder()
                .clientId(CLIENT_ID)
                .clientName("Rohan Mehta")
                .pending(new BigDecimal("24235.00"))
                .messageId("wamid.ABC123")
                .build());

        mockMvc.perform(post(ApiPaths.V1 + "/clients/" + CLIENT_ID + "/whatsapp-payment-reminder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.pending").value(24235.0))
                .andExpect(jsonPath("$.messageId").value("wamid.ABC123"));
    }

    @Test
    @DisplayName("returns 409 when the client has not opted in to WhatsApp, and 404 when there is no such client")
    void rejectsWhatsAppReminderToOneClient() throws Exception {
        String url = ApiPaths.V1 + "/clients/" + CLIENT_ID + "/whatsapp-payment-reminder";
        when(whatsAppNotificationService.sendPaymentReminder(CLIENT_ID))
                .thenThrow(new WhatsAppNotOptedInException(CLIENT_ID))
                .thenThrow(new ClientNotFoundException(CLIENT_ID));

        mockMvc.perform(post(url))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        mockMvc.perform(post(url))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("returns 502 when sending WhatsApp reminders while WhatsApp is not configured")
    void reportsWhatsAppNotConfigured() throws Exception {
        when(whatsAppNotificationService.sendPaymentReminders())
                .thenThrow(new WhatsAppDeliveryException("WhatsApp is not configured"));

        mockMvc.perform(post(ApiPaths.V1 + "/notifications/whatsapp-payment-reminders"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_SERVICE_ERROR"))
                .andExpect(jsonPath("$.message").value("WhatsApp is not configured"));
    }

    @Test
    @DisplayName("returns 400 when an identifier is not a UUID")
    void rejectsMalformedIdentifier() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/clients/not-a-uuid/companies/" + COMPANY_ID + "/ledger-summary-email"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).sendLedgerSummary(any(), any());
    }
}
