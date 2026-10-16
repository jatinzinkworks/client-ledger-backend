package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Totals of the figures of the ledger entries falling in one financial year.
 */
@Value
@Builder
@Schema(name = "FinancialYearTotals", description = "Totals of the ledger entries listed in one financial year")
public class FinancialYearTotals {

    @Schema(description = "Indian financial year (1 April to 31 March) the entries' due dates fall in",
            example = "2026-27")
    String financialYear;

    @Schema(description = "Number of entries listed in this financial year", example = "7")
    long entryCount;

    @Schema(description = "Sum of the service fees, before discount and excluding GST", example = "35000.00")
    BigDecimal totalServiceFee;

    @Schema(description = "Sum of the discounts taken off the service fees", example = "1750.00")
    BigDecimal totalDiscountAmount;

    @Schema(description = "Sum of the taxable amounts, on which GST is charged", example = "33250.00")
    BigDecimal totalTaxableAmount;

    @Schema(description = "Sum of the GST charged", example = "5985.00")
    BigDecimal totalGstAmount;

    @Schema(description = "Sum of the amounts charged for services: taxable amounts plus GST. Payments and "
            + "opening balances are not included; see totalPaid and openingBalance", example = "39235.00")
    BigDecimal totalAmount;

    @Schema(description = "Sum of the opening balances dated in this financial year: positive when owed, "
            + "negative when in credit", example = "0.00")
    BigDecimal openingBalance;

    @Schema(description = "Sum of the payments received, as a positive number", example = "28025.00")
    BigDecimal totalPaid;

    @Schema(description = "Amount still owed: totalAmount plus openingBalance less totalPaid; negative when "
            + "in credit", example = "11210.00")
    BigDecimal balance;
}
