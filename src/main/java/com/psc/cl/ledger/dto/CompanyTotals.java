package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Totals of the figures of the ledger entries billed to one company of a client.
 */
@Value
@Builder
@Schema(name = "CompanyTotals", description = "Totals of the ledger entries listed for one company of the client")
public class CompanyTotals {

    @Schema(description = "Identifier of the company", example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
    UUID companyId;

    @Schema(description = "Name of the company", example = "Mehta Textiles Pvt Ltd")
    String companyName;

    @Schema(description = "Number of entries listed for this company", example = "5")
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
            + "the opening balance are not included; see totalPaid and openingBalance", example = "39235.00")
    BigDecimal totalAmount;

    @Schema(description = "The opening balance the company was taken on with, when it is listed: positive "
            + "when owed, negative when in credit; zero otherwise", example = "0.00")
    BigDecimal openingBalance;

    @Schema(description = "Sum of the payments received, as a positive number", example = "28025.00")
    BigDecimal totalPaid;

    @Schema(description = "Amount still owed: totalAmount plus openingBalance less totalPaid; negative when "
            + "in credit", example = "11210.00")
    BigDecimal balance;
}
