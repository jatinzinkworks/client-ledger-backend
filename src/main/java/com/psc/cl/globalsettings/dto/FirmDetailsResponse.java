package com.psc.cl.globalsettings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.globalsettings.model.FirmDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * Firm details as stored under Global Settings.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "FirmDetailsResponse", description = "Firm details currently held under Global Settings")
public class FirmDetailsResponse {

    @Schema(description = "Identifier of the firm details record",
            example = "7c1d9f42-5a3b-4e88-9b21-6f0e8d4a2c17")
    UUID id;

    @Schema(description = "Registered name of the firm", example = "Pranay Singhal & Company")
    String firmName;

    @Schema(description = "Goods and Services Tax Identification Number", example = "27AAKFP4471M1ZS")
    String gstin;

    @Schema(description = "Firm registration number", example = "FRN 0148290W")
    String firmRegistrationNo;

    @Schema(description = "Contact email address", example = "accounts@pranaysinghal.in")
    String email;

    @Schema(description = "Contact phone number", example = "+91 22 4012 8890")
    String phone;

    @Schema(description = "Postal address",
            example = "302, Hubtown Solaris, N.S. Phadke Marg, Andheri East, Mumbai 400069")
    String address;

    @Schema(description = "When the record was created", example = "2026-09-29T10:15:30Z")
    Instant createdAt;

    @Schema(description = "When the record was last updated", example = "2026-09-29T10:15:30Z")
    Instant updatedAt;

    /**
     * Maps a persisted entity onto its API representation.
     *
     * @param entity the stored firm details
     * @return the response payload
     */
    public static FirmDetailsResponse from(FirmDetails entity) {
        return FirmDetailsResponse.builder()
                .id(entity.getId())
                .firmName(entity.getFirmName())
                .gstin(entity.getGstin())
                .firmRegistrationNo(entity.getFirmRegistrationNo())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .address(entity.getAddress())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
