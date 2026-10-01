package com.psc.cl.servicecatalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.servicecatalog.model.QuarterInvoiceMonth;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;

import java.time.Month;

/**
 * When invoices are raised for a recurring service.
 *
 * <p>Which fields apply is decided by the billing frequency of the service: {@code MONTHLY} uses
 * only {@code dayOfMonth}, {@code QUARTERLY} adds {@code monthOfQuarter}, and {@code ANNUAL} adds
 * {@code month}. A one-off service carries no schedule at all.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "InvoiceSchedule", description = "When invoices are raised for a recurring service")
public record InvoiceSchedule(

        @Min(value = 1, message = "must be between 1 and 31")
        @Max(value = 31, message = "must be between 1 and 31")
        @Schema(description = "Day of the month the invoice is raised. In months shorter than this "
                + "day, the invoice falls on the last day of that month instead",
                example = "7", minimum = "1", maximum = "31")
        Integer dayOfMonth,

        @Schema(description = "Quarterly billing only: which month after the quarter closes the "
                + "invoice is raised in", example = "FIRST_MONTH")
        QuarterInvoiceMonth monthOfQuarter,

        @Schema(description = "Annual billing only: the month the invoice is raised in, by name",
                example = "APRIL")
        Month month) {
}
