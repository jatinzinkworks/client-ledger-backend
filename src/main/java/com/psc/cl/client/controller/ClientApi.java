package com.psc.cl.client.controller;

import com.psc.cl.client.dto.ClientManagerBulkUpdateRequest;
import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.ClientSummaryResponse;
import com.psc.cl.client.dto.ClientUpdateRequest;
import com.psc.cl.config.ApiPaths;
import com.psc.cl.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

/**
 * Contract for every Client endpoint.
 */
@Tag(name = "Clients", description = "People the firm works for and the manager who looks after them")
@RequestMapping(value = ClientApi.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public interface ClientApi {

    /** Base path shared by every Client endpoint. */
    String BASE_PATH = ApiPaths.V1 + "/clients";

    /** Path of a single client, relative to {@link #BASE_PATH}. */
    String BY_ID_PATH = "/{id}";

    /** Path several clients are assigned to a manager through, relative to {@link #BASE_PATH}. */
    String MANAGER_PATH = "/manager";

    @Operation(
            summary = "Create a client",
            description = "Adds a client together with the companies they hold. Every field is "
                    + "mandatory, at least one company must be supplied, and the assigned manager "
                    + "must already exist in the Manager Catalog. A company may bring an opening balance: "
                    + "what it owed (positive) or was owed (negative) when taken on, dated "
                    + "openingBalanceDate or the day the client is added. It is recorded in the company's "
                    + "ledger as an OPENING_BALANCE entry, counted in what is pending but not as billed. "
                    + "Nothing is created when any of it cannot be.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Client created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ClientResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Request body failed validation, such as an openingBalanceDate without an "
                            + "openingBalance, or an openingBalanceDate after today",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The assigned manager does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ClientResponse> createClient(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Client to add, with their companies", required = true)
            @Valid @RequestBody ClientRequest request);

    @Operation(
            summary = "Fetch a client",
            description = "Returns one client by identifier, with their companies in full and the "
                    + "name of the manager who looks after them.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ClientResponse.class))),
            @ApiResponse(responseCode = "400", description = "The identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No client carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(BY_ID_PATH)
    ResponseEntity<ClientResponse> getClient(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID id);

    @Operation(
            summary = "Update a client",
            description = "Replaces a client's own fields wholesale. The client's companies are left "
                    + "as they are; add or delete them through the company endpoints. An omitted "
                    + "client discount resets to zero. Companies without a discount of their own take the "
                    + "new client discount.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ClientResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Request body failed validation, or the identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, or the assigned manager does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PutMapping(value = BY_ID_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ClientResponse> updateClient(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New content for the client, excluding their companies",
                    required = true)
            @Valid @RequestBody ClientUpdateRequest request);

    @Operation(
            summary = "Assign several clients to a manager",
            description = "Assigns every client named to one manager, in one go, and changes nothing else "
                    + "about them. The update is all or nothing: if the manager or any client is unknown, no "
                    + "client is reassigned. A client named more than once is assigned once. Up to "
                    + ClientManagerBulkUpdateRequest.MAX_CLIENTS + " clients may be assigned at once.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Clients reassigned; the updated clients, in the order the request first "
                            + "named them",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(
                                    schema = @Schema(implementation = ClientSummaryResponse.class)))),
            @ApiResponse(responseCode = "400",
                    description = "Request body failed validation, such as a missing manager or an empty "
                            + "client list",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "The manager does not exist, or one or more identifiers name no client; "
                            + "the message lists each",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    // A literal path, so Spring routes it here rather than to updateClient's /{id}.
    @PutMapping(value = MANAGER_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<ClientSummaryResponse>> updateManager(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The manager and the clients to assign to them", required = true)
            @Valid @RequestBody ClientManagerBulkUpdateRequest request);

    @Operation(
            summary = "Delete a client",
            description = "Removes a client together with their companies. A client with services "
                    + "assigned to any of their companies cannot be deleted; remove those services first. Nor "
                    + "can a client with payments or service charges in the ledger of any company: a ledger is a "
                    + "financial record and is never deleted along with its client. Opening balances alone do "
                    + "not stop it; they are deleted with the client.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Client deleted"),
            @ApiResponse(responseCode = "400", description = "The identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No client carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Services are still assigned to, or payments or service charges recorded "
                            + "against, one or more of the client's companies; the message says which",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @DeleteMapping(BY_ID_PATH)
    ResponseEntity<Void> deleteClient(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID id);

    @Operation(
            summary = "List clients",
            description = "Returns every client, or only the clients of one manager when managerId is "
                    + "given, ordered by last name then first name. Each entry names the client's "
                    + "companies only; fetch a client by id for the full company details. Each entry also "
                    + "carries what the client still owes (totalPending): everything billed to all of their "
                    + "companies less every payment received, across all financial years. The list is "
                    + "returned whole; it is not paged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The matching clients, empty when there are none",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(
                                    schema = @Schema(implementation = ClientSummaryResponse.class)))),
            @ApiResponse(responseCode = "400", description = "managerId is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping
    ResponseEntity<List<ClientSummaryResponse>> listClients(
            @Parameter(description = "Only return clients assigned to this manager. Omit to return "
                    + "every client. An unknown manager returns an empty list",
                    example = "e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47")
            @RequestParam(required = false) UUID managerId);
}
