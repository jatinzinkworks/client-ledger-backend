package com.psc.cl.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * Standard error payload returned by every endpoint in this service.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ErrorResponse", description = "Standard error payload")
public class ErrorResponse {

    @Schema(description = "Moment the error was produced", example = "2026-09-29T10:15:30Z")
    Instant timestamp;

    @Schema(description = "HTTP status code", example = "400")
    int status;

    @Schema(description = "Short, machine-friendly error code", example = "VALIDATION_FAILED")
    String code;

    @Schema(description = "Human readable summary of what went wrong",
            example = "One or more fields are invalid")
    String message;

    @Schema(description = "Request path that produced the error", example = "/psc/cl/v1/global-settings/payment-terms")
    String path;

    @Schema(description = "Field level validation failures, present only for validation errors")
    List<FieldError> errors;

    /**
     * A single field level validation failure.
     */
    @Value
    @Builder
    @Schema(name = "FieldError", description = "A single field level validation failure")
    public static class FieldError {

        @Schema(description = "Name of the offending field", example = "markOverdueAfterDays")
        String field;

        @Schema(description = "Why the value was rejected", example = "must be greater than or equal to 1")
        String message;
    }
}
