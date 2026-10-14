package com.psc.cl.overview.controller;

import com.psc.cl.config.ApiPaths;
import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.globalsettings.model.PaymentTerms;
import com.psc.cl.overview.dto.AgeingResponse;
import com.psc.cl.overview.dto.BilledByCategoryResponse;
import com.psc.cl.overview.dto.MonthlyTrendResponse;
import com.psc.cl.overview.dto.OverviewResponse;
import com.psc.cl.overview.dto.RecentPaymentsResponse;
import com.psc.cl.overview.dto.TopBilledClientsResponse;
import com.psc.cl.overview.dto.TopBilledCompaniesResponse;
import com.psc.cl.overview.dto.TopBilledManagersResponse;
import com.psc.cl.overview.dto.TopPendingClientsResponse;
import com.psc.cl.overview.dto.TopPendingCompaniesResponse;
import com.psc.cl.overview.dto.TopPendingManagersResponse;
import com.psc.cl.overview.service.OverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Contract for the firm-wide overview.
 */
@Tag(name = "Overview", description = "The firm's position across every client")
@RequestMapping(value = OverviewApi.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public interface OverviewApi {

    /** Base path shared by every Overview endpoint. */
    String BASE_PATH = ApiPaths.V1 + "/overview";

    /** Path of the clients owing most, relative to {@link #BASE_PATH}. */
    String TOP_PENDING_CLIENTS_PATH = "/top-pending-clients";

    /** Clients ranked when the caller does not ask for a number. */
    String DEFAULT_TOP_CLIENTS = "10";

    /** Path of the clients billed most, relative to {@link #BASE_PATH}. */
    String TOP_BILLED_CLIENTS_PATH = "/top-billed-clients";

    /** Path of the managers whose clients owe most, relative to {@link #BASE_PATH}. */
    String TOP_PENDING_MANAGERS_PATH = "/top-pending-managers";

    /** Managers ranked when the caller does not ask for a number. */
    String DEFAULT_TOP_MANAGERS = "10";

    /** Path of the managers whose clients were billed most, relative to {@link #BASE_PATH}. */
    String TOP_BILLED_MANAGERS_PATH = "/top-billed-managers";

    /** Path of the companies owing most, relative to {@link #BASE_PATH}. */
    String TOP_PENDING_COMPANIES_PATH = "/top-pending-companies";

    /** Companies ranked when the caller does not ask for a number. */
    String DEFAULT_TOP_COMPANIES = "10";

    /** Path of the companies billed most, relative to {@link #BASE_PATH}. */
    String TOP_BILLED_COMPANIES_PATH = "/top-billed-companies";

    /** Path of the latest payments received, relative to {@link #BASE_PATH}. */
    String RECENT_PAYMENTS_PATH = "/recent-payments";

    /** Payments listed when the caller does not ask for a number. */
    String DEFAULT_RECENT_PAYMENTS = "20";

    /** Path of the receivables ageing, relative to {@link #BASE_PATH}. */
    String AGEING_PATH = "/ageing";

    /** Path of the monthly billed and received trend, relative to {@link #BASE_PATH}. */
    String MONTHLY_PATH = "/monthly";

    /** Path of the billing by service category, relative to {@link #BASE_PATH}. */
    String BILLED_BY_CATEGORY_PATH = "/billed-by-category";

    @Operation(
            summary = "Show the firm's position for a financial year",
            description = "Returns, across every client and one Indian financial year (1 April to 31 March): "
                    + "the total billed, the total payment received, what is pending (billed less received) "
                    + "and the total discount given, worked out as each client's ledger summary does. Charges "
                    + "count in the year they fell due and payments in the year they were received; earlier "
                    + "years' balances are not brought forward. Also returns how many clients, companies and "
                    + "assigned services there are now. previousToDate gives the same figures for the previous "
                    + "year up to the same point in it (today a year earlier while the year is running, the "
                    + "whole previous year once it is over), so this year to date compares fairly with last.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The overview; every amount zero when the year has no ledger entries",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = OverviewResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is missing or is not a year such as 2026-27",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping
    ResponseEntity<OverviewResponse> overview(
            @Parameter(description = "Indian financial year to total, written as yyyy-yy", required = true,
                    example = "2026-27")
            @RequestParam String financialYear);

    @Operation(
            summary = "Rank the clients owing most",
            description = "Returns the clients still owing, most first, with what each was billed and paid "
                    + "and what remains pending (billed less received) across all of their companies, and "
                    + "their manager. Given a financialYear, only charges falling due and payments received "
                    + "in that year count, as in each client's ledger summary; without one, every year to "
                    + "date counts, as in the client list's totalPending. Clients settled or in credit are "
                    + "left out; those owing the same are ranked in identifier order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The ranking; no clients when nobody owes anything",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TopPendingClientsResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is not a year such as 2026-27, or limit is not a whole "
                            + "number from 1 to " + OverviewService.MAX_TOP_CLIENTS,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(TOP_PENDING_CLIENTS_PATH)
    ResponseEntity<TopPendingClientsResponse> topPendingClients(
            @Parameter(description = "Indian financial year to rank by, written as yyyy-yy. Omit to rank by "
                    + "every year to date", example = "2026-27")
            @RequestParam(required = false) String financialYear,
            @Parameter(description = "Most clients to return, from 1 to " + OverviewService.MAX_TOP_CLIENTS,
                    example = "10")
            @RequestParam(defaultValue = DEFAULT_TOP_CLIENTS) int limit);

    @Operation(
            summary = "Rank the clients billed most",
            description = "Returns the clients billed anything, most billed first, with what each was billed "
                    + "(service charges, GST included, after discount) and paid across all of their companies, "
                    + "what remains pending, and their manager. Given a financialYear, only charges falling due "
                    + "and payments received in that year count, as in each client's ledger summary; without "
                    + "one, every year to date counts. Clients billed the same are ranked in identifier order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The ranking; no clients when nothing was billed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TopBilledClientsResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is not a year such as 2026-27, or limit is not a whole "
                            + "number from 1 to " + OverviewService.MAX_TOP_CLIENTS,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(TOP_BILLED_CLIENTS_PATH)
    ResponseEntity<TopBilledClientsResponse> topBilledClients(
            @Parameter(description = "Indian financial year to rank by, written as yyyy-yy. Omit to rank by "
                    + "every year to date", example = "2026-27")
            @RequestParam(required = false) String financialYear,
            @Parameter(description = "Most clients to return, from 1 to " + OverviewService.MAX_TOP_CLIENTS,
                    example = "10")
            @RequestParam(defaultValue = DEFAULT_TOP_CLIENTS) int limit);

    @Operation(
            summary = "Rank the managers whose clients owe most",
            description = "Returns the managers whose clients still owe, most first, with what those clients "
                    + "were billed and paid and what remains pending (billed less received) across all of "
                    + "them, and how many clients each manager has. Clients count towards the manager they "
                    + "are assigned to now, including their history from before. Given a financialYear, only "
                    + "charges falling due and payments received in that year count; without one, every year "
                    + "to date counts, as in each manager's totalPending. Managers whose clients are settled "
                    + "or in credit overall are left out; those owing the same are ranked in identifier order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The ranking; no managers when nobody owes anything",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TopPendingManagersResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is not a year such as 2026-27, or limit is not a whole "
                            + "number from 1 to " + OverviewService.MAX_TOP_MANAGERS,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(TOP_PENDING_MANAGERS_PATH)
    ResponseEntity<TopPendingManagersResponse> topPendingManagers(
            @Parameter(description = "Indian financial year to rank by, written as yyyy-yy. Omit to rank by "
                    + "every year to date", example = "2026-27")
            @RequestParam(required = false) String financialYear,
            @Parameter(description = "Most managers to return, from 1 to " + OverviewService.MAX_TOP_MANAGERS,
                    example = "10")
            @RequestParam(defaultValue = DEFAULT_TOP_MANAGERS) int limit);

    @Operation(
            summary = "Rank the managers whose clients were billed most",
            description = "Returns the managers whose clients were billed anything, most billed first, with "
                    + "what was billed to those clients (service charges, GST included, after discount), what "
                    + "was received from them and what remains pending, and how many clients each manager "
                    + "has. Clients count towards the manager they are assigned to now, including their "
                    + "history from before. Given a financialYear, only charges falling due and payments "
                    + "received in that year count; without one, every year to date counts. Managers billed "
                    + "the same are ranked in identifier order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The ranking; no managers when nothing was billed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TopBilledManagersResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is not a year such as 2026-27, or limit is not a whole "
                            + "number from 1 to " + OverviewService.MAX_TOP_MANAGERS,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(TOP_BILLED_MANAGERS_PATH)
    ResponseEntity<TopBilledManagersResponse> topBilledManagers(
            @Parameter(description = "Indian financial year to rank by, written as yyyy-yy. Omit to rank by "
                    + "every year to date", example = "2026-27")
            @RequestParam(required = false) String financialYear,
            @Parameter(description = "Most managers to return, from 1 to " + OverviewService.MAX_TOP_MANAGERS,
                    example = "10")
            @RequestParam(defaultValue = DEFAULT_TOP_MANAGERS) int limit);

    @Operation(
            summary = "Rank the companies owing most",
            description = "Returns the companies still owing, most first, with what each was billed and paid "
                    + "and what remains pending (billed less received), and the client each belongs to. Given "
                    + "a financialYear, only charges falling due and payments received in that year count, as "
                    + "in each company's ledger summary; without one, every year to date counts. Companies "
                    + "settled or in credit are left out; those owing the same are ranked in identifier order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The ranking; no companies when nobody owes anything",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TopPendingCompaniesResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is not a year such as 2026-27, or limit is not a whole "
                            + "number from 1 to " + OverviewService.MAX_TOP_COMPANIES,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(TOP_PENDING_COMPANIES_PATH)
    ResponseEntity<TopPendingCompaniesResponse> topPendingCompanies(
            @Parameter(description = "Indian financial year to rank by, written as yyyy-yy. Omit to rank by "
                    + "every year to date", example = "2026-27")
            @RequestParam(required = false) String financialYear,
            @Parameter(description = "Most companies to return, from 1 to " + OverviewService.MAX_TOP_COMPANIES,
                    example = "10")
            @RequestParam(defaultValue = DEFAULT_TOP_COMPANIES) int limit);

    @Operation(
            summary = "Rank the companies billed most",
            description = "Returns the companies billed anything, most billed first, with what each was "
                    + "billed (service charges, GST included, after discount) and paid, what remains pending, "
                    + "and the client each belongs to. Given a financialYear, only charges falling due and "
                    + "payments received in that year count, as in each company's ledger summary; without one, "
                    + "every year to date counts. Companies billed the same are ranked in identifier order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The ranking; no companies when nothing was billed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TopBilledCompaniesResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is not a year such as 2026-27, or limit is not a whole "
                            + "number from 1 to " + OverviewService.MAX_TOP_COMPANIES,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(TOP_BILLED_COMPANIES_PATH)
    ResponseEntity<TopBilledCompaniesResponse> topBilledCompanies(
            @Parameter(description = "Indian financial year to rank by, written as yyyy-yy. Omit to rank by "
                    + "every year to date", example = "2026-27")
            @RequestParam(required = false) String financialYear,
            @Parameter(description = "Most companies to return, from 1 to " + OverviewService.MAX_TOP_COMPANIES,
                    example = "10")
            @RequestParam(defaultValue = DEFAULT_TOP_COMPANIES) int limit);

    @Operation(
            summary = "List the latest payments received",
            description = "Returns the latest payments received across every client and company, most "
                    + "recently received first; payments received the same day come latest recorded first. "
                    + "Each carries its amount as a positive number, how and when it was received, and the "
                    + "company and client that made it. 20 are returned unless limit asks otherwise.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The latest payments; none when nothing has been received",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RecentPaymentsResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "limit is not a whole number from 1 to " + OverviewService.MAX_RECENT_PAYMENTS,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(RECENT_PAYMENTS_PATH)
    ResponseEntity<RecentPaymentsResponse> recentPayments(
            @Parameter(description = "Most payments to return, from 1 to " + OverviewService.MAX_RECENT_PAYMENTS,
                    example = "20")
            @RequestParam(defaultValue = DEFAULT_RECENT_PAYMENTS) int limit);

    @Operation(
            summary = "Show how long what the firm is owed has been due",
            description = "Returns, as of today, what remains unpaid across every company split into ranges of "
                    + "days past due: 0-30, 31-60, 61-90 and 90+, each with how many companies owe in it. Days "
                    + "past due run from the date a service charge fell due. Payments are not matched to charges, "
                    + "so each company's payments are applied to its oldest charges first; companies settled or "
                    + "in credit owe nothing. Also returns what has been due for longer than the payment terms' "
                    + "markOverdueAfterDays (" + PaymentTerms.DEFAULT_MARK_OVERDUE_AFTER_DAYS
                    + " when no payment terms are saved) as overdue.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The ageing; every range zero when nobody owes anything",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AgeingResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(AGEING_PATH)
    ResponseEntity<AgeingResponse> ageing();

    @Operation(
            summary = "Show what was billed and received each month of a financial year",
            description = "Returns all twelve months of one Indian financial year, April first, each with what "
                    + "was billed across every client (service charges falling due in the month, GST included, "
                    + "after discount), what was received in the month and the discount given. Months without "
                    + "entries are listed with zeros.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The year's months",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MonthlyTrendResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is missing or is not a year such as 2026-27",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(MONTHLY_PATH)
    ResponseEntity<MonthlyTrendResponse> monthlyTrend(
            @Parameter(description = "Indian financial year to show, written as yyyy-yy", required = true,
                    example = "2026-27")
            @RequestParam String financialYear);

    @Operation(
            summary = "Split a financial year's billing across the practice areas",
            description = "Returns every service category (Compliance, Tax, Audit, Accounting and Advisory), "
                    + "billed most first, each with what its catalog services were billed in the year (service "
                    + "charges falling due in it, GST included, after discount) and how many of them were "
                    + "billed. Categories are those the services are in now. Given topServices, each category "
                    + "also lists that many of its most billed services.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Every category; zero for those with nothing billed in the year",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BilledByCategoryResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "financialYear is missing or is not a year such as 2026-27, or topServices "
                            + "is not a whole number from 0 to " + OverviewService.MAX_TOP_SERVICES_PER_CATEGORY,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(BILLED_BY_CATEGORY_PATH)
    ResponseEntity<BilledByCategoryResponse> billedByCategory(
            @Parameter(description = "Indian financial year to split, written as yyyy-yy", required = true,
                    example = "2026-27")
            @RequestParam String financialYear,
            @Parameter(description = "Most billed services to list in each category, from 0 to "
                    + OverviewService.MAX_TOP_SERVICES_PER_CATEGORY + "; 0 lists none", example = "3")
            @RequestParam(defaultValue = "0") int topServices);
}
