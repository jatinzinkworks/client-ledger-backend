package com.psc.cl.overview.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The latest payments received across every client and company.
 */
@Value
@Builder
@Schema(name = "RecentPaymentsResponse", description = "The latest payments received across every client")
public class RecentPaymentsResponse {

    @Schema(description = "The most payments asked for", example = "20")
    int limit;

    @ArraySchema(arraySchema = @Schema(description = "The latest payments, most recently received first; "
            + "payments received the same day come latest recorded first. At most limit of them"),
            schema = @Schema(implementation = RecentPayment.class))
    List<RecentPayment> payments;
}
