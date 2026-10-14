package com.psc.cl.ledger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A client's balance in one financial year, across all of their companies, with the same figures
 * for each company.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "FinancialYearStatus", description = "A client's balance in one financial year")
public class FinancialYearStatus {

    @Schema(description = "Indian financial year (1 April to 31 March)", example = "2025-26")
    String financialYear;

    @Schema(description = "First day of the financial year", example = "2025-04-01")
    LocalDate fromDate;

    @Schema(description = "Last day of the financial year", example = "2026-03-31")
    LocalDate toDate;

    @Schema(description = "Whether this is the current financial year, whose figures run to date", example = "false")
    boolean current;

    @Schema(description = "Day the year closed: its last day. Absent for the current year", example = "2026-03-31")
    LocalDate closedOn;

    @Schema(description = "Number of the client's companies with ledger entries in the year", example = "3")
    int companyCount;

    @Schema(description = "Service charges falling due in the year before their discount: what was charged "
            + "plus the discount given, GST included", example = "184500.00")
    BigDecimal billed;

    @Schema(description = "Discount given on those charges", example = "6350.00")
    BigDecimal discount;

    @Schema(description = "Payments received in the year", example = "157150.00")
    BigDecimal received;

    @Schema(description = "Billed less discount less received: what the year's activity left owing; negative "
            + "when more was received. Each year stands alone; an earlier year's balance is not brought "
            + "forward into it", example = "21000.00")
    BigDecimal closingBalance;

    @Schema(description = "Where the year stands", example = "CARRIED")
    LedgerYearStatus status;

    @ArraySchema(arraySchema = @Schema(description = "The same figures for each company with ledger entries in "
            + "the year, ordered by company name; they add up to the year's. With pendingOnly, only the "
            + "companies still owing for the year"),
            schema = @Schema(implementation = CompanyYearStatus.class))
    List<CompanyYearStatus> companies;
}
