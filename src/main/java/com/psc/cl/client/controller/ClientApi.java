package com.psc.cl.client.controller;

import com.psc.cl.client.dto.ClientRequest;
import com.psc.cl.client.dto.ClientResponse;
import com.psc.cl.client.dto.ClientSummaryResponse;
import com.psc.cl.client.dto.ClientUpdateRequest;
import com.psc.cl.client.dto.CompanyResponse;
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
@Tag(name = "Clients",
        description = "People the firm works for, the manager who looks after them and the companies they hold")
@RequestMapping(value = ClientApi.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public interface ClientApi {

    /** Base path shared by every Client endpoint. */
    String BASE_PATH = ApiPaths.V1 + "/clients";

    /** Path of a single client, relative to {@link #BASE_PATH}. */
    String BY_ID_PATH = "/{id}";

    /** Path of the companies of a client, relative to {@link #BASE_PATH}. */
    String COMPANIES_PATH = BY_ID_PATH + "/companies";

    /** Path of a single company of a client, relative to {@link #BASE_PATH}. */
    String COMPANY_BY_ID_PATH = COMPANIES_PATH + "/{companyId}";

    @Operation(
            summary = "Create a client",
            description = "Adds a client together with the companies they hold. Every field is "
                    + "mandatory, at least one company must be supplied, and the assigned manager "
                    + "must already exist in the Manager Catalog.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Client created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ClientResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
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
            description = "Replaces a client wholesale, together with their full set of companies. "
                    + "A company carrying an id is updated in place and keeps that id, a company "
                    + "without an id is added, and any existing company left out is removed. An "
                    + "omitted client discount resets to zero, and companies without a discount of "
                    + "their own take the client's.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ClientResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Request body failed validation, or the identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, the assigned manager does not "
                            + "exist, or a company id is not one of this client's companies",
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
                    description = "New content for the client and their full set of companies",
                    required = true)
            @Valid @RequestBody ClientUpdateRequest request);

    @Operation(
            summary = "Delete a client",
            description = "Removes a client together with their companies. A client with services "
                    + "assigned to any of their companies cannot be deleted; remove those services first.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Client deleted"),
            @ApiResponse(responseCode = "400", description = "The identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No client carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Services are still assigned to one or more of the client's companies",
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
                    + "companies only; fetch a client by id for the full company details. The list is "
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

    @Operation(
            summary = "List the companies of a client",
            description = "Returns every company the client holds, in full, ordered by company name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The client's companies",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(
                                    schema = @Schema(implementation = CompanyResponse.class)))),
            @ApiResponse(responseCode = "400", description = "The identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No client carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(COMPANIES_PATH)
    ResponseEntity<List<CompanyResponse>> listCompanies(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID id);

    @Operation(
            summary = "Fetch a company of a client",
            description = "Returns one company of the client. A company that exists but belongs "
                    + "to a different client is reported as not found.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CompanyResponse.class))),
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
    @GetMapping(COMPANY_BY_ID_PATH)
    ResponseEntity<CompanyResponse> getCompany(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID id,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId);

    @Operation(
            summary = "Delete a company of a client",
            description = "Removes one company of the client. A client must keep at least one company, "
                    + "so their only company cannot be deleted; delete the client instead. A company "
                    + "with services assigned cannot be deleted; remove those services first.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Company deleted"),
            @ApiResponse(responseCode = "400", description = "An identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, or the client holds no such company",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "It is the client's only company (BUSINESS_RULE_VIOLATION), or services "
                            + "are still assigned to it (RESOURCE_IN_USE)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @DeleteMapping(COMPANY_BY_ID_PATH)
    ResponseEntity<Void> deleteCompany(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID id,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId);
}
