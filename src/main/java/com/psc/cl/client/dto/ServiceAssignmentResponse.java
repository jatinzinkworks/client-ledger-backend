package com.psc.cl.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.client.model.ServiceAssignment;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import com.psc.cl.servicecatalog.model.CatalogService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A catalog service as assigned to a company of a client.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ServiceAssignmentResponse", description = "A catalog service assigned to a company")
public class ServiceAssignmentResponse {

    @Schema(description = "Identifier of this assignment", example = "9d2a7c40-6e1b-4f53-b8a9-0c3e5d7f1a62")
    UUID id;

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Identifier of the company the service is delivered to",
            example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
    UUID companyId;

    @Schema(description = "Identifier of the service in the Service Catalog",
            example = "3b8e5f21-9c4d-4a17-8e62-1f0d7a9c5b34")
    UUID serviceId;

    @Schema(description = "Name of the service in the Service Catalog", example = "GST Monthly Return Filing")
    String serviceName;

    @Schema(description = "How the service is billed, taken from the Service Catalog", example = "MONTHLY")
    BillingFrequency serviceType;

    @Schema(description = "Standard fee of the service, excluding GST and before this assignment's "
            + "discount, taken from the Service Catalog", example = "5000.00")
    BigDecimal serviceFee;

    @Schema(description = "Date the service is, or was, delivered", example = "2026-10-15")
    LocalDate deliveryDate;

    @Schema(description = "First date the service's invoice schedule falls on, on or after the "
            + "delivery date. A ONE_OFF service is due on its delivery date", example = "2026-11-07")
    LocalDate nextDueDate;

    @Schema(description = "Discount granted on this service, as a percentage", example = "5.00")
    BigDecimal discountPercent;

    @Schema(description = "Free-text note about this delivery, absent when none was given",
            example = "Filing for September 2026")
    String comment;

    @Schema(description = "When the service was assigned", example = "2026-10-04T10:15:30Z")
    Instant createdAt;

    @Schema(description = "When the assignment was last updated", example = "2026-10-04T10:15:30Z")
    Instant updatedAt;

    /**
     * Maps a persisted assignment onto its API representation.
     *
     * @param entity   the stored assignment, with its company loaded
     * @param clientId the client the company belongs to
     * @param service  the catalog service assigned
     * @return the response payload
     */
    public static ServiceAssignmentResponse from(ServiceAssignment entity, UUID clientId,
                                                 CatalogService service) {
        return ServiceAssignmentResponse.builder()
                .id(entity.getId())
                .clientId(clientId)
                .companyId(entity.getCompany().getId())
                .serviceId(entity.getCatalogServiceId())
                .serviceName(service.getServiceName())
                .serviceType(entity.getServiceType())
                .serviceFee(service.getStandardFee())
                .deliveryDate(entity.getDeliveryDate())
                .nextDueDate(entity.getNextDueDate())
                .discountPercent(entity.getDiscountPercent())
                .comment(entity.getComment())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
