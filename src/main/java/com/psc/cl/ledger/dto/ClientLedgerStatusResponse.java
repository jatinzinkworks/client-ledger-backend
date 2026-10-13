package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A client's ledger balance for every financial year since they became a client, latest first.
 */
@Value
@Builder
@Schema(name = "ClientLedgerStatusResponse",
        description = "A client's ledger balance for every financial year since they became a client")
public class ClientLedgerStatusResponse {

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Full name of the client", example = "Rohan Mehta")
    String clientName;

    @Schema(description = "First day of the first financial year listed: that of the client's earliest ledger "
            + "entry, or of the day they were added if earlier", example = "2019-04-01")
    LocalDate clientSince;

    @Schema(description = "Number of financial years listed, from clientSince to the current year", example = "8")
    int yearCount;

    @ArraySchema(arraySchema = @Schema(description = "Every financial year from clientSince to the current one, "
            + "latest first, including years with no entries"),
            schema = @Schema(implementation = FinancialYearStatus.class))
    List<FinancialYearStatus> years;
}
