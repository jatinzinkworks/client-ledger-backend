package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * One page of ledger entries, together with the totals of every entry matching the filters.
 */
@Value
@Builder
@Schema(name = "LedgerListResponse",
        description = "One page of ledger entries, with the totals of every entry matching the filters")
public class LedgerListResponse {

    @ArraySchema(arraySchema = @Schema(description = "The entries on this page, earliest due first; empty when "
            + "none matches or the page is past the last"))
    List<LedgerEntryResponse> entries;

    @Schema(description = "Position of this page within every entry matching the filters")
    PageMetadata page;

    @Schema(description = "Totals of every entry matching the filters, across all pages")
    LedgerSummary summary;
}
