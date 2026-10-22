package com.psc.cl.notification.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psc.cl.client.exception.ClientNotFoundException;
import com.psc.cl.client.exception.CompanyNotFoundException;
import com.psc.cl.config.ApiPaths;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.ledger.service.FinancialYears;
import com.psc.cl.notification.dto.BulkLedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.ClientEmailFailure;
import com.psc.cl.notification.dto.ClientEmailResult;
import com.psc.cl.notification.dto.ClientReminderResult;
import com.psc.cl.notification.dto.LedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.PaymentReminderResponse;
import com.psc.cl.notification.exception.EmailDeliveryException;
import com.psc.cl.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    private static final String PDF_URL = ApiPaths.V1 + "/clients/" + CLIENT_ID + "/ledger-summary-pdf";

    private static final String COMPANY_PDF_URL = ApiPaths.V1 + "/clients/" + CLIENT_ID + "/companies/" + COMPANY_ID
            + "/ledger-summary-pdf";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    @DisplayName("downloads the client's ledger summary PDF for a financial year as an attachment")
    void downloadsClientLedgerSummaryPdf() throws Exception {
        byte[] pdf = {37, 80, 68, 70};
        when(notificationService.clientLedgerSummaryPdf(CLIENT_ID, FinancialYears.parse("2026-27")))
                .thenReturn(new LedgerExport("Rohan_Mehta_Ledger_FY2026-27.pdf", pdf));

        mockMvc.perform(get(PDF_URL).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(LedgerExport.PDF_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"Rohan_Mehta_Ledger_FY2026-27.pdf\"")))
                .andExpect(content().bytes(pdf));
    }

    @Test
    @DisplayName("without a financial year, downloads the PDF across every year")
    void downloadsClientLedgerSummaryPdfForAllYears() throws Exception {
        byte[] pdf = {37, 80, 68, 70};
        when(notificationService.clientLedgerSummaryPdf(CLIENT_ID, null))
                .thenReturn(new LedgerExport("Rohan_Mehta_Ledger_All_Years.pdf", pdf));

        mockMvc.perform(get(PDF_URL))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"Rohan_Mehta_Ledger_All_Years.pdf\"")))
                .andExpect(content().bytes(pdf));
    }

    @Test
    @DisplayName("returns 404 as JSON when downloading the PDF of an unknown client")
    void rejectsPdfOfUnknownClient() throws Exception {
        when(notificationService.clientLedgerSummaryPdf(CLIENT_ID, null))
                .thenThrow(new ClientNotFoundException(CLIENT_ID));

        mockMvc.perform(get(PDF_URL))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("returns 400 when downloading the PDF with a client id that is not a UUID")
    void rejectsPdfOfMalformedClientId() throws Exception {
        mockMvc.perform(get(ApiPaths.V1 + "/clients/not-a-uuid/ledger-summary-pdf"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).clientLedgerSummaryPdf(any(), any());
    }

    @Test
    @DisplayName("downloads a company's ledger summary PDF for a financial year as an attachment")
    void downloadsCompanyLedgerSummaryPdf() throws Exception {
        byte[] pdf = {37, 80, 68, 70};
        when(notificationService.companyLedgerSummaryPdf(CLIENT_ID, COMPANY_ID, FinancialYears.parse("2026-27")))
                .thenReturn(new LedgerExport("Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.pdf", pdf));

        mockMvc.perform(get(COMPANY_PDF_URL).param("financialYear", "2026-27"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(LedgerExport.PDF_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.pdf\"")))
                .andExpect(content().bytes(pdf));
    }

    @Test
    @DisplayName("without a financial year, downloads a company's PDF across every year")
    void downloadsCompanyLedgerSummaryPdfForAllYears() throws Exception {
        byte[] pdf = {37, 80, 68, 70};
        when(notificationService.companyLedgerSummaryPdf(CLIENT_ID, COMPANY_ID, null))
                .thenReturn(new LedgerExport("Mehta_Textiles_Pvt_Ltd_Ledger_All_Years.pdf", pdf));

        mockMvc.perform(get(COMPANY_PDF_URL))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"Mehta_Textiles_Pvt_Ltd_Ledger_All_Years.pdf\"")))
                .andExpect(content().bytes(pdf));
    }

    @Test
    @DisplayName("returns 404 as JSON when downloading the PDF of a company the client does not hold")
    void rejectsPdfOfUnknownCompany() throws Exception {
        when(notificationService.companyLedgerSummaryPdf(CLIENT_ID, COMPANY_ID, null))
                .thenThrow(new CompanyNotFoundException(CLIENT_ID, COMPANY_ID));

        mockMvc.perform(get(COMPANY_PDF_URL))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("returns 400 when downloading a company's PDF for a malformed financial year")
    void rejectsCompanyPdfOfMalformedFinancialYear() throws Exception {
        mockMvc.perform(get(COMPANY_PDF_URL).param("financialYear", "26-27"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).companyLedgerSummaryPdf(any(), any(), any());
    }

    @Test
    @DisplayName("returns 400 when downloading the PDF for a financial year that is not like 2026-27")
    void rejectsPdfOfMalformedFinancialYear() throws Exception {
        mockMvc.perform(get(PDF_URL).param("financialYear", "2026-28"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).clientLedgerSummaryPdf(any(), any());
    }

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
    @DisplayName("returns 400 when an identifier is not a UUID")
    void rejectsMalformedIdentifier() throws Exception {
        mockMvc.perform(post(ApiPaths.V1 + "/clients/not-a-uuid/companies/" + COMPANY_ID + "/ledger-summary-email"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).sendLedgerSummary(any(), any());
    }
}
