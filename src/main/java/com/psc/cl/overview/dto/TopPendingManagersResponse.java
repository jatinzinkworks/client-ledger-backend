package com.psc.cl.overview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The managers whose clients owe most, in one financial year or across every year.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "TopPendingManagersResponse", description = "The managers whose clients owe most")
public class TopPendingManagersResponse {

    @Schema(description = "Financial year the amounts cover. Absent when they cover every year to date",
            example = "2026-27")
    String financialYear;

    @Schema(description = "The most managers asked for", example = "10")
    int limit;

    @ArraySchema(arraySchema = @Schema(description = "The managers whose clients still owe, most first, at most "
            + "limit of them; those whose clients are settled or in credit overall are left out"),
            schema = @Schema(implementation = PendingManager.class))
    List<PendingManager> managers;
}
