package com.psc.cl.servicecatalog.controller;

import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.ledger.dto.LedgerExport;
import com.psc.cl.servicecatalog.dto.ServiceCatalogImportResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * Contract for importing catalog services in bulk from an Excel workbook.
 */
@Tag(name = "Service Catalog",
        description = "Billable services the firm offers, with their fees and billing schedules")
@RequestMapping(value = ServiceCatalogApi.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public interface ServiceCatalogImportApi {

    /** Path a workbook is imported through, relative to {@link ServiceCatalogApi#BASE_PATH}. */
    String IMPORT_PATH = "/import";

    /** Path the workbook to fill in is downloaded from, relative to {@link ServiceCatalogApi#BASE_PATH}. */
    String TEMPLATE_PATH = IMPORT_PATH + "/template";

    /** Name the template is downloaded under. */
    String TEMPLATE_FILE_NAME = "Service_Catalog_Import_Template.xlsx";

    @Operation(
            summary = "Download the service catalog import template",
            description = "Returns the Excel workbook to fill in for POST /services/import. Its Services sheet "
                    + "takes one row per service: Service Name, Description, Category, Billing Frequency, "
                    + "Standard Fee, GST %, Invoice Day, Invoice Month of Quarter and Invoice Month. An "
                    + "Instructions sheet describes every column with an example, and which schedule columns "
                    + "each billing frequency needs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The template, as an attachment",
                    headers = @Header(name = HttpHeaders.CONTENT_DISPOSITION,
                            description = "attachment, with the template's file name",
                            schema = @Schema(type = "string",
                                    example = "attachment; filename=\"" + TEMPLATE_FILE_NAME + "\"")),
                    content = @Content(mediaType = LedgerExport.XLSX_MEDIA_TYPE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    // JSON is listed too, so an error can still be returned as an ErrorResponse.
    @GetMapping(value = TEMPLATE_PATH, produces = {LedgerExport.XLSX_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
    ResponseEntity<byte[]> downloadTemplate();

    @Operation(
            summary = "Import services into the catalog from an Excel workbook",
            description = "Creates every service on a filled-in import template (GET /services/import/template), "
                    + "as POST /services would. The import is all or nothing: every row is checked first, and if "
                    + "any cell is missing or invalid, or a name is already in the catalog or repeated on the "
                    + "sheet, nothing is created and each cell at fault is listed under errors, by reference "
                    + "such as Services!G3. Names are compared ignoring case. The invoice schedule columns must "
                    + "match the billing frequency, as on create. Up to 500 services may be imported at once.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Every service was created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ServiceCatalogImportResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "No file was attached, or the file is not an .xlsx workbook, lacks the Services "
                            + "sheet or its headings, holds no services or too many, or has invalid cells; code "
                            + "SPREADSHEET_INVALID lists each cell at fault under errors. Nothing was created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "A service of the same name was added while the import ran; nothing was created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "The file is larger than the server accepts",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(value = IMPORT_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ServiceCatalogImportResponse> importServices(
            @Parameter(description = "The filled-in service catalog import workbook (.xlsx)", required = true)
            @RequestPart("file") MultipartFile file);
}
