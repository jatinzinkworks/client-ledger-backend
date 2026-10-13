package com.psc.cl.ledger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Where a company's account stands for one financial year: what it was billed, what it paid, and
 * what remains.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "CompanyLedgerSummaryResponse",
        description = "A company's billing and payments for one Indian financial year")
public class CompanyLedgerSummaryResponse {

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Identifier of the company", example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
    UUID companyId;

    @Schema(description = "Name of the company", example = "Mehta Textiles Pvt Ltd")
    String companyName;

    @Schema(description = "Indian financial year summarised", example = "2026-27")
    String financialYear;

    @Schema(description = "First day of the financial year, 1 April", example = "2026-04-01")
    LocalDate fromDate;

    @Schema(description = "Last day of the financial year, 31 March", example = "2027-03-31")
    LocalDate toDate;

    @Schema(description = "Sum of the service charges falling due in the year: taxable amounts plus GST",
            example = "39235.00")
    BigDecimal totalBilled;

    @Schema(description = "Sum of the payments received in the year, as a positive number", example = "15000.00")
    BigDecimal totalPaymentReceived;

    @Schema(description = "Billed less received in the year; negative when the year's payments exceed its "
            + "billing, for example when they settle an earlier year's charges", example = "24235.00")
    BigDecimal pending;

    @Schema(description = "Sum of the discounts taken off the service fees billed in the year", example = "1750.00")
    BigDecimal totalDiscount;

    @Schema(description = "The most recent payment received in the year; absent when none was received")
    LastPayment lastPayment;
}
