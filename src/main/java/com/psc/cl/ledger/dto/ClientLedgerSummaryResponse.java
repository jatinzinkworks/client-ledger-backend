package com.psc.cl.ledger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Where a client's account stands for one financial year, across all of their companies together.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ClientLedgerSummaryResponse",
        description = "A client's billing and payments for one Indian financial year, across all of their companies")
public class ClientLedgerSummaryResponse {

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Full name of the client", example = "Rohan Mehta")
    String clientName;

    @Schema(description = "Indian financial year summarised", example = "2026-27")
    String financialYear;

    @Schema(description = "First day of the financial year, 1 April", example = "2026-04-01")
    LocalDate fromDate;

    @Schema(description = "Last day of the financial year, 31 March", example = "2027-03-31")
    LocalDate toDate;

    @Schema(description = "Sum of the service charges falling due in the year, across every company",
            example = "67260.00")
    BigDecimal totalBilled;

    @Schema(description = "Sum of the opening balances dated in the year, which companies were taken on with: "
            + "positive when owed, negative when in credit; zero when none was. Not part of totalBilled",
            example = "0.00")
    BigDecimal openingBalance;

    @Schema(description = "Sum of the payments received in the year from every company, as a positive number",
            example = "43025.00")
    BigDecimal totalPaymentReceived;

    @Schema(description = "Billed plus opening balance less received in the year, across every company; "
            + "negative when the year's payments exceed them", example = "24235.00")
    BigDecimal pending;

    @Schema(description = "Sum of the discounts taken off the service fees billed in the year, across every "
            + "company", example = "3000.00")
    BigDecimal totalDiscount;

    @Schema(description = "The most recent payment received in the year from any company, naming the company; "
            + "absent when none was received")
    LastPayment lastPayment;
}
