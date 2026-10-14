package com.psc.cl.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * What was billed and received across every client in one month.
 */
@Value
@Builder
@Schema(name = "MonthlyTrend", description = "What was billed and received across every client in one month")
public class MonthlyTrend {

    @Schema(description = "The calendar month, written as yyyy-MM", example = "2026-04")
    String month;

    @Schema(description = "Service charges falling due in the month, GST included, after discount",
            example = "152000.00")
    BigDecimal billed;

    @Schema(description = "Payments received in the month", example = "131500.00")
    BigDecimal received;

    @Schema(description = "Discount given on the service charges falling due in the month", example = "5200.00")
    BigDecimal discount;
}
