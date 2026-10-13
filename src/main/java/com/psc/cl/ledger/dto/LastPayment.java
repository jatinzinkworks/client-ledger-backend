package com.psc.cl.ledger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The most recent payment received in a financial year, from one company or across a client's.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "LastPayment", description = "The most recent payment received in the financial year")
public class LastPayment {

    @Schema(description = "Date the payment was received", example = "2026-10-12")
    LocalDate date;

    @Schema(description = "Amount received, as a positive number", example = "15000.00")
    BigDecimal amount;

    @Schema(description = "Company that made the payment; given only in a client's summary, which spans "
            + "several companies", example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
    UUID companyId;

    @Schema(description = "Name of the company that made the payment; given only in a client's summary",
            example = "Mehta Textiles Pvt Ltd")
    String companyName;
}
