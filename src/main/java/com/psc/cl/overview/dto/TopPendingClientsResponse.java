package com.psc.cl.overview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The clients owing most, in one financial year or across every year.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "TopPendingClientsResponse", description = "The clients owing most")
public class TopPendingClientsResponse {

    @Schema(description = "Financial year the amounts cover. Absent when they cover every year to date",
            example = "2026-27")
    String financialYear;

    @Schema(description = "The most clients asked for", example = "10")
    int limit;

    @ArraySchema(arraySchema = @Schema(description = "The clients still owing, most first, at most limit of "
            + "them; those settled or in credit are left out"),
            schema = @Schema(implementation = PendingClient.class))
    List<PendingClient> clients;
}
