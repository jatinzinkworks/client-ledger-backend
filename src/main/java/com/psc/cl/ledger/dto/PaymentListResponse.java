package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * One page of a company's payments, together with the totals of every payment matching the filters.
 */
@Value
@Builder
@Schema(name = "PaymentListResponse",
        description = "One page of a company's payments, with the totals of every payment matching the filters")
public class PaymentListResponse {

    @ArraySchema(arraySchema = @Schema(description = "The payments on this page, each the ledger entry it was "
            + "recorded as, with entryType PAYMENT and a negative totalAmount; empty when none matches or the "
            + "page is past the last"))
    List<LedgerEntryResponse> payments;

    @Schema(description = "Position of this page within every payment matching the filters")
    PageMetadata page;

    @Schema(description = "Totals of every payment matching the filters, across all pages")
    PaymentSummary summary;
}
