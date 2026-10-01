package com.psc.cl.managercatalog.controller;

import com.psc.cl.config.ApiPaths;
import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.managercatalog.dto.ManagerRequest;
import com.psc.cl.managercatalog.dto.ManagerResponse;
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
 * Contract for every Manager Catalog endpoint.
 */
@Tag(name = "Manager Catalog",
        description = "Staff who can be assigned to client work, with their roles and contact details")
@RequestMapping(value = ManagerCatalogApi.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public interface ManagerCatalogApi {

    /** Base path shared by every Manager Catalog endpoint. */
    String BASE_PATH = ApiPaths.V1 + "/managers";

    /** Path of a single manager, relative to {@link #BASE_PATH}. */
    String BY_ID_PATH = "/{id}";

    @Operation(
            summary = "Create a manager",
            description = "Adds a manager to the catalog. Every field is mandatory, and the email "
                    + "address must be unique across the catalog, ignoring case.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Manager created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ManagerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A manager already holds this email address",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ManagerResponse> createManager(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Manager to add to the catalog", required = true)
            @Valid @RequestBody ManagerRequest request);

    @Operation(summary = "Fetch a manager", description = "Returns one manager by identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manager found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ManagerResponse.class))),
            @ApiResponse(responseCode = "400", description = "The identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No manager carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(BY_ID_PATH)
    ResponseEntity<ManagerResponse> getManager(
            @Parameter(description = "Identifier of the manager", required = true,
                    example = "e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47")
            @PathVariable UUID id);

    @Operation(
            summary = "List managers",
            description = "Returns every manager, ordered by last name then first name. The list "
                    + "is returned whole; it is not paged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The catalog, empty when nobody has been added yet",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(
                                    schema = @Schema(implementation = ManagerResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping
    ResponseEntity<List<ManagerResponse>> listManagers();

    @Operation(
            summary = "Update a manager",
            description = "Replaces a manager wholesale. Every field is mandatory, so nothing is "
                    + "left behind from the previous version.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manager updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ManagerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No manager carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Another manager already holds this email address",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PutMapping(value = BY_ID_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ManagerResponse> updateManager(
            @Parameter(description = "Identifier of the manager", required = true,
                    example = "e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47")
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New content for the manager", required = true)
            @Valid @RequestBody ManagerRequest request);

    @Operation(summary = "Delete a manager",
            description = "Removes a manager from the catalog.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Manager deleted"),
            @ApiResponse(responseCode = "400", description = "The identifier is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No manager carries this identifier",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @DeleteMapping(BY_ID_PATH)
    ResponseEntity<Void> deleteManager(
            @Parameter(description = "Identifier of the manager", required = true,
                    example = "e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47")
            @PathVariable UUID id);
}
