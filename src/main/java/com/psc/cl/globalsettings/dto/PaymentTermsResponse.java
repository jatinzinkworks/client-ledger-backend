package com.psc.cl.globalsettings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.globalsettings.model.PaymentTerms;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * Payment terms as stored under Global Settings, with every default resolved.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PaymentTermsResponse", description = "Payment terms currently held under Global Settings")
public class PaymentTermsResponse {

    @Schema(description = "Identifier of the payment terms record",
            example = "3f2a6c1e-8b4d-4a91-9f0c-2d5e7a1b3c4d")
    UUID id;

    @Schema(description = "Days after work completion before payment becomes due", example = "15")
    Integer paymentDueAfterDays;

    @Schema(description = "Days after the payment due date before an unpaid invoice is marked overdue",
            example = "60")
    Integer markOverdueAfterDays;

    @Schema(description = "Whether payment reminders are sent for outstanding invoices", example = "true")
    Boolean paymentReminderEnabled;

    @Schema(description = "Days before the due date on which a reminder is sent. "
            + "Absent when reminders are disabled", example = "7")
    Integer paymentReminderDays;

    @Schema(description = "When the record was created", example = "2026-09-29T10:15:30Z")
    Instant createdAt;

    @Schema(description = "When the record was last updated", example = "2026-09-29T10:15:30Z")
    Instant updatedAt;

    /**
     * Maps a persisted entity onto its API representation.
     *
     * @param entity the stored payment terms
     * @return the response payload
     */
    public static PaymentTermsResponse from(PaymentTerms entity) {
        return PaymentTermsResponse.builder()
                .id(entity.getId())
                .paymentDueAfterDays(entity.getPaymentDueAfterDays())
                .markOverdueAfterDays(entity.getMarkOverdueAfterDays())
                .paymentReminderEnabled(entity.getPaymentReminderEnabled())
                .paymentReminderDays(entity.getPaymentReminderDays())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
