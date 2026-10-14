package com.psc.cl.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One catalog service among the most billed of its category.
 */
@Value
@Builder
@Schema(name = "CategoryService", description = "A catalog service and what it was billed")
public class CategoryService {

    @Schema(description = "Identifier of the catalog service", example = "3f6c2a1e-9b4d-4e7a-8c15-2d0b7e9f4a61")
    UUID catalogServiceId;

    @Schema(description = "Name of the catalog service", example = "GST Return Filing")
    String serviceName;

    @Schema(description = "Service charges falling due in the year, GST included, after discount",
            example = "84000.00")
    BigDecimal billed;
}
