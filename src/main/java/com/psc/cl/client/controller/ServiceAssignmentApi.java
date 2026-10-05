package com.psc.cl.client.controller;

import com.psc.cl.client.dto.ServiceAssignmentRequest;
import com.psc.cl.client.dto.ServiceAssignmentResponse;
import com.psc.cl.client.dto.ServiceAssignmentUpdateRequest;
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

import java.util.List;
import java.util.UUID;

/**
 * Contract for the services delivered to a company of a client.
 */
@Tag(name = "Client Services",
        description = "Services from the Service Catalog delivered to a client's companies")
@RequestMapping(value = ServiceAssignmentApi.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public interface ServiceAssignmentApi {

    /** Base path shared by every endpoint over the services of one company of a client. */
    String BASE_PATH = ClientApi.BASE_PATH + "/{clientId}/companies/{companyId}/services";

    /** Path of a single assigned service, relative to {@link #BASE_PATH}. */
    String BY_ID_PATH = "/{assignmentId}";

    @Operation(
            summary = "Assign a service to a company",
            description = "Assigns a service from the Service Catalog to one company of the client. "
                    + "A company can take many services, and the same service more than once, for "
                    + "example on different delivery dates. When no discount is given, the service "
                    + "takes the company's discount. The service type (the catalog billing frequency) "
                    + "and the next due date (the first scheduled invoice date on or after the "
                    + "delivery date; the delivery date itself for ONE_OFF) are worked out from the "
                    + "catalog service.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Service assigned",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ServiceAssignmentResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Request body failed validation, or an identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, the client holds no such "
                            + "company, or the service does not exist in the Service Catalog",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ServiceAssignmentResponse> assignService(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company receiving the service", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Service to assign to the company", required = true)
            @Valid @RequestBody ServiceAssignmentRequest request);

    @Operation(
            summary = "List the services of a company",
            description = "Returns every service assigned to one company of the client, most recent "
                    + "delivery first. The list is returned whole; it is not paged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The company's services, empty when none has been assigned",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(
                                    schema = @Schema(implementation = ServiceAssignmentResponse.class)))),
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
    @GetMapping
    ResponseEntity<List<ServiceAssignmentResponse>> listServices(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId);

    @Operation(summary = "Fetch a service of a company",
            description = "Returns one service assigned to a company of the client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assigned service found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ServiceAssignmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "An identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, the client holds no such "
                            + "company, or the company holds no such assigned service",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(BY_ID_PATH)
    ResponseEntity<ServiceAssignmentResponse> getService(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @Parameter(description = "Identifier of the assigned service", required = true,
                    example = "9d2a7c40-6e1b-4f53-b8a9-0c3e5d7f1a62")
            @PathVariable UUID assignmentId);

    @Operation(
            summary = "Update a service of a company",
            description = "Replaces the delivery date, discount and comment of a service assigned to a "
                    + "company of the client. The catalog service itself cannot change; remove this "
                    + "assignment and assign the other service instead. An omitted discount resets to "
                    + "the company's, and an omitted comment is cleared. The service type and next due "
                    + "date are worked out again from the catalog service's current schedule.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assigned service updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ServiceAssignmentResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Request body failed validation, or an identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, the client holds no such "
                            + "company, or the company holds no such assigned service",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PutMapping(value = BY_ID_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ServiceAssignmentResponse> updateService(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @Parameter(description = "Identifier of the assigned service", required = true,
                    example = "9d2a7c40-6e1b-4f53-b8a9-0c3e5d7f1a62")
            @PathVariable UUID assignmentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New details for the assigned service", required = true)
            @Valid @RequestBody ServiceAssignmentUpdateRequest request);

    @Operation(
            summary = "Remove a service from a company",
            description = "Removes one assigned service from a company of the client. When it was the "
                    + "company's last assignment of that service, the company no longer counts "
                    + "towards the catalog service's usedByCompanies.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Assigned service removed"),
            @ApiResponse(responseCode = "400", description = "An identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No client carries this identifier, the client holds no such "
                            + "company, or the company holds no such assigned service",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @DeleteMapping(BY_ID_PATH)
    ResponseEntity<Void> removeService(
            @Parameter(description = "Identifier of the client", required = true,
                    example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
            @PathVariable UUID clientId,
            @Parameter(description = "Identifier of the company", required = true,
                    example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
            @PathVariable UUID companyId,
            @Parameter(description = "Identifier of the assigned service", required = true,
                    example = "9d2a7c40-6e1b-4f53-b8a9-0c3e5d7f1a62")
            @PathVariable UUID assignmentId);
}
