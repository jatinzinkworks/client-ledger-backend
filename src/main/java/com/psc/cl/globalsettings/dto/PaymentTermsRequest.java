package com.psc.cl.globalsettings.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.psc.cl.globalsettings.model.PaymentTerms;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;

import java.util.Objects;

/**
 * Payload for creating or updating the tenant-wide payment terms.
 *
 * <p>Every field is optional on the wire: omitted day counts fall back to the documented
 * defaults and reminders default to disabled.
 *
 * <p>A record rather than a Lombok {@code @Value} class because Jackson 3 builds immutable
 * types from their canonical constructor. Lombok's {@code @Jacksonized} emits Jackson 2
 * databind annotations, which moved to {@code tools.jackson.databind} in Jackson 3 and are
 * therefore invisible to it.
 */
@Builder
@Schema(name = "PaymentTermsRequest", description = "Payment terms to store under Global Settings")
public record PaymentTermsRequest(

        @Min(value = 1, message = "must be at least 1 day")
        @Max(value = 365, message = "must not exceed 365 days")
        @Schema(description = "Days after work completion before payment becomes due",
                example = "15", defaultValue = "15", minimum = "1", maximum = "365")
        Integer paymentDueAfterDays,

        @Min(value = 1, message = "must be at least 1 day")
        @Max(value = 365, message = "must not exceed 365 days")
        @Schema(description = "Days after the payment due date before an unpaid invoice is marked "
                + "overdue", example = "60", defaultValue = "60", minimum = "1", maximum = "365")
        Integer markOverdueAfterDays,

        @Schema(description = "Whether payment reminders are sent for outstanding invoices",
                example = "true", defaultValue = "false")
        Boolean paymentReminderEnabled,

        @Min(value = 1, message = "must be at least 1 day")
        @Max(value = 365, message = "must not exceed 365 days")
        @Schema(description = "Days before the due date on which a reminder is sent. "
                + "Required when paymentReminderEnabled is true, ignored otherwise",
                example = "7", minimum = "1", maximum = "365")
        Integer paymentReminderDays) {

    /** @return the supplied payment-due days, or the default when omitted */
    @JsonIgnore
    @Schema(hidden = true)
    public int effectivePaymentDueAfterDays() {
        return Objects.requireNonNullElse(paymentDueAfterDays,
                PaymentTerms.DEFAULT_PAYMENT_DUE_AFTER_DAYS);
    }

    /** @return the supplied overdue-after days, or the default when omitted */
    @JsonIgnore
    @Schema(hidden = true)
    public int effectiveMarkOverdueAfterDays() {
        return Objects.requireNonNullElse(markOverdueAfterDays,
                PaymentTerms.DEFAULT_MARK_OVERDUE_AFTER_DAYS);
    }

    /** @return whether reminders are enabled, defaulting to disabled when omitted */
    @JsonIgnore
    @Schema(hidden = true)
    public boolean effectivePaymentReminderEnabled() {
        return Boolean.TRUE.equals(paymentReminderEnabled);
    }

    /** @return the reminder lead time, or null when reminders are disabled */
    @JsonIgnore
    @Schema(hidden = true)
    public Integer effectivePaymentReminderDays() {
        return effectivePaymentReminderEnabled() ? paymentReminderDays : null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "paymentReminderDays is required when paymentReminderEnabled is true")
    public boolean isReminderDaysPresentWhenEnabled() {
        return !effectivePaymentReminderEnabled() || paymentReminderDays != null;
    }
}
