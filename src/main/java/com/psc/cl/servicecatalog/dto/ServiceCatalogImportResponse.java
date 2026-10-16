package com.psc.cl.servicecatalog.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * What a service catalog import created: every service on the uploaded workbook.
 */
@Value
@Builder
@Schema(name = "ServiceCatalogImportResponse", description = "The services created by an import")
public class ServiceCatalogImportResponse {

    @Schema(description = "How many services were created", example = "2")
    int serviceCount;

    @ArraySchema(arraySchema = @Schema(description = "Each service created, in the order of the Services sheet"),
            schema = @Schema(implementation = CatalogServiceResponse.class))
    List<CatalogServiceResponse> services;
}
