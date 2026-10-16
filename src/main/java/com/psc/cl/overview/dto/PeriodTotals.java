package com.psc.cl.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What was billed, received and discounted across every client over part of a financial year, for
 * comparing one year's figures with another's.
 */
@Value
@Builder
@Schema(name = "PeriodTotals", description = "The firm's figures over part of a financial year, for comparison")
public class PeriodTotals {

    @Schema(description = "Indian financial year the period falls in", example = "2025-26")
    String financialYear;

    @Schema(description = "First day of the period: the first day of the financial year", example = "2025-04-01")
    LocalDate fromDate;

    @Schema(description = "Last day of the period", example = "2025-10-14")
    LocalDate toDate;

    @Schema(description = "Sum of the service charges falling due in the period: taxable amounts plus GST, "
            + "after discount", example = "1647000.00")
    BigDecimal totalBilled;

    @Schema(description = "Sum of the opening balances dated in the period: positive when owed, negative when "
            + "in credit. Not part of totalBilled", example = "0.00")
    BigDecimal openingBalance;

    @Schema(description = "Sum of the payments received in the period", example = "1402000.00")
    BigDecimal totalPaymentReceived;

    @Schema(description = "Billed plus opening balance less received in the period; negative when more was "
            + "received", example = "245000.00")
    BigDecimal pending;

    @Schema(description = "Sum of the discounts given on the period's service charges", example = "58000.00")
    BigDecimal totalDiscount;
}
