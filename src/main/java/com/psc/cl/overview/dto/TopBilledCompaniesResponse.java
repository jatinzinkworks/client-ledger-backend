package com.psc.cl.overview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The companies billed most, in one financial year or across every year.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "TopBilledCompaniesResponse", description = "The companies billed most")
public class TopBilledCompaniesResponse {

    @Schema(description = "Financial year the amounts cover. Absent when they cover every year to date",
            example = "2026-27")
    String financialYear;

    @Schema(description = "The most companies asked for", example = "10")
    int limit;

    @ArraySchema(arraySchema = @Schema(description = "The companies billed anything, most billed first, at most "
            + "limit of them"),
            schema = @Schema(implementation = BilledCompany.class))
    List<BilledCompany> companies;
}
