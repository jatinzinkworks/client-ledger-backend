package com.psc.cl.notification.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Confirms a ledger summary, of one company or across all of a client's, was emailed to the client.
 */
@Value
@Builder
@Schema(name = "LedgerSummaryEmailResponse", description = "Confirms a ledger summary email was sent")
public class LedgerSummaryEmailResponse {

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Identifier of the company summarised. Absent when the summary spans all of the "
            + "client's companies", example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
    UUID companyId;

    @Schema(description = "Indian financial year summarised: the current one", example = "2026-27")
    String financialYear;

    @Schema(description = "Address the email was sent to: the client's", example = "rohan.mehta@example.com")
    String to;

    @ArraySchema(arraySchema = @Schema(description = "Addresses copied in: the client's manager"),
            schema = @Schema(example = "pranay.singhal@example.com"))
    List<String> cc;

    @Schema(description = "Subject of the email",
            example = "Your ledger summary for Mehta Textiles Pvt Ltd, FY 2026-27")
    String subject;

    @Schema(description = "Name of the PDF ledger statement attached",
            example = "Mehta_Textiles_Pvt_Ltd_Ledger_FY2026-27.pdf")
    String attachmentName;

    @Schema(description = "When the email was handed to the SMTP server", example = "2026-10-14T05:30:00Z")
    Instant sentAt;
}
