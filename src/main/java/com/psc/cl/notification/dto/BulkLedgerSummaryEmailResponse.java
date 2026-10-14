package com.psc.cl.notification.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The outcome of emailing every client their ledger summary across all of their companies.
 */
@Value
@Builder
@Schema(name = "BulkLedgerSummaryEmailResponse",
        description = "The outcome of emailing every client their ledger summary")
public class BulkLedgerSummaryEmailResponse {

    @Schema(description = "Indian financial year summarised: the current one", example = "2026-27")
    String financialYear;

    @Schema(description = "How many clients there are", example = "42")
    int clientCount;

    @Schema(description = "How many clients were emailed", example = "41")
    int sentCount;

    @ArraySchema(arraySchema = @Schema(description = "The clients emailed, by name"),
            schema = @Schema(implementation = ClientEmailResult.class))
    List<ClientEmailResult> sent;

    @ArraySchema(arraySchema = @Schema(description = "The clients that could not be emailed; empty when every "
            + "email was sent. The other clients are emailed regardless"),
            schema = @Schema(implementation = ClientEmailFailure.class))
    List<ClientEmailFailure> failures;
}
