package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Totals of the figures of every ledger entry matching a list's filters, across all of its pages.
 */
@Value
@Builder
@Schema(name = "LedgerSummary",
        description = "Totals of every ledger entry matching the filters, across all pages, not just the page returned")
public class LedgerSummary {

    @Schema(description = "Number of entries matching the filters, service charges, payments and opening "
            + "balances alike", example = "12")
    long entryCount;

    @Schema(description = "Sum of the service fees, before discount and excluding GST; service charges only",
            example = "60000.00")
    BigDecimal totalServiceFee;

    @Schema(description = "Sum of the discounts taken off the service fees", example = "3000.00")
    BigDecimal totalDiscountAmount;

    @Schema(description = "Sum of the taxable amounts, on which GST is charged", example = "57000.00")
    BigDecimal totalTaxableAmount;

    @Schema(description = "Sum of the GST charged", example = "10260.00")
    BigDecimal totalGstAmount;

    @Schema(description = "Sum of the amounts charged for services: taxable amounts plus GST. Payments and "
            + "opening balances are not included; see totalPaid and openingBalance", example = "67260.00")
    BigDecimal totalAmount;

    @Schema(description = "Sum of the opening balances companies were taken on with: positive when owed, "
            + "negative when in credit; zero when there are none", example = "0.00")
    BigDecimal openingBalance;

    @Schema(description = "Sum of the payments received, as a positive number", example = "28025.00")
    BigDecimal totalPaid;

    @Schema(description = "Amount still owed: totalAmount plus openingBalance less totalPaid; negative when "
            + "in credit", example = "39235.00")
    BigDecimal balance;

    @ArraySchema(arraySchema = @Schema(description = "The same totals for each financial year the entries "
            + "fall in, earliest year first; empty when there are no entries"))
    List<FinancialYearTotals> byFinancialYear;

    @ArraySchema(arraySchema = @Schema(description = "The same totals for each company the entries were "
            + "billed to, ordered by company name. Returned on the client's ledger only; empty when there "
            + "are no entries"))
    List<CompanyTotals> byCompany;
}
