package com.psc.cl.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One company in the ranking of companies by what they were billed.
 */
@Value
@Builder
@Schema(name = "BilledCompany", description = "A company and what it was billed")
public class BilledCompany {

    @Schema(description = "Position in the ranking, from 1 for the company billed most", example = "1")
    int rank;

    @Schema(description = "Identifier of the company", example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
    UUID companyId;

    @Schema(description = "Name of the company", example = "Rohan Mehta HUF")
    String companyName;

    @Schema(description = "Identifier of the client the company belongs to",
            example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Full name of the client the company belongs to", example = "Rohan Mehta")
    String clientName;

    @Schema(description = "Sum of the company's service charges in the period: taxable amounts plus GST, after "
            + "discount; always above zero here", example = "28025.00")
    BigDecimal totalBilled;

    @Schema(description = "Sum of the company's payments received in the period", example = "10000.00")
    BigDecimal totalPaymentReceived;

    @Schema(description = "Billed less received in the period; negative when more was received",
            example = "18025.00")
    BigDecimal pending;
}
