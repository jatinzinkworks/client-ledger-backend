package com.psc.cl.overview.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * A financial year's billing split across the practice areas.
 */
@Value
@Builder
@Schema(name = "BilledByCategoryResponse", description = "A financial year's billing split across the practice areas")
public class BilledByCategoryResponse {

    @Schema(description = "The Indian financial year", example = "2026-27")
    String financialYear;

    @ArraySchema(arraySchema = @Schema(description = "Every practice area, billed most first; those billed the "
            + "same in the catalog's order. Areas with nothing billed are listed with zero"),
            schema = @Schema(implementation = CategoryBilling.class))
    List<CategoryBilling> categories;
}
