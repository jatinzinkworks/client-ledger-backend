package com.psc.cl.overview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.ledger.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One payment in the list of the latest payments received across every client.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RecentPayment", description = "A payment received, with who made it")
public class RecentPayment {

    @Schema(description = "Identifier of the payment's ledger entry", example = "5e8b1d24-7a3c-4f90-b6e2-1c9d8a7f3b05")
    UUID paymentId;

    @Schema(description = "Date the payment was received", example = "2026-10-10")
    LocalDate dateReceived;

    @Schema(description = "Amount received, as a positive number", example = "10000.00")
    BigDecimal amount;

    @Schema(description = "How the payment was received", example = "UPI")
    PaymentMethod method;

    @Schema(description = "Reference of the payment, such as a UTR or cheque number. Absent when none was given",
            example = "UTR428193650127")
    String reference;

    @Schema(description = "Note about the payment. Absent when none was given", example = "Part payment for Q2")
    String comment;

    @Schema(description = "Identifier of the company that paid", example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
    UUID companyId;

    @Schema(description = "Name of the company that paid", example = "Rohan Mehta HUF")
    String companyName;

    @Schema(description = "Identifier of the client the company belongs to",
            example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Full name of the client the company belongs to", example = "Rohan Mehta")
    String clientName;

    @Schema(description = "When the payment was recorded", example = "2026-10-10T09:30:00Z")
    Instant recordedAt;
}
