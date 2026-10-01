package com.psc.cl.servicecatalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.model.QuarterInvoiceMonth;
import com.psc.cl.servicecatalog.model.ServiceCategory;
import com.psc.cl.servicecatalog.model.InvoiceMonth;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Payload for creating a service in the catalog.
 *
 * <p>The invoice schedule must agree with the billing frequency. The assertions at the foot of
 * this record spell out each rule, and the same relationship is held by a database constraint.
 *
 * <p>{@code usedByCompanies} is deliberately absent: it is derived from how many companies
 * subscribe to the service, and is returned on responses only.
 */
@Builder
@Schema(name = "CatalogServiceRequest", description = "Service to add to the catalog")
public record CatalogServiceRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = CatalogService.MAX_SERVICE_NAME_LENGTH, message = "must not exceed 150 characters")
        @Schema(description = "Name of the service, unique across the catalog",
                example = "GST Monthly Return Filing", requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = CatalogService.MAX_SERVICE_NAME_LENGTH)
        String serviceName,

        @Size(max = CatalogService.MAX_DESCRIPTION_LENGTH, message = "must not exceed 1000 characters")
        @Schema(description = "What the service covers",
                example = "Preparation and filing of GSTR-1 and GSTR-3B each month",
                maxLength = CatalogService.MAX_DESCRIPTION_LENGTH)
        String description,

        @NotNull(message = "must be provided")
        @Schema(description = "Practice area the service belongs to", example = "COMPLIANCE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ServiceCategory category,

        @NotNull(message = "must be provided")
        @Schema(description = "How often the service is billed", example = "MONTHLY",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BillingFrequency billingFrequency,

        @NotNull(message = "must be provided")
        @DecimalMin(value = "0.00", message = "must not be negative")
        @Digits(integer = 10, fraction = 2, message = "must have at most 2 decimal places")
        @Schema(description = "Standard fee charged for the service, excluding GST",
                example = "5000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal standardFee,

        @DecimalMin(value = "0.00", message = "must not be negative")
        @DecimalMax(value = "100.00", message = "must not exceed 100")
        @Digits(integer = 3, fraction = 2, message = "must have at most 2 decimal places")
        @Schema(description = "GST rate applied to the fee, as a percentage", example = "18.00",
                defaultValue = "18.00", minimum = "0", maximum = "100")
        BigDecimal gstRatePercent,

        @Valid
        @Schema(description = "When invoices are raised. Omit entirely for ONE_OFF billing")
        InvoiceSchedule invoiceSchedule) {

    /** @return the supplied GST rate, or the 18% default when omitted */
    @JsonIgnore
    @Schema(hidden = true)
    public BigDecimal effectiveGstRatePercent() {
        return Objects.requireNonNullElse(gstRatePercent, CatalogService.DEFAULT_GST_RATE_PERCENT);
    }

    /** @return the scheduled day of month, or null when there is no schedule */
    @JsonIgnore
    @Schema(hidden = true)
    public Integer scheduledDayOfMonth() {
        return invoiceSchedule == null ? null : invoiceSchedule.dayOfMonth();
    }

    /** @return the scheduled month of quarter, or null when there is no schedule */
    @JsonIgnore
    @Schema(hidden = true)
    public QuarterInvoiceMonth scheduledMonthOfQuarter() {
        return invoiceSchedule == null ? null : invoiceSchedule.monthOfQuarter();
    }

    /** @return the scheduled month, or null when there is no schedule */
    @JsonIgnore
    @Schema(hidden = true)
    public InvoiceMonth scheduledMonth() {
        return invoiceSchedule == null ? null : invoiceSchedule.month();
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "invoiceSchedule must be omitted when billingFrequency is ONE_OFF")
    public boolean isScheduleAbsentForOneOff() {
        if (billingFrequency == null || billingFrequency.requiresInvoiceSchedule()) {
            return true;
        }
        return invoiceSchedule == null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "invoiceSchedule.dayOfMonth is required for MONTHLY, QUARTERLY and "
            + "ANNUAL billing")
    public boolean isDayOfMonthPresentWhenRecurring() {
        if (billingFrequency == null || !billingFrequency.requiresInvoiceSchedule()) {
            return true;
        }
        return scheduledDayOfMonth() != null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "invoiceSchedule.monthOfQuarter is required for QUARTERLY billing and "
            + "must be omitted otherwise")
    public boolean isMonthOfQuarterMatchingBilling() {
        if (billingFrequency == null) {
            return true;
        }
        boolean required = billingFrequency == BillingFrequency.QUARTERLY;
        return required == (scheduledMonthOfQuarter() != null);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "invoiceSchedule.month is required for ANNUAL billing and must be "
            + "omitted otherwise")
    public boolean isMonthMatchingBilling() {
        if (billingFrequency == null) {
            return true;
        }
        boolean required = billingFrequency == BillingFrequency.ANNUAL;
        return required == (scheduledMonth() != null);
    }
}
