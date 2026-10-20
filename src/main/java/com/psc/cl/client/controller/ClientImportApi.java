package com.psc.cl.client.controller;

import com.psc.cl.client.dto.ClientImportResponse;
import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.ledger.dto.LedgerExport;
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
 * Contract for importing clients and their companies in bulk from an Excel workbook.
 */
@Tag(name = "Clients", description = "People the firm works for and the manager who looks after them")
@RequestMapping(value = ClientApi.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public interface ClientImportApi {

    /** Path a workbook is imported through, relative to {@link ClientApi#BASE_PATH}. */
    String IMPORT_PATH = "/import";

    /** Path the workbook to fill in is downloaded from, relative to {@link ClientApi#BASE_PATH}. */
    String TEMPLATE_PATH = IMPORT_PATH + "/template";

    /** Name the template is downloaded under. */
    String TEMPLATE_FILE_NAME = "Client_Import_Template.xlsx";

    @Operation(
            summary = "Download the client import template",
            description = "Returns the Excel workbook to fill in for POST /clients/import. Its Clients sheet takes "
                    + "one row per client: Client Ref, First Name, Last Name, Phone, Email, Manager Email, "
                    + "and Discount %. Its Companies sheet takes one row per company: Client Ref, "
                    + "Company Name, Company Type, Address and Discount %, linked to its client by the Client Ref. "
                    + "An Instructions sheet describes every column with an example. Only clients and their "
                    + "companies are imported; services are assigned afterwards as usual.")
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
            summary = "Import clients and their companies from an Excel workbook",
            description = "Creates every client on a filled-in import template (GET /clients/import/template), "
                    + "each with the companies the Companies sheet links to them, as POST /clients would. The "
                    + "import is all or nothing: every row is checked first, and if any cell is missing or "
                    + "invalid, nothing is created and each cell at fault is listed under errors, by reference "
                    + "such as Clients!F3. Managers are named by their email in the Manager Catalog. Up to 500 "
                    + "clients may be imported at once, each with 1 to 50 companies. Clients already on file "
                    + "are not looked for, so importing the same workbook twice creates its clients twice. "
                    + "The response lists each company created with the opening balance recorded for it, and "
                    + "counts and adds those balances up per client and overall, so they can be checked "
                    + "against the workbook.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Every client and company was created, with the "
                    + "opening balances recorded",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ClientImportResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "No file was attached, or the file is not an .xlsx workbook, lacks the Clients "
                            + "or Companies sheet or its headings, holds no clients or too many, or has invalid "
                            + "cells; code SPREADSHEET_INVALID lists each cell at fault under errors. Nothing "
                            + "was created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "A manager was deleted while the import ran; nothing was created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "The file is larger than the server accepts",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(value = IMPORT_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ClientImportResponse> importClients(
            @Parameter(description = "The filled-in client import workbook (.xlsx)", required = true)
            @RequestPart("file") MultipartFile file);
}
