package com.psc.cl.ledger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.ledger.model.LedgerEntry;
import com.psc.cl.ledger.model.LedgerEntryType;
import com.psc.cl.ledger.model.PaymentMethod;
import com.psc.cl.servicecatalog.model.BillingFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of a company's ledger: a charge for a service that fell due, or a payment received.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "LedgerEntryResponse", description = "A charge for a service that fell due, or a payment received. "
        + "entryType tells them apart; fields that do not apply to the type are left out")
public class LedgerEntryResponse {

    @Schema(description = "Identifier of the ledger entry", example = "5e7c1a93-2b4d-4f86-9a0e-8d3b6c2f1e47")
    UUID id;

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Identifier of the company billed", example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
    UUID companyId;

    @Schema(description = "SERVICE_CHARGE for an amount owed for a service, PAYMENT for an amount received",
            example = "SERVICE_CHARGE")
    LedgerEntryType entryType;

    @Schema(description = "Identifier of the service as assigned to the company",
            example = "9d2a7c40-6e1b-4f53-b8a9-0c3e5d7f1a62")
    UUID companyServiceId;

    @Schema(description = "Identifier of the service in the Service Catalog",
            example = "3b8e5f21-9c4d-4a17-8e62-1f0d7a9c5b34")
    UUID serviceId;

    @Schema(description = "Name of the service when the entry was posted", example = "GST Monthly Return Filing")
    String serviceName;

    @Schema(description = "How the service is billed", example = "MONTHLY")
    BillingFrequency serviceType;

    @Schema(description = "For a service charge, the date the service fell due, which it bills; for a "
            + "payment, the date it was received", example = "2026-10-07")
    LocalDate dueDate;

    @Schema(description = "Indian financial year (1 April to 31 March) the due date falls in",
            example = "2026-27")
    String financialYear;

    @Schema(description = "Date the entry was posted to the ledger", example = "2026-10-13")
    LocalDate entryDate;

    @Schema(description = "Standard fee of the service, before discount and excluding GST", example = "5000.00")
    BigDecimal serviceFee;

    @Schema(description = "Discount granted on the service, as a percentage", example = "5.00")
    BigDecimal discountPercent;

    @Schema(description = "Amount taken off the service fee by the discount", example = "250.00")
    BigDecimal discountAmount;

    @Schema(description = "Service fee after discount, on which GST is charged", example = "4750.00")
    BigDecimal taxableAmount;

    @Schema(description = "GST rate applied, as a percentage", example = "18.00")
    BigDecimal gstRatePercent;

    @Schema(description = "GST charged on the taxable amount", example = "855.00")
    BigDecimal gstAmount;

    @Schema(description = "For a service charge, the amount owed: the taxable amount plus GST. For a "
            + "payment, the amount received as a negative number", example = "5605.00")
    BigDecimal totalAmount;

    @Schema(description = "How a payment was received", example = "BANK_TRANSFER")
    PaymentMethod paymentMethod;

    @Schema(description = "The payer's reference for a payment, such as a UTR or cheque number",
            example = "HDFC0001234567890")
    String paymentReference;

    @Schema(description = "Free-text note about a payment", example = "Settles the incorporation invoice")
    String comment;

    @Schema(description = "When the entry was posted", example = "2026-10-13T04:30:00Z")
    Instant createdAt;

    @Schema(description = "When the entry was last changed; its posting time until its rates are corrected",
            example = "2026-10-13T04:30:00Z")
    Instant updatedAt;

    /**
     * Maps a persisted entry onto its API representation.
     *
     * @param entity the stored ledger entry
     * @return the response payload
     */
    public static LedgerEntryResponse from(LedgerEntry entity) {
        return LedgerEntryResponse.builder()
                .id(entity.getId())
                .clientId(entity.getClientId())
                .companyId(entity.getCompanyId())
                .entryType(entity.getEntryType())
                .companyServiceId(entity.getCompanyServiceId())
                .serviceId(entity.getCatalogServiceId())
                .serviceName(entity.getServiceName())
                .serviceType(entity.getServiceType())
                .dueDate(entity.getDueDate())
                .financialYear(entity.getFinancialYear())
                .entryDate(entity.getEntryDate())
                .serviceFee(entity.getServiceFee())
                .discountPercent(entity.getDiscountPercent())
                .discountAmount(entity.getDiscountAmount())
                .taxableAmount(entity.getTaxableAmount())
                .gstRatePercent(entity.getGstRatePercent())
                .gstAmount(entity.getGstAmount())
                .totalAmount(entity.getTotalAmount())
                .paymentMethod(entity.getPaymentMethod())
                .paymentReference(entity.getPaymentReference())
                .comment(entity.getComment())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
