package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Totals of every payment matching a payment list's filters, across all of its pages.
 */
@Value
@Builder
@Schema(name = "PaymentSummary",
        description = "Totals of every payment matching the filters, across all pages, not just the page returned")
public class PaymentSummary {

    @Schema(description = "Number of payments matching the filters", example = "4")
    long paymentCount;

    @Schema(description = "Sum of the payments received, as a positive number", example = "18605.00")
    BigDecimal totalReceived;
}
