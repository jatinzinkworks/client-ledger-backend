package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Corrected rates for a ledger entry. The amounts are worked out again from them, so they always
 * add up; the entry's dates and what it was billed for cannot change.
 */
@Builder
@Schema(name = "LedgerEntryUpdateRequest", description = "Corrected rates for a ledger entry")
public record LedgerEntryUpdateRequest(

        @NotNull(message = "must be provided")
        @DecimalMin(value = "0.00", message = "must not be negative")
        @Digits(integer = 10, fraction = 2, message = "must have at most 2 decimal places")
        @Schema(description = "Fee for the service, before discount and excluding GST", example = "5000.00",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        BigDecimal serviceFee,

        @NotNull(message = "must be provided")
        @DecimalMin(value = "0.00", message = "must not be negative")
        @DecimalMax(value = "100.00", message = "must not exceed 100")
        @Digits(integer = 3, fraction = 2, message = "must have at most 2 decimal places")
        @Schema(description = "Discount granted on the service, as a percentage", example = "5.00",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "100")
        BigDecimal discountPercent,

        @NotNull(message = "must be provided")
        @DecimalMin(value = "0.00", message = "must not be negative")
        @DecimalMax(value = "100.00", message = "must not exceed 100")
        @Digits(integer = 3, fraction = 2, message = "must have at most 2 decimal places")
        @Schema(description = "GST rate to charge, as a percentage", example = "18.00",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "100")
        BigDecimal gstRatePercent) {
}
