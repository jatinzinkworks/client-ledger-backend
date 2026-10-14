package com.psc.cl.overview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * What is owed that has been due for a range of days.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AgeingBucket", description = "What is owed that has been due for a range of days")
public class AgeingBucket {

    @Schema(description = "The range of days, as shown on the chart", example = "0-30")
    String label;

    @Schema(description = "Fewest days past due in the range", example = "0")
    int fromDays;

    @Schema(description = "Most days past due in the range. Absent for the last, open-ended range", example = "30")
    Integer toDays;

    @Schema(description = "What remains unpaid of the charges due for this many days", example = "125000.00")
    BigDecimal amount;

    @Schema(description = "How many companies owe something in the range", example = "12")
    long companyCount;
}
