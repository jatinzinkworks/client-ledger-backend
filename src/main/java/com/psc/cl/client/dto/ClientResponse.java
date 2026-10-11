package com.psc.cl.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.client.model.Client;
import com.psc.cl.managercatalog.model.Manager;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A client as held in the ledger, with their companies.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ClientResponse", description = "A client held in the ledger")
public class ClientResponse {

    /** Companies are listed by name so the order is the same however the client was loaded. */
    public static final Comparator<CompanyResponse> BY_COMPANY_NAME =
            Comparator.comparing(CompanyResponse::getCompanyName, String.CASE_INSENSITIVE_ORDER);

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID id;

    @Schema(description = "Given name of the client", example = "Rohan")
    String firstName;

    @Schema(description = "Family name of the client", example = "Mehta")
    String lastName;

    @Schema(description = "Contact phone number", example = "+91 98200 12345")
    String phone;

    @Schema(description = "Contact email address", example = "rohan.mehta@example.com")
    String email;

    @Schema(description = "Identifier of the manager who looks after the client",
            example = "e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47")
    UUID managerId;

    @Schema(description = "Full name of the manager who looks after the client",
            example = "Pranay Singhal")
    String managerName;

    @Schema(description = "Discount granted to the client, as a percentage", example = "10.00")
    BigDecimal discountPercent;

    @ArraySchema(arraySchema = @Schema(description = "Companies the client holds, ordered by name"),
            schema = @Schema(implementation = CompanyResponse.class))
    List<CompanyResponse> companies;

    @Schema(description = "When the client was added", example = "2026-10-04T10:15:30Z")
    Instant createdAt;

    @Schema(description = "When the client was last updated", example = "2026-10-04T10:15:30Z")
    Instant updatedAt;

    /**
     * Maps a persisted entity onto its API representation.
     *
     * @param entity  the stored client
     * @param manager the manager assigned to the client
     * @return the response payload
     */
    public static ClientResponse from(Client entity, Manager manager) {
        return ClientResponse.builder()
                .id(entity.getId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .managerId(entity.getManagerId())
                .managerName(managerName(manager))
                .discountPercent(entity.getDiscountPercent())
                .companies(companiesOf(entity))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Maps the companies of a client onto their API representation, ordered by name.
     *
     * @param entity the stored client, with its companies loaded
     * @return the companies, ordered by name
     */
    public static List<CompanyResponse> companiesOf(Client entity) {
        return entity.getCompanies().stream()
                .map(CompanyResponse::from)
                .sorted(BY_COMPANY_NAME)
                .toList();
    }

    /** How a manager's name is shown on every client payload. */
    static String managerName(Manager manager) {
        return manager.getFirstName() + " " + manager.getLastName();
    }
}
