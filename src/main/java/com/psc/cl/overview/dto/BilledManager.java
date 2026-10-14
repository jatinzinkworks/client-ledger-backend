package com.psc.cl.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One manager in the ranking of managers by what was billed to their clients.
 */
@Value
@Builder
@Schema(name = "BilledManager", description = "A manager and what was billed to their clients")
public class BilledManager {

    @Schema(description = "Position in the ranking, from 1 for the manager whose clients were billed most",
            example = "1")
    int rank;

    @Schema(description = "Identifier of the manager", example = "e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47")
    UUID managerId;

    @Schema(description = "Full name of the manager", example = "Pranay Singhal")
    String managerName;

    @Schema(description = "Number of clients assigned to the manager now", example = "12")
    long clientCount;

    @Schema(description = "Sum of the service charges of the manager's clients in the period: taxable amounts "
            + "plus GST, after discount; always above zero here", example = "39235.00")
    BigDecimal totalBilled;

    @Schema(description = "Sum of the payments received from the manager's clients in the period",
            example = "18605.00")
    BigDecimal totalPaymentReceived;

    @Schema(description = "Billed less received in the period; negative when more was received",
            example = "20630.00")
    BigDecimal pending;
}
