package com.psc.cl.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One company's balance in one financial year, for the By Company view of a client's years.
 */
@Value
@Builder
@Schema(name = "CompanyYearStatus", description = "One company's balance in one financial year")
public class CompanyYearStatus {

    @Schema(description = "Identifier of the company", example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
    UUID companyId;

    @Schema(description = "Name of the company", example = "Mehta Textiles Pvt Ltd")
    String companyName;

    @Schema(description = "Service charges falling due in the year before their discount: what was charged "
            + "plus the discount given, GST included", example = "61500.00")
    BigDecimal billed;

    @Schema(description = "Discount given on those charges", example = "2116.67")
    BigDecimal discount;

    @Schema(description = "Payments received in the year", example = "52383.33")
    BigDecimal received;

    @Schema(description = "Billed less discount less received: what the year's activity left owing; negative "
            + "when more was received", example = "7000.00")
    BigDecimal closingBalance;

    @Schema(description = "Where the company's year stands", example = "CARRIED")
    LedgerYearStatus status;
}
