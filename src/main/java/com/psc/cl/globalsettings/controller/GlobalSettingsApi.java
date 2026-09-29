package com.psc.cl.globalsettings.controller;

import com.psc.cl.config.ApiPaths;
import com.psc.cl.dto.ErrorResponse;
import com.psc.cl.globalsettings.dto.FirmDetailsRequest;
import com.psc.cl.globalsettings.dto.FirmDetailsResponse;
import com.psc.cl.globalsettings.dto.PaymentTermsRequest;
import com.psc.cl.globalsettings.dto.PaymentTermsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contract for every Global Settings endpoint.
 *
 * <p>All Global Settings operations live behind this single API so the module presents one
 * coherent surface in Swagger UI.
 */
@Tag(name = "Global Settings",
        description = "Tenant-wide configuration for the Client Ledger, such as payment terms")
@RequestMapping(value = GlobalSettingsApi.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public interface GlobalSettingsApi {

    /** Base path shared by every Global Settings endpoint. */
    String BASE_PATH = ApiPaths.V1 + "/global-settings";

    /** Path of the payment terms resource, relative to {@link #BASE_PATH}. */
    String PAYMENT_TERMS_PATH = "/payment-terms";

    /** Path of the firm details resource, relative to {@link #BASE_PATH}. */
    String FIRM_DETAILS_PATH = "/firm-details";

    @Operation(
            summary = "Create or update payment terms",
            description = "Stores the tenant-wide payment terms. Payment terms are a singleton "
                    + "setting, so this endpoint creates them on the first call (201) and overwrites "
                    + "them on every subsequent call (200) — there is no separate update endpoint. "
                    + "Any omitted field falls back to its default: payment due 15 days after "
                    + "completion, overdue 60 days after the due date, and reminders disabled. Note that "
                    + "an update replaces the record wholesale, so omitted fields are reset to their "
                    + "defaults rather than left untouched.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Existing payment terms updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PaymentTermsResponse.class))),
            @ApiResponse(responseCode = "201", description = "Payment terms created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PaymentTermsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A concurrent save conflicted; retry",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(value = PAYMENT_TERMS_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PaymentTermsResponse> savePaymentTerms(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Payment terms to store", required = true)
            @Valid @RequestBody PaymentTermsRequest request);

    @Operation(
            summary = "Fetch payment terms",
            description = "Returns the tenant-wide payment terms currently in force. Responds with "
                    + "404 until they have been created.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment terms found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PaymentTermsResponse.class))),
            @ApiResponse(responseCode = "404", description = "No payment terms have been configured yet",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(PAYMENT_TERMS_PATH)
    ResponseEntity<PaymentTermsResponse> getPaymentTerms();

    @Operation(
            summary = "Create or update firm details",
            description = "Stores the firm details printed on invoices and Excel exports. Firm "
                    + "details are a singleton setting, so this endpoint creates them on the first "
                    + "call (201) and overwrites them on every subsequent call (200) - there is no "
                    + "separate update endpoint. firmName, gstin and address are mandatory; a save replaces the "
                    + "record wholesale, so omitted fields are cleared rather than left untouched.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Existing firm details updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = FirmDetailsResponse.class))),
            @ApiResponse(responseCode = "201", description = "Firm details created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = FirmDetailsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A concurrent save conflicted; retry",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @PostMapping(value = FIRM_DETAILS_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FirmDetailsResponse> saveFirmDetails(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Firm details to store", required = true)
            @Valid @RequestBody FirmDetailsRequest request);

    @Operation(
            summary = "Fetch firm details",
            description = "Returns the firm details currently in force. Responds with 404 until "
                    + "they have been created.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Firm details found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = FirmDetailsResponse.class))),
            @ApiResponse(responseCode = "404", description = "No firm details have been configured yet",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping(FIRM_DETAILS_PATH)
    ResponseEntity<FirmDetailsResponse> getFirmDetails();
}
