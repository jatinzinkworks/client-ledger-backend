package com.psc.cl.ledger.dto;

import com.psc.cl.ledger.model.LedgerEntry;
import com.psc.cl.ledger.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A payment received from a company, to record in its ledger.
 */
@Builder
@Schema(name = "PaymentRequest", description = "A payment received from a company")
public record PaymentRequest(

        @NotNull(message = "must be provided")
        @DecimalMin(value = "0.01", message = "must be greater than zero")
        @Digits(integer = 12, fraction = 2, message = "must have at most 2 decimal places")
        @Schema(description = "Amount received, as a positive number. The ledger stores it as negative, "
                + "against the charges it settles", example = "28025.00",
                requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0.01")
        BigDecimal amountReceived,

        @NotNull(message = "must be provided")
        @Schema(description = "Date the payment was received; not after today", example = "2026-10-12",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate dateReceived,

        @NotNull(message = "must be provided")
        @Schema(description = "How the payment was received", example = "BANK_TRANSFER",
                requiredMode = Schema.RequiredMode.REQUIRED)
        PaymentMethod method,

        @Size(max = LedgerEntry.MAX_REFERENCE_LENGTH,
                message = "must be at most " + LedgerEntry.MAX_REFERENCE_LENGTH + " characters")
        @Schema(description = "The payer's reference, such as a UTR, UPI transaction id or cheque number",
                example = "HDFC0001234567890")
        String reference,

        @Size(max = LedgerEntry.MAX_COMMENT_LENGTH,
                message = "must be at most " + LedgerEntry.MAX_COMMENT_LENGTH + " characters")
        @Schema(description = "Free-text note about the payment", example = "Settles the incorporation invoice")
        String comment) {
}
