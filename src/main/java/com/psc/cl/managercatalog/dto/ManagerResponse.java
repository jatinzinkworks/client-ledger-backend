package com.psc.cl.managercatalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.model.ManagerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * A manager as held in the catalog.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ManagerResponse", description = "A manager held in the catalog")
public class ManagerResponse {

    @Schema(description = "Identifier of the manager",
            example = "e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47")
    UUID id;

    @Schema(description = "Given name of the manager", example = "Pranay")
    String firstName;

    @Schema(description = "Family name of the manager", example = "Singhal")
    String lastName;

    @Schema(description = "Work email address", example = "pranay.singhal@pranaysinghal.in")
    String email;

    @Schema(description = "Seniority the manager holds", example = "SENIOR_MANAGER")
    ManagerRole role;

    @Schema(description = "Mobile contact number", example = "+91 98765 43210")
    String mobileNumber;

    @Schema(description = "When the manager was added", example = "2026-10-01T10:15:30Z")
    Instant createdAt;

    @Schema(description = "When the manager was last updated", example = "2026-10-01T10:15:30Z")
    Instant updatedAt;

    /**
     * Maps a persisted entity onto its API representation.
     *
     * @param entity the stored manager
     * @return the response payload
     */
    public static ManagerResponse from(Manager entity) {
        return ManagerResponse.builder()
                .id(entity.getId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .email(entity.getEmail())
                .role(entity.getRole())
                .mobileNumber(entity.getMobileNumber())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
