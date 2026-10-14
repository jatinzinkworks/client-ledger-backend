package com.psc.cl.notification.controller;

import com.psc.cl.config.ApiPaths;
import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.notification.dto.BulkLedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.ClientWhatsAppResult;
import com.psc.cl.notification.dto.LedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.PaymentReminderResponse;
import com.psc.cl.notification.dto.WhatsAppReminderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * Contract for the emails sent to clients.
 */
@Tag(name = "Notifications", description = "Emails sent to clients about their accounts")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public interface NotificationApi {

    /** Path a company's ledger summary is emailed to its client from. */
    String LEDGER_SUMMARY_EMAIL_PATH = ApiPaths.V1 + "/clients/{clientId}/companies/{companyId}/ledger-summary-email";

    /** Path a client's ledger summary across all of their companies is emailed to them from. */
    String CLIENT_LEDGER_SUMMARY_EMAIL_PATH = ApiPaths.V1 + "/clients/{clientId}/ledger-summary-email";

    /** Path every client is emailed their ledger summary from in one go. */
    String ALL_LEDGER_SUMMARY_EMAILS_PATH = ApiPaths.V1 + "/notifications/ledger-summary-emails";

    /** Path payment reminders are sent to every client with an amount pending from. */
    String PAYMENT_REMINDERS_PATH = ApiPaths.V1 + "/notifications/payment-reminders";

    /** Path WhatsApp payment reminders are sent to every client with an amount pending from. */
    String WHATSAPP_PAYMENT_REMINDERS_PATH = ApiPaths.V1 + "/notifications/whatsapp-payment-reminders";

    /** Path a WhatsApp payment reminder is sent to one client from. */
    String CLIENT_WHATSAPP_PAYMENT_REMINDER_PATH = ApiPaths.V1 + "/clients/{clientId}/whatsapp-payment-reminder";

    @Operation(
            summary = "Email a client the ledger summary of one of their companies",
            description = "Emails the client a summary of the company's ledger for the current Indian financial "
                    + "year (1 April to 31 March): what was billed, the discount given, what was received, what "
                    + "is pending and the last payment, with the year's full ledger attached as an Excel "
                    + "workbook. It goes to the client's email address, copying in their manager, from the "
                    + "address set in MAIL_FROM; replies go to the firm's email from Global Settings when one "
                    + "is saved. The email is sent before the response returns.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The email was sent",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LedgerSummaryEmailResponse.class))),
            @ApiResponse(responseCode = "400", description = "An identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, or the client holds no such company",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502",
                    description = "Email is not configured, or the mail server refused the email or could not "
                            + "be reached; nothing was sent",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(LEDGER_SUMMARY_EMAIL_PATH)
    ResponseEntity<LedgerSummaryEmailResponse> sendLedgerSummary(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company to summarise", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId);

    @Operation(
            summary = "Email a client the ledger summary across all of their companies",
            description = "Emails the client a summary of their ledger for the current Indian financial year "
                    + "(1 April to 31 March) across all of their companies together: what was billed, the "
                    + "discount given, what was received, what is pending and the last payment, followed by "
                    + "each company's billed, received and pending amounts. The year's full ledger is attached "
                    + "as an Excel workbook with a sheet per company. It goes to the client's email address, "
                    + "copying in their manager, from the address set in MAIL_FROM; replies go to the firm's "
                    + "email from Global Settings when one is saved. The email is sent before the response "
                    + "returns.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The email was sent",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LedgerSummaryEmailResponse.class))),
            @ApiResponse(responseCode = "400", description = "The identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No client carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502",
                    description = "Email is not configured, or the mail server refused the email or could not "
                            + "be reached; nothing was sent",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(CLIENT_LEDGER_SUMMARY_EMAIL_PATH)
    ResponseEntity<LedgerSummaryEmailResponse> sendClientLedgerSummary(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId);

    @Operation(
            summary = "Email every client their ledger summary",
            description = "Takes no input. Emails every client, one after another, their ledger summary for the "
                    + "current Indian financial year across all of their companies, exactly as emailing one "
                    + "client does: to the client, copying in their manager, with the year's Excel ledger "
                    + "attached. Each client is emailed on its own, so one that fails is listed under failures "
                    + "and the others are still emailed. Clients with nothing billed or received in the year "
                    + "are emailed too. The response returns once every client has been tried, so it takes "
                    + "longer the more clients there are.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The run's outcome, including any clients that could not be emailed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BulkLedgerSummaryEmailResponse.class))),
            @ApiResponse(responseCode = "502",
                    description = "Email is not configured; no client was emailed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(ALL_LEDGER_SUMMARY_EMAILS_PATH)
    ResponseEntity<BulkLedgerSummaryEmailResponse> sendAllClientLedgerSummaries();

    @Operation(
            summary = "Email a payment reminder to every client with an amount pending",
            description = "Takes no input. Emails every client who owes something across every financial year "
                    + "(billed less received across all of their companies, to date, is more than zero) an "
                    + "individual payment reminder for that whole amount, under the subject \"Payment "
                    + "reminder\". It carries the same details as the ledger summary email for the current "
                    + "Indian financial year: billed, discount, received, the last payment and each company's "
                    + "figures, with the year's Excel ledger attached; when earlier years left a balance, it "
                    + "is shown alongside the year's so the figures add up to the amount asked for. It goes to "
                    + "the client, copying in their manager. Clients settled or in credit overall are skipped. "
                    + "Each client is emailed on its own, so one that fails is listed under failures and the "
                    + "others are still reminded. The response returns once every client has been tried.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The run's outcome, including any clients that could not be reminded",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PaymentReminderResponse.class))),
            @ApiResponse(responseCode = "502",
                    description = "Email is not configured; no client was emailed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(PAYMENT_REMINDERS_PATH)
    ResponseEntity<PaymentReminderResponse> sendPaymentReminders();

    @Operation(
            summary = "Send a WhatsApp payment reminder to every client with an amount pending",
            description = "Takes no input. Sends every client who has opted in to WhatsApp messages "
                    + "(whatsappOptIn) and owes something across every financial year (billed less received "
                    + "across all of their companies, to date, is more than zero) a WhatsApp payment reminder on "
                    + "the phone number in their record, through Meta's WhatsApp Cloud API. Each is the approved "
                    + "template set in WHATSAPP_PAYMENT_REMINDER_TEMPLATE, filled with the client's first name, "
                    + "the firm's name, the amount pending and today's date. Numbers without a country code get "
                    + "WHATSAPP_DEFAULT_COUNTRY_CODE. Clients not opted in are counted in notOptedInCount; those "
                    + "settled or in credit overall in skippedCount. Each client is sent to on its own, so one "
                    + "that fails, such as a phone number WhatsApp cannot use, is listed under failures and the "
                    + "others are still reminded. The response returns once every client has been tried.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The run's outcome, including any clients that could not be reminded",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WhatsAppReminderResponse.class))),
            @ApiResponse(responseCode = "502",
                    description = "WhatsApp is not configured; no client was sent a message",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(WHATSAPP_PAYMENT_REMINDERS_PATH)
    ResponseEntity<WhatsAppReminderResponse> sendWhatsAppPaymentReminders();

    @Operation(
            summary = "Send one client a WhatsApp payment reminder",
            description = "Sends the client a WhatsApp payment reminder for what they owe across every financial "
                    + "year and all of their companies, on the phone number in their record, through Meta's "
                    + "WhatsApp Cloud API: the approved template set in WHATSAPP_PAYMENT_REMINDER_TEMPLATE, filled "
                    + "with their first name, the firm's name, the amount pending and today's date. The client "
                    + "must have opted in to WhatsApp messages (whatsappOptIn) and owe something. The message is "
                    + "sent before the response returns.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The reminder was accepted by WhatsApp",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ClientWhatsAppResult.class))),
            @ApiResponse(responseCode = "400", description = "The identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No client carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "The client has not opted in to WhatsApp messages, owes nothing, or has a phone "
                            + "number WhatsApp cannot use; nothing was sent",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502",
                    description = "WhatsApp is not configured, or refused the message or could not be reached; "
                            + "nothing was sent",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(CLIENT_WHATSAPP_PAYMENT_REMINDER_PATH)
    ResponseEntity<ClientWhatsAppResult> sendWhatsAppPaymentReminder(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId);
}
