package com.psc.cl.client.dto;

import com.psc.cl.client.model.ServiceAssignment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for assigning a catalog service to a company of a client.
 *
 * <p>The service and the delivery date are mandatory. A service without a discount of its own
 * takes the company's, and a blank comment is stored as no comment.
 */
@Builder
@Schema(name = "ServiceAssignmentRequest", description = "Catalog service to deliver to the company")
public record ServiceAssignmentRequest(

        @NotNull(message = "must be provided")
        @Schema(description = "Identifier of the service in the Service Catalog",
                example = "3b8e5f21-9c4d-4a17-8e62-1f0d7a9c5b34",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID serviceId,

        @NotNull(message = "must be provided")
        @Schema(description = "Date the service is, or was, delivered", example = "2026-10-15",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate deliveryDate,

        @DecimalMin(value = "0.00", message = "must not be negative")
        @DecimalMax(value = "100.00", message = "must not exceed 100")
        @Digits(integer = 3, fraction = 2, message = "must have at most 2 decimal places")
        @Schema(description = "Discount granted on this service, as a percentage. When omitted, "
                + "the service takes the company's discount", example = "5.00",
                minimum = "0", maximum = "100")
        BigDecimal discountPercent,

        @Size(max = ServiceAssignment.MAX_COMMENT_LENGTH, message = "must not exceed 1000 characters")
        @Schema(description = "Free-text note about this delivery",
                example = "Filing for September 2026", maxLength = ServiceAssignment.MAX_COMMENT_LENGTH)
        String comment) {
}
