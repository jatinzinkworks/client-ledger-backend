package com.psc.cl.overview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The companies owing most, in one financial year or across every year.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "TopPendingCompaniesResponse", description = "The companies owing most")
public class TopPendingCompaniesResponse {

    @Schema(description = "Financial year the amounts cover. Absent when they cover every year to date",
            example = "2026-27")
    String financialYear;

    @Schema(description = "The most companies asked for", example = "10")
    int limit;

    @ArraySchema(arraySchema = @Schema(description = "The companies still owing, most first, at most limit of "
            + "them; those settled or in credit are left out"),
            schema = @Schema(implementation = PendingCompany.class))
    List<PendingCompany> companies;
}
