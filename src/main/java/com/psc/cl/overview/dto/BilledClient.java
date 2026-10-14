package com.psc.cl.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One client in the ranking of clients by what they were billed.
 */
@Value
@Builder
@Schema(name = "BilledClient", description = "A client and what they were billed")
public class BilledClient {

    @Schema(description = "Position in the ranking, from 1 for the client billed most", example = "1")
    int rank;

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Full name of the client", example = "Rohan Mehta")
    String clientName;

    @Schema(description = "Identifier of the manager who looks after the client",
            example = "e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47")
    UUID managerId;

    @Schema(description = "Full name of the manager who looks after the client", example = "Pranay Singhal")
    String managerName;

    @Schema(description = "Sum of the client's service charges in the period: taxable amounts plus GST, after "
            + "discount; always above zero here", example = "39235.00")
    BigDecimal totalBilled;

    @Schema(description = "Sum of the client's payments received in the period", example = "18605.00")
    BigDecimal totalPaymentReceived;

    @Schema(description = "Billed less received in the period; negative when more was received",
            example = "20630.00")
    BigDecimal pending;
}
