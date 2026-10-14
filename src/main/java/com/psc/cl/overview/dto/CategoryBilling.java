package com.psc.cl.overview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.servicecatalog.model.ServiceCategory;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * What one practice area was billed in a financial year.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "CategoryBilling", description = "What one practice area was billed in a financial year")
public class CategoryBilling {

    @Schema(description = "The practice area", example = "COMPLIANCE")
    ServiceCategory category;

    @Schema(description = "Service charges of the category's services falling due in the year, GST included, "
            + "after discount", example = "612000.00")
    BigDecimal billed;

    @Schema(description = "How many of the category's catalog services were billed in the year", example = "6")
    long serviceCount;

    @ArraySchema(arraySchema = @Schema(description = "The category's services billed most, most first; those "
            + "billed the same in name order. At most topServices of them. Absent when topServices is 0"),
            schema = @Schema(implementation = CategoryService.class))
    List<CategoryService> topServices;
}
