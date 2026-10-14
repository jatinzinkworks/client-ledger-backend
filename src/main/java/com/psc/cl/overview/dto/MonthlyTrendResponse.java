package com.psc.cl.overview.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * What was billed and received across every client in each month of a financial year.
 */
@Value
@Builder
@Schema(name = "MonthlyTrendResponse",
        description = "What was billed and received across every client in each month of a financial year")
public class MonthlyTrendResponse {

    @Schema(description = "The Indian financial year", example = "2026-27")
    String financialYear;

    @ArraySchema(arraySchema = @Schema(description = "All twelve months of the year, April first; months "
            + "without entries have every amount zero"),
            schema = @Schema(implementation = MonthlyTrend.class))
    List<MonthlyTrend> months;
}
