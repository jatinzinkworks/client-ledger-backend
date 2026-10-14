package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * What posting due services did for one company in a run across every company.
 */
@Value
@Builder
@Schema(name = "CompanyPostingResult", description = "What was posted to one company's ledger in a run")
public class CompanyPostingResult {

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Identifier of the company", example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
    UUID companyId;

    @Schema(description = "Name of the company", example = "Mehta Textiles Pvt Ltd")
    String companyName;

    @Schema(description = "How many ledger entries were posted for the company", example = "3")
    int entriesPosted;
}
