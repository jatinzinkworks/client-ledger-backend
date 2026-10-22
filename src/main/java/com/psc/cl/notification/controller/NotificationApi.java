package com.psc.cl.notification.controller;

import com.psc.cl.config.ApiPaths;
import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.notification.dto.BulkLedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.LedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.PaymentReminderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Contract for the emails sent to clients.
 */
@Tag(name = "Notifications", description = "Emails sent to clients about their accounts, and the PDF they attach")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public interface NotificationApi {

    /** Path a company's ledger summary is emailed to its client from. */
    String LEDGER_SUMMARY_EMAIL_PATH = ApiPaths.V1 + "/clients/{clientId}/companies/{companyId}/ledger-summary-email";

    /** Path a client's ledger summary across all of their companies is emailed to them from. */
    String CLIENT_LEDGER_SUMMARY_EMAIL_PATH = ApiPaths.V1 + "/clients/{clientId}/ledger-summary-email";

    /** Path the PDF a company's ledger summary email attaches is downloaded from. */
    String LEDGER_SUMMARY_PDF_PATH = ApiPaths.V1 + "/clients/{clientId}/companies/{companyId}/ledger-summary-pdf";

    /** Path the PDF a client's ledger summary email attaches is downloaded from. */
    String CLIENT_LEDGER_SUMMARY_PDF_PATH = ApiPaths.V1 + "/clients/{clientId}/ledger-summary-pdf";

    /** Path every client is emailed their ledger summary from in one go. */
    String ALL_LEDGER_SUMMARY_EMAILS_PATH = ApiPaths.V1 + "/notifications/ledger-summary-emails";

    /** Path payment reminders are sent to every client with an amount pending from. */
    String PAYMENT_REMINDERS_PATH = ApiPaths.V1 + "/notifications/payment-reminders";

    @Operation(
            summary = "Email a client the ledger summary of one of their companies",
            description = "Emails the client a summary of the company's ledger for the current Indian financial "
                    + "year (1 April to 31 March): what was billed, what was received, what "
                    + "is pending and the last payment, with the year's ledger statement attached as a PDF: "
                    + "the summary, then every charge as one total fee and every payment as the amount paid, "
                    + "with no discount shown. It goes to the client's email address, copying in their manager, "
                    + "from the "
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
                    + "what was received, what is pending and the last payment, followed by "
                    + "each company's billed, received and pending amounts. The year's ledger statement is "
                    + "attached as a PDF: the summary with a line per company, then each company's charges and "
                    + "payments on a page of its own, every charge as one total fee and every payment as the "
                    + "amount paid, with no discount shown. It goes to the client's email address, "
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
            summary = "Download the ledger summary of one of a client's companies as a PDF",
            description = "Downloads the PDF ledger statement that emailing the client the ledger summary of one "
                    + "of their companies attaches, without sending anything, every page headed by the firm's "
                    + "logo and its name from Global Settings. With financialYear, it covers that Indian "
                    + "financial year (1 April to 31 March); for the current year it is exactly the PDF the "
                    + "email attaches. Without it, it covers every year as one period, from the year the client "
                    + "was added (or of the company's earliest entry, if earlier) to the current year: the "
                    + "summary is across the whole span, so the amount pending is what the company owes "
                    + "overall, and the details list every entry since it was taken on with a running balance. "
                    + "Either way it opens with the company's summary, then its charges and payments, every "
                    + "charge as one total fee and every payment as the amount paid, with no discount shown. "
                    + "The file is named after the company and the year, such as "
                    + "Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.pdf, or Mehta_Textiles_Pvt_Ltd_Ledger_All_Years.pdf "
                    + "for every year.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The PDF, as an attachment; a period with no entries gives zero totals",
                    headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                            description = "attachment, with the statement's file name",
                            schema = @Schema(type = "string",
                                    example = "attachment; filename=\"Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.pdf\"")),
                    content = @Content(mediaType = LedgerExport.PDF_MEDIA_TYPE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is not a year such as 2026-27, or an identifier is not a "
                            + "valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, or the client holds no such company",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    // JSON is listed too, so an error can still be returned as an ErrorResponse.
    @GetMapping(value = LEDGER_SUMMARY_PDF_PATH,
            produces = {LedgerExport.PDF_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    ResponseEntity<byte[]> downloadLedgerSummaryPdf(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @Parameter(description = "Indian financial year of the statement, written as yyyy-yy. Omit for "
                    + "every year together", example = "2026-27")
            @RequestParam(required = false) String financialYear);

    @Operation(
            summary = "Download a client's ledger summary as a PDF",
            description = "Downloads the PDF ledger statement that emailing the client their ledger summary "
                    + "attaches, without sending anything, across all of the client's companies, every page "
                    + "headed by the firm's logo and its name from Global Settings. With financialYear, it covers "
                    + "that Indian financial year (1 April to 31 March); for the current year it is exactly the "
                    + "PDF the email attaches. Without it, it covers every year the client has been one, from "
                    + "the year they were added (or of their earliest entry) to the current year, as one "
                    + "period: the summary is across the whole span, so the amount pending is what the client "
                    + "owes overall, and each company lists every entry since it was taken on with a running "
                    + "balance. Either way it opens with the summary and a line per company, then each "
                    + "company's charges and payments on a page of its own, every charge as one total fee and "
                    + "every payment as the amount paid, with no discount shown. The file is named after the "
                    + "client and the year, such as Rohan_Mehta_Ledger_FY2026-27.pdf, or "
                    + "Rohan_Mehta_Ledger_All_Years.pdf for every year.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The PDF, as an attachment; a year with no entries gives zero totals",
                    headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                            description = "attachment, with the statement's file name",
                            schema = @Schema(type = "string",
                                    example = "attachment; filename=\"Rohan_Mehta_Ledger_FY2026-27.pdf\"")),
                    content = @Content(mediaType = LedgerExport.PDF_MEDIA_TYPE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is not a year such as 2026-27, or the identifier is not a "
                            + "valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No client carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    // JSON is listed too, so an error can still be returned as an ErrorResponse.
    @GetMapping(value = CLIENT_LEDGER_SUMMARY_PDF_PATH,
            produces = {LedgerExport.PDF_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    ResponseEntity<byte[]> downloadClientLedgerSummaryPdf(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Indian financial year of the statement, written as yyyy-yy. Omit for "
                    + "every year the client has been one, together", example = "2026-27")
            @RequestParam(required = false) String financialYear);

    @Operation(
            summary = "Email every client their ledger summary",
            description = "Takes no input. Emails every client, one after another, their ledger summary for the "
                    + "current Indian financial year across all of their companies, exactly as emailing one "
                    + "client does: to the client, copying in their manager, with the year's PDF ledger "
                    + "statement attached. Each client is emailed on its own, so one that fails is listed under failures "
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
                    + "Indian financial year: billed, received, the last payment and each company's "
                    + "figures, with the year's PDF ledger statement attached; when earlier years left a balance, it "
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
}
