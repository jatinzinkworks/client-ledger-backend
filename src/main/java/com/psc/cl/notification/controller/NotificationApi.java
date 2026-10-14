package com.psc.cl.notification.controller;

import com.psc.cl.config.ApiPaths;
import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.notification.dto.BulkLedgerSummaryEmailResponse;
import com.psc.cl.notification.dto.LedgerSummaryEmailResponse;
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
}
