package com.psc.cl.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The firm's position for one financial year across every client: what was billed, received and
 * discounted, what is pending, and how many clients, companies and services there are.
 */
@Value
@Builder
@Schema(name = "OverviewResponse", description = "The firm's position for one financial year across every client")
public class OverviewResponse {

    @Schema(description = "Indian financial year (1 April to 31 March) the amounts cover", example = "2026-27")
    String financialYear;

    @Schema(description = "First day of the financial year", example = "2026-04-01")
    LocalDate fromDate;

    @Schema(description = "Last day of the financial year", example = "2027-03-31")
    LocalDate toDate;

    @Schema(description = "Sum of the service charges falling due in the year across every client: taxable "
            + "amounts plus GST, after discount", example = "1845000.00")
    BigDecimal totalBilled;

    @Schema(description = "Sum of the payments received in the year across every client", example = "1571500.00")
    BigDecimal totalPaymentReceived;

    @Schema(description = "Billed less received in the year; negative when more was received. Earlier years' "
            + "balances are not brought forward", example = "273500.00")
    BigDecimal pending;

    @Schema(description = "Sum of the discounts given on the year's service charges", example = "63500.00")
    BigDecimal totalDiscount;

    @Schema(description = "The same figures for the previous financial year, up to the same point in it: the "
            + "same day a year earlier while the requested year is running, its whole year once the requested "
            + "year is over. For comparing this year to date with last year to date")
    PeriodTotals previousToDate;

    @Schema(description = "Number of clients, as of now", example = "42")
    long totalClients;

    @Schema(description = "Number of companies across every client, as of now", example = "97")
    long totalCompanies;

    @Schema(description = "Number of services assigned across every company of every client, as of now",
            example = "268")
    long totalServices;
}
