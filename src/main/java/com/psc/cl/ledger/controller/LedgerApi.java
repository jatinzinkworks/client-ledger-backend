package com.psc.cl.ledger.controller;

import com.psc.cl.config.ApiPaths;
import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.ledger.dto.ClientLedgerSummaryResponse;
import com.psc.cl.ledger.dto.CompanyLedgerSummaryResponse;
import com.psc.cl.ledger.dto.LedgerEntryResponse;
import com.psc.cl.ledger.dto.LedgerEntryUpdateRequest;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.ledger.dto.LedgerListResponse;
import com.psc.cl.ledger.dto.PaymentListResponse;
import com.psc.cl.ledger.dto.PaymentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Contract for the ledger of amounts a company owes for its services.
 */
@Tag(name = "Ledger", description = "Amounts client companies owe for the services delivered to them")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public interface LedgerApi {

    /** Path of the ledger across every company of a client. */
    String CLIENT_PATH = ApiPaths.V1 + "/clients/{clientId}/ledger-entries";

    /** Path of the ledger of one company of a client. */
    String COMPANY_PATH = ApiPaths.V1 + "/clients/{clientId}/companies/{companyId}/ledger-entries";

    /** Path of a single entry in the ledger of one company of a client. */
    String ENTRY_PATH = COMPANY_PATH + "/{entryId}";

    /** Path payments received from one company of a client are recorded under. */
    String PAYMENTS_PATH = ApiPaths.V1 + "/clients/{clientId}/companies/{companyId}/payments";

    /** Path of a single payment recorded for one company of a client. */
    String PAYMENT_PATH = PAYMENTS_PATH + "/{paymentId}";

    /** Path of the financial year summary of one company of a client. */
    String COMPANY_SUMMARY_PATH = ApiPaths.V1 + "/clients/{clientId}/companies/{companyId}/ledger-summary";

    /** Path of the financial year summary of a client across all of their companies. */
    String CLIENT_SUMMARY_PATH = ApiPaths.V1 + "/clients/{clientId}/ledger-summary";

    /** Path a client's ledger for a financial year is downloaded from as an Excel workbook. */
    String CLIENT_EXPORT_PATH = ApiPaths.V1 + "/clients/{clientId}/ledger-export";

    /** Path the ledger of one company of a client for a financial year is downloaded from as an Excel workbook. */
    String COMPANY_EXPORT_PATH = ApiPaths.V1 + "/clients/{clientId}/companies/{companyId}/ledger-export";

    /** Shared by the list endpoints' range filter. */
    String FROM_DATE_DESCRIPTION = "Only return entries due on or after this date (ISO yyyy-MM-dd). "
            + "Omit to leave the range open at the start";

    /** Shared by the list endpoints' range filter. */
    String TO_DATE_DESCRIPTION = "Only return entries due on or before this date (ISO yyyy-MM-dd). "
            + "Omit to leave the range open at the end";

    /** Entries per page when the caller does not ask for a size. */
    String DEFAULT_PAGE_SIZE = "50";

    /** Most entries a single page may hold. */
    int MAX_PAGE_SIZE = 200;

    /** Shared by the list endpoints' paging. */
    String PAGE_DESCRIPTION = "Page to return, counting from 0. A page past the last returns no entries";

    /** Shared by the list endpoints' paging. */
    String SIZE_DESCRIPTION = "Largest number of entries a page holds, from 1 to " + MAX_PAGE_SIZE;

    /** Field entries are sorted by when the caller does not choose one. */
    String DEFAULT_SORT_BY = "dueDate";

    /** Direction entries are sorted in when the caller does not choose one. */
    String DEFAULT_SORT_DIRECTION = "asc";

    /** Shared by the list endpoints' sorting. */
    String SORT_BY_DESCRIPTION = "Field to sort entries by: dueDate, entryDate, serviceName or totalAmount. "
            + "Entries alike in that field follow in due date, then service name, order, which keeps pages stable";

    /** Shared by the list endpoints' sorting. */
    String SORT_DIRECTION_DESCRIPTION = "Whether sortBy ascends (asc) or descends (desc); either case is accepted";

    /** Shared by the list endpoints' financial year filter. */
    String FINANCIAL_YEAR_DESCRIPTION = "Only return entries whose due date falls in this Indian financial "
            + "year (1 April to 31 March), written as yyyy-yy. Omit to return entries of every year";

    @Operation(
            summary = "Post due services to a company's ledger",
            description = "Creates a ledger entry for every service of the company that has fallen due "
                    + "on or before today. Each entry takes the service fee from the Service Catalog, "
                    + "takes off the discount granted on the service, and adds GST at the service's "
                    + "rate; every figure is stored separately. A recurring service is billed once for "
                    + "each date it fell due and has not been billed for, then moves on to its next "
                    + "due date after today. A ONE_OFF service is billed once and never again. "
                    + "Posting again when nothing new is due creates nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The entries created, earliest due first; empty when nothing was due",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(
                                    schema = @Schema(implementation = LedgerEntryResponse.class)))),
            @ApiResponse(responseCode = "400", description = "An identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, or the client holds no such company",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(COMPANY_PATH)
    ResponseEntity<List<LedgerEntryResponse>> postDueServices(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company to bill", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId);

    @Operation(
            summary = "Fetch a ledger entry of a company",
            description = "Returns one ledger entry billed to a company of the client, with every financial "
                    + "figure stored for it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ledger entry found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LedgerEntryResponse.class))),
            @ApiResponse(responseCode = "400", description = "An identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, the client holds no such company, or "
                            + "no such ledger entry was billed to the company",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(ENTRY_PATH)
    ResponseEntity<LedgerEntryResponse> getEntry(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @Parameter(description = "Identifier of the ledger entry", required = true,
                    example = "5e7c1a93-2b4d-4f86-9a0e-8d3b6c2f1e47")
            @PathVariable UUID entryId);

    @Operation(
            summary = "Summarise a company's account for a financial year",
            description = "Returns, for one company of the client and one Indian financial year (1 April to "
                    + "31 March): the total billed, the total payment received, what is pending (billed "
                    + "less received), the total discount given, and the date and amount of the last "
                    + "payment. Service charges count in the year they fell due, and payments in the year "
                    + "they were received; a payment is not matched to particular charges, so a payment "
                    + "settling an earlier year's charges reduces the pending amount of the year it was "
                    + "received in.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The year's summary; every amount zero, and no lastPayment, when the "
                            + "year has no entries",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CompanyLedgerSummaryResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is missing or is not a year such as 2026-27, or an "
                            + "identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, or the client holds no such company",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(COMPANY_SUMMARY_PATH)
    ResponseEntity<CompanyLedgerSummaryResponse> summarizeCompany(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @Parameter(description = "Indian financial year to summarise, written as yyyy-yy", required = true,
                    example = "2026-27")
            @RequestParam String financialYear);

    @Operation(
            summary = "Summarise a client's account for a financial year, across all of their companies",
            description = "Returns, for the client and one Indian financial year (1 April to 31 March): the "
                    + "total billed, the total payment received, what is pending (billed less received), "
                    + "and the total discount given, across every company of the client together; and the "
                    + "client's last payment, naming the company that made it. As for a single company, "
                    + "charges count in the year they fell due and payments in the year they were received.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The year's summary; every amount zero, and no lastPayment, when the "
                            + "year has no entries",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ClientLedgerSummaryResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is missing or is not a year such as 2026-27, or the "
                            + "identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No client carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(CLIENT_SUMMARY_PATH)
    ResponseEntity<ClientLedgerSummaryResponse> summarizeClient(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Indian financial year to summarise, written as yyyy-yy", required = true,
                    example = "2026-27")
            @RequestParam String financialYear);

    @Operation(
            summary = "Export a client's ledger for a financial year as an Excel workbook",
            description = "Downloads an .xlsx workbook for the client and one Indian financial year (1 April "
                    + "to 31 March), named after both, such as Rohan_Mehta_Ledger_FY2026-27.xlsx. Its Summary "
                    + "sheet holds the figures of the client's ledger summary for the year; its Ledger sheet "
                    + "lists every service charge and payment of the year across all of the client's "
                    + "companies, earliest first, with charges as debits, payments as credits, a running "
                    + "balance, and a totals row. One sheet per company follows, by company name, listing "
                    + "only that company's entries with its own running balance and totals; a company with "
                    + "no entries in the year gets headings and zero totals. As in the summary, charges count "
                    + "in the year they fell due and payments in the year they were received, and each "
                    + "balance starts from zero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The workbook, as an attachment; a year with no entries gives zero totals "
                            + "and an empty ledger",
                    headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                            description = "attachment, with the workbook's file name",
                            schema = @Schema(type = "string",
                                    example = "attachment; filename=\"Rohan_Mehta_Ledger_FY2026-27.xlsx\"")),
                    content = @Content(mediaType = LedgerExport.XLSX_MEDIA_TYPE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is missing or is not a year such as 2026-27, or the "
                            + "identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No client carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    // JSON is listed too, so an error can still be returned as an ErrorResponse.
    @GetMapping(value = CLIENT_EXPORT_PATH, produces = {LedgerExport.XLSX_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    ResponseEntity<byte[]> exportClient(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Indian financial year to export, written as yyyy-yy", required = true,
                    example = "2026-27")
            @RequestParam String financialYear);

    @Operation(
            summary = "Export a company's ledger for a financial year as an Excel workbook",
            description = "Downloads an .xlsx workbook for one company of the client and one Indian financial "
                    + "year (1 April to 31 March), named after the company and the year, such as "
                    + "Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.xlsx. Its Summary sheet holds the figures of "
                    + "the company's ledger summary for the year; its Ledger sheet lists every service "
                    + "charge and payment of the company in the year, earliest first, with charges as "
                    + "debits, payments as credits, a running balance, and a totals row. As in the summary, "
                    + "charges count in the year they fell due and payments in the year they were received, "
                    + "and the balance starts from zero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The workbook, as an attachment; a year with no entries gives zero totals "
                            + "and an empty ledger",
                    headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                            description = "attachment, with the workbook's file name",
                            schema = @Schema(type = "string",
                                    example = "attachment; filename=\"Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.xlsx\"")),
                    content = @Content(mediaType = LedgerExport.XLSX_MEDIA_TYPE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is missing or is not a year such as 2026-27, or an "
                            + "identifier is not a valid UUID",
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
    @GetMapping(value = COMPANY_EXPORT_PATH,
            produces = {LedgerExport.XLSX_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    ResponseEntity<byte[]> exportCompany(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company of the client", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @Parameter(description = "Indian financial year to export, written as yyyy-yy", required = true,
                    example = "2026-27")
            @RequestParam String financialYear);

    @Operation(
            summary = "Record a payment received from a company",
            description = "Records a payment received from one company of the client as an entry in its "
                    + "ledger, with entryType PAYMENT. The amount is given as a positive number and stored "
                    + "as negative, so the ledger's totals add up to what is still owed. The date received "
                    + "places the payment in the ledger: it is the entry's dueDate, decides its financial "
                    + "year, and cannot be after today. The payment is not matched to particular service "
                    + "charges; it reduces the company's balance as a whole.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment recorded",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LedgerEntryResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Request body failed validation, dateReceived is after today, or an "
                            + "identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, or the client holds no such company",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(value = PAYMENTS_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LedgerEntryResponse> recordPayment(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company that paid", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The payment received", required = true)
            @Valid @RequestBody PaymentRequest request);

    @Operation(
            summary = "List the payments received from a company",
            description = "Returns the payments recorded for one company of the client, a page at a time, "
                    + "earliest received first unless sortBy and sortDirection choose another order. Each is "
                    + "the ledger entry it was recorded as: entryType PAYMENT, totalAmount negative, and "
                    + "dueDate the date received. fromDate, toDate and financialYear filter on the date "
                    + "received, either end of the range may be left open, and given together only payments "
                    + "matching both are returned. Sorting by totalAmount ascending puts the largest payment "
                    + "first, as amounts are stored as negative. The summary counts and totals every matching "
                    + "payment across all pages.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "A page of the company's payments, with the totals of every matching payment; "
                            + "no payments and zero totals when none matches",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PaymentListResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "An identifier is not a valid UUID, a date is not a valid ISO date, "
                            + "fromDate is after toDate, financialYear is not a year such as 2026-27, page is "
                            + "negative, size is outside 1 to 200, or sortBy or sortDirection is not one of "
                            + "the values listed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, or the client holds no such company",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(PAYMENTS_PATH)
    ResponseEntity<PaymentListResponse> listPayments(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @Parameter(description = FROM_DATE_DESCRIPTION, example = "2026-04-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = TO_DATE_DESCRIPTION, example = "2027-03-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = FINANCIAL_YEAR_DESCRIPTION, example = "2026-27")
            @RequestParam(required = false) String financialYear,
            @Parameter(description = PAGE_DESCRIPTION, example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = SIZE_DESCRIPTION, example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size,
            @Parameter(description = SORT_BY_DESCRIPTION, schema = @Schema(type = "string",
                    allowableValues = {"dueDate", "entryDate", "serviceName", "totalAmount"},
                    defaultValue = DEFAULT_SORT_BY))
            @RequestParam(defaultValue = DEFAULT_SORT_BY) String sortBy,
            @Parameter(description = SORT_DIRECTION_DESCRIPTION, schema = @Schema(type = "string",
                    allowableValues = {"asc", "desc"}, defaultValue = DEFAULT_SORT_DIRECTION))
            @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection);

    @Operation(
            summary = "Update a payment received from a company",
            description = "Replaces every detail of a payment recorded for one company of the client: the "
                    + "amount, the date received, the method, the reference and the comment. An omitted "
                    + "reference or comment is cleared. The amount is given as a positive number and stored "
                    + "as negative; the financial year follows the new date received, which cannot be after "
                    + "today. When the payment was recorded (entryDate) does not change. paymentId is the "
                    + "ledger entry id the payment was recorded under.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LedgerEntryResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Request body failed validation, dateReceived is after today, or an "
                            + "identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, the client holds no such company, or "
                            + "no such ledger entry belongs to the company",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The ledger entry is a service charge, not a payment",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PutMapping(value = PAYMENT_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LedgerEntryResponse> updatePayment(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company that paid", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @Parameter(description = "Identifier of the payment's ledger entry", required = true,
                    example = "5e7c1a93-2b4d-4f86-9a0e-8d3b6c2f1e47")
            @PathVariable UUID paymentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The payment's corrected details", required = true)
            @Valid @RequestBody PaymentRequest request);

    @Operation(
            summary = "Correct the rates of a ledger entry",
            description = "Replaces the service fee, discount and GST rate of one ledger entry billed to a "
                    + "company of the client. The discount, taxable, GST and total amounts are worked out "
                    + "again from them, rounded the same way as when entries are posted, so the figures "
                    + "always add up. What the entry bills, its due date, financial year and entry date "
                    + "cannot change. Only service charges have rates; update a payment through its "
                    + "payment endpoint instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ledger entry corrected",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LedgerEntryResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Request body failed validation, or an identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, the client holds no such company, or "
                            + "no such ledger entry was billed to the company",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The ledger entry is a payment, which has no rates",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PutMapping(value = ENTRY_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LedgerEntryResponse> updateEntry(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @Parameter(description = "Identifier of the ledger entry", required = true,
                    example = "5e7c1a93-2b4d-4f86-9a0e-8d3b6c2f1e47")
            @PathVariable UUID entryId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Corrected rates for the ledger entry", required = true)
            @Valid @RequestBody LedgerEntryUpdateRequest request);

    @Operation(
            summary = "Delete a ledger entry",
            description = "Deletes one ledger entry of a company of the client: a payment recorded in error, "
                    + "or a service charge. For a service charge, the service it billed is left as it is: a "
                    + "ONE_OFF service stays marked as posted, and a recurring service keeps its next due "
                    + "date, so posting again does not bill the deleted date a second time.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ledger entry deleted"),
            @ApiResponse(responseCode = "400", description = "An identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, the client holds no such company, or "
                            + "no such ledger entry was billed to the company",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @DeleteMapping(ENTRY_PATH)
    ResponseEntity<Void> deleteEntry(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @Parameter(description = "Identifier of the ledger entry", required = true,
                    example = "5e7c1a93-2b4d-4f86-9a0e-8d3b6c2f1e47")
            @PathVariable UUID entryId);

    @Operation(
            summary = "List the ledger entries of a company",
            description = "Returns the ledger entries posted for one company of the client, earliest due "
                    + "first unless sortBy and sortDirection choose another order. Narrow them to a range "
                    + "of due dates with fromDate and toDate, either of which may be left open, and to an "
                    + "Indian financial year with financialYear; given together, only entries matching "
                    + "both are returned. Entries are returned a page "
                    + "at a time; the summary totals every matching entry across all pages, not just the "
                    + "page returned.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "A page of the company's ledger entries, with the totals of every matching "
                            + "entry; no entries and zero totals when none matches",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LedgerListResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "An identifier is not a valid UUID, a date is not a valid ISO date, "
                            + "fromDate is after toDate, financialYear is not a year such as 2026-27, page is "
                            + "negative, size is outside 1 to 200, or sortBy or sortDirection is not one of "
                            + "the values listed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, or the client holds no such company",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(COMPANY_PATH)
    ResponseEntity<LedgerListResponse> listCompanyEntries(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @Parameter(description = FROM_DATE_DESCRIPTION, example = "2026-04-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = TO_DATE_DESCRIPTION, example = "2027-03-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = FINANCIAL_YEAR_DESCRIPTION, example = "2026-27")
            @RequestParam(required = false) String financialYear,
            @Parameter(description = PAGE_DESCRIPTION, example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = SIZE_DESCRIPTION, example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size,
            @Parameter(description = SORT_BY_DESCRIPTION, schema = @Schema(type = "string",
                    allowableValues = {"dueDate", "entryDate", "serviceName", "totalAmount"},
                    defaultValue = DEFAULT_SORT_BY))
            @RequestParam(defaultValue = DEFAULT_SORT_BY) String sortBy,
            @Parameter(description = SORT_DIRECTION_DESCRIPTION, schema = @Schema(type = "string",
                    allowableValues = {"asc", "desc"}, defaultValue = DEFAULT_SORT_DIRECTION))
            @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection);

    @Operation(
            summary = "List the ledger entries of a client",
            description = "Returns the ledger entries posted for any company of the client, earliest due "
                    + "first unless sortBy and sortDirection choose another order. Each entry carries the "
                    + "companyId it was billed to. Narrow them to a range of "
                    + "due dates with fromDate and toDate, either of which may be left open, and to an "
                    + "Indian financial year with financialYear; given together, only entries matching "
                    + "both are returned. Entries are returned a page at a time; the "
                    + "summary totals every matching entry across all pages, not just the page returned.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "A page of the client's ledger entries, with the totals of every matching "
                            + "entry, broken down by financial year and by company; no entries and zero totals "
                            + "when none matches",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LedgerListResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "The identifier is not a valid UUID, a date is not a valid ISO date, "
                            + "fromDate is after toDate, financialYear is not a year such as 2026-27, page is "
                            + "negative, size is outside 1 to 200, or sortBy or sortDirection is not one of "
                            + "the values listed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No client carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(CLIENT_PATH)
    ResponseEntity<LedgerListResponse> listClientEntries(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = FROM_DATE_DESCRIPTION, example = "2026-04-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = TO_DATE_DESCRIPTION, example = "2027-03-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = FINANCIAL_YEAR_DESCRIPTION, example = "2026-27")
            @RequestParam(required = false) String financialYear,
            @Parameter(description = PAGE_DESCRIPTION, example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = SIZE_DESCRIPTION, example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size,
            @Parameter(description = SORT_BY_DESCRIPTION, schema = @Schema(type = "string",
                    allowableValues = {"dueDate", "entryDate", "serviceName", "totalAmount"},
                    defaultValue = DEFAULT_SORT_BY))
            @RequestParam(defaultValue = DEFAULT_SORT_BY) String sortBy,
            @Parameter(description = SORT_DIRECTION_DESCRIPTION, schema = @Schema(type = "string",
                    allowableValues = {"asc", "desc"}, defaultValue = DEFAULT_SORT_DIRECTION))
            @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection);
}
