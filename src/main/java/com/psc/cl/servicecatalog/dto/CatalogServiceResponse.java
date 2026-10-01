package com.psc.cl.servicecatalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import com.psc.cl.servicecatalog.model.CatalogService;
import com.psc.cl.servicecatalog.model.ServiceCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A service as held in the catalog.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "CatalogServiceResponse", description = "A service held in the catalog")
public class CatalogServiceResponse {

    @Schema(description = "Identifier of the service",
            example = "b41e7a52-9c3d-4f16-8ad0-5e2b9c7f1d38")
    UUID id;

    @Schema(description = "Name of the service", example = "GST Monthly Return Filing")
    String serviceName;

    @Schema(description = "What the service covers",
            example = "Preparation and filing of GSTR-1 and GSTR-3B each month")
    String description;

    @Schema(description = "Practice area the service belongs to", example = "COMPLIANCE")
    ServiceCategory category;

    @Schema(description = "How often the service is billed", example = "MONTHLY")
    BillingFrequency billingFrequency;

    @Schema(description = "Standard fee charged for the service, excluding GST", example = "5000.00")
    BigDecimal standardFee;

    @Schema(description = "GST rate applied to the fee, as a percentage", example = "18.00")
    BigDecimal gstRatePercent;

    @Schema(description = "When invoices are raised. Absent for ONE_OFF billing")
    InvoiceSchedule invoiceSchedule;

    @Schema(description = "How many companies currently subscribe to this service. Read only",
            example = "12", accessMode = Schema.AccessMode.READ_ONLY)
    Integer usedByCompanies;

    @Schema(description = "When the service was created", example = "2026-10-01T10:15:30Z")
    Instant createdAt;

    @Schema(description = "When the service was last updated", example = "2026-10-01T10:15:30Z")
    Instant updatedAt;

    /**
     * Maps a persisted entity onto its API representation, rebuilding the nested invoice schedule
     * from the flattened columns.
     *
     * @param entity the stored service
     * @return the response payload
     */
    public static CatalogServiceResponse from(CatalogService entity) {
        return CatalogServiceResponse.builder()
                .id(entity.getId())
                .serviceName(entity.getServiceName())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .billingFrequency(entity.getBillingFrequency())
                .standardFee(entity.getStandardFee())
                .gstRatePercent(entity.getGstRatePercent())
                .invoiceSchedule(scheduleOf(entity))
                .usedByCompanies(entity.getUsedByCompanies())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static InvoiceSchedule scheduleOf(CatalogService entity) {
        if (entity.getBillingFrequency() == null || !entity.getBillingFrequency().requiresInvoiceSchedule()) {
            return null;
        }
        return InvoiceSchedule.builder()
                .dayOfMonth(entity.getInvoiceDayOfMonth())
                .monthOfQuarter(entity.getInvoiceMonthOfQuarter())
                .month(entity.getInvoiceMonth())
                .build();
    }
}
