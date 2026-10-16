package com.psc.cl.servicecatalog.controller;

import com.psc.cl.config.ApiPaths;
import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.servicecatalog.dto.CatalogServiceRequest;
import com.psc.cl.servicecatalog.dto.CatalogServiceResponse;
import com.psc.cl.servicecatalog.dto.ServiceFeeBulkUpdateRequest;
import com.psc.cl.servicecatalog.model.ServiceCategory;
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
 * Contract for every Service Catalog endpoint.
 */
@Tag(name = "Service Catalog",
        description = "Billable services the firm offers, with their fees and billing schedules")
@RequestMapping(value = ServiceCatalogApi.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public interface ServiceCatalogApi {

    /** Base path shared by every Service Catalog endpoint. */
    String BASE_PATH = ApiPaths.V1 + "/services";

    /** Path of a single service, relative to {@link #BASE_PATH}. */
    String BY_ID_PATH = "/{id}";

    /** Path the standard fees of several services are changed through, relative to {@link #BASE_PATH}. */
    String FEES_PATH = "/fees";

    /** Path the service categories are listed at, relative to {@link #BASE_PATH}. */
    String CATEGORIES_PATH = "/categories";

    @Operation(
            summary = "Create a service",
            description = "Adds a service to the catalog. The invoice schedule must match the "
                    + "billing frequency: ONE_OFF carries no schedule, MONTHLY needs a day of "
                    + "month, QUARTERLY needs a month of quarter plus a day of month, and ANNUAL "
                    + "needs a month plus a day of month. The GST rate defaults to 18 percent when "
                    + "omitted. Service names are unique across the catalog, ignoring case.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Service created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CatalogServiceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A service with this name already exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CatalogServiceResponse> createService(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Service to add to the catalog", required = true)
            @Valid @RequestBody CatalogServiceRequest request);

    @Operation(
            summary = "Fetch a service",
            description = "Returns one service by its identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CatalogServiceResponse.class))),
            @ApiResponse(responseCode = "400", description = "The identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No service carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(BY_ID_PATH)
    ResponseEntity<CatalogServiceResponse> getService(
            @Parameter(description = "Identifier of the service", required = true,
                    example = "b41e7a52-9c3d-4f16-8ad0-5e2b9c7f1d38")
            @PathVariable UUID id);

    @Operation(
            summary = "List services",
            description = "Returns every service in the catalog, ordered by name. The list is "
                    + "returned whole; it is not paged. Each service also carries what it was billed across "
                    + "every client (totalBilled): the sum of its service charges in the ledger, GST "
                    + "included, or zero when there are none. Given a financialYear, only charges falling "
                    + "due in that year count, and financialYear is echoed back; without one, every charge "
                    + "due to date counts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The catalog, empty when nothing has "
                    + "been added yet",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(
                                    schema = @Schema(implementation = CatalogServiceResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping
    ResponseEntity<List<CatalogServiceResponse>> listServices(
            @Parameter(description = "Indian financial year (1 April to 31 March), written as yyyy-yy, to "
                    + "total each service's billed amount for. Omit to total every year to date",
                    example = "2026-27")
            @RequestParam(required = false) String financialYear);

    @Operation(
            summary = "List service categories",
            description = "Returns every category a service can belong to, in their declared order. "
                    + "These are the values accepted for a service's category.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Every service category",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(
                                    schema = @Schema(implementation = ServiceCategory.class)))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    // A literal path, so Spring routes it here rather than to getService's /{id}.
    @GetMapping(CATEGORIES_PATH)
    ResponseEntity<List<ServiceCategory>> listCategories();

    @Operation(
            summary = "Update a service",
            description = "Replaces a service wholesale, so omitted fields are cleared rather than "
                    + "left untouched. The same invoice schedule rules as create apply. The "
                    + "subscriber count cannot be changed through this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CatalogServiceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No service carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Another service already uses this name",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PutMapping(value = BY_ID_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CatalogServiceResponse> updateService(
            @Parameter(description = "Identifier of the service", required = true,
                    example = "b41e7a52-9c3d-4f16-8ad0-5e2b9c7f1d38")
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New content for the service", required = true)
            @Valid @RequestBody CatalogServiceRequest request);

    @Operation(
            summary = "Update the standard fees of several services",
            description = "Changes the standard fee of each service named, in one go, and nothing else "
                    + "about them. The update is all or nothing: if any service is unknown, or one is "
                    + "named twice, no fee changes. Up to " + ServiceFeeBulkUpdateRequest.MAX_UPDATES
                    + " services may be changed at once. Charges already posted to the ledger keep the "
                    + "fee they were posted with; services due afterwards are billed at the new fee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Fees updated; the updated services, in the order the request named them",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(
                                    schema = @Schema(implementation = CatalogServiceResponse.class)))),
            @ApiResponse(responseCode = "400",
                    description = "Request body failed validation, such as a missing or negative fee or "
                            + "an empty list, or a service is named more than once",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "One or more identifiers name no service; the message lists each",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    // A literal path, so Spring routes it here rather than to updateService's /{id}.
    @PutMapping(value = FEES_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<CatalogServiceResponse>> updateFees(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The new standard fee of each service to change", required = true)
            @Valid @RequestBody ServiceFeeBulkUpdateRequest request);

    @Operation(
            summary = "Delete a service",
            description = "Removes a service from the catalog. A service that companies still "
                    + "subscribe to cannot be deleted and is rejected with 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Service deleted"),
            @ApiResponse(responseCode = "400", description = "The identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No service carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Companies still subscribe to the service",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @DeleteMapping(BY_ID_PATH)
    ResponseEntity<Void> deleteService(
            @Parameter(description = "Identifier of the service", required = true,
                    example = "b41e7a52-9c3d-4f16-8ad0-5e2b9c7f1d38")
            @PathVariable UUID id);
}
