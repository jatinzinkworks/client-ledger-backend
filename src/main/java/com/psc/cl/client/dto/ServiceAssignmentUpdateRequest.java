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

/**
 * Payload for replacing the details of a service assigned to a company.
 *
 * <p>The catalog service itself cannot change: to deliver a different service, remove this one and
 * assign the other. Nothing is left behind from the previous version, so an omitted discount
 * resets to the company's and an omitted comment is cleared.
 */
@Builder
@Schema(name = "ServiceAssignmentUpdateRequest", description = "New details for a service assigned to a company")
public record ServiceAssignmentUpdateRequest(

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
        @Schema(description = "Free-text note about this delivery. When omitted, any existing comment "
                + "is cleared", example = "Filing for September 2026",
                maxLength = ServiceAssignment.MAX_COMMENT_LENGTH)
        String comment) {
}
