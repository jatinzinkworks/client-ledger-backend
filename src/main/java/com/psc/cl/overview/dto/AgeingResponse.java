package com.psc.cl.overview.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * How long what the firm is owed has been due.
 */
@Value
@Builder
@Schema(name = "AgeingResponse", description = "How long what the firm is owed has been due, across every client")
public class AgeingResponse {

    @Schema(description = "The day the ageing is worked out on: today", example = "2026-10-14")
    LocalDate asOf;

    @Schema(description = "Days past due after which an unpaid charge is overdue, from the payment terms",
            example = "60")
    int overdueAfterDays;

    @ArraySchema(arraySchema = @Schema(description = "The ranges of days past due, newest first: 0-30, 31-60, "
            + "61-90 and 90+. Every range is listed, with zero when nothing is owed in it"),
            schema = @Schema(implementation = AgeingBucket.class))
    List<AgeingBucket> buckets;

    @Schema(description = "Everything owed: the sum of the ranges", example = "273500.00")
    BigDecimal total;

    @Schema(description = "What has been due for more than overdueAfterDays", example = "48000.00")
    BigDecimal overdue;

    @Schema(description = "How many companies owe anything", example = "31")
    long companyCount;
}
