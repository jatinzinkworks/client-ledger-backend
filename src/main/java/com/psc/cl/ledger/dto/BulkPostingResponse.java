package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

/**
 * The outcome of posting the due services of every company of every client.
 */
@Value
@Builder
@Schema(name = "BulkPostingResponse",
        description = "The outcome of posting the due services of every company of every client")
public class BulkPostingResponse {

    @Schema(description = "The day services were posted as due on: today", example = "2026-10-14")
    LocalDate asOf;

    @Schema(description = "How many companies were looked at", example = "97")
    int companyCount;

    @Schema(description = "How many ledger entries were posted across every company", example = "41")
    int entriesPosted;

    @ArraySchema(arraySchema = @Schema(description = "The companies something was posted for, in client then "
            + "company order; those with nothing due are left out"),
            schema = @Schema(implementation = CompanyPostingResult.class))
    List<CompanyPostingResult> companies;

    @ArraySchema(arraySchema = @Schema(description = "The companies posting failed for; empty when every "
            + "company succeeded. Other companies are posted regardless"),
            schema = @Schema(implementation = CompanyPostingFailure.class))
    List<CompanyPostingFailure> failures;
}
