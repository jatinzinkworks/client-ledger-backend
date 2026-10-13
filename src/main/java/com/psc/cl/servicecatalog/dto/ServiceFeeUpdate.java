package com.psc.cl.servicecatalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A new standard fee for one catalog service, as part of a {@link ServiceFeeBulkUpdateRequest}.
 */
@Builder
@Schema(name = "ServiceFeeUpdate", description = "New standard fee for one service")
public record ServiceFeeUpdate(

        @NotNull(message = "must be provided")
        @Schema(description = "Identifier of the service in the catalog",
                example = "b41e7a52-9c3d-4f16-8ad0-5e2b9c7f1d38", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID serviceId,

        // Same rules as the fee on CatalogServiceRequest.
        @NotNull(message = "must be provided")
        @DecimalMin(value = "0.00", message = "must not be negative")
        @Digits(integer = 10, fraction = 2, message = "must have at most 2 decimal places")
        @Schema(description = "New standard fee charged for the service, excluding GST",
                example = "5500.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal standardFee) {
}
