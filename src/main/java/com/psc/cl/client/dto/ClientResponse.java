package com.psc.cl.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.client.model.Client;
import com.psc.cl.ledger.dto.OpeningBalance;
import com.psc.cl.managercatalog.model.Manager;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    @Schema(description = "The firm's own identifier for the client; fixed when the client is added",
            example = "PSC-00001")
    String clientId;

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

    @Schema(description = "Date of birth of the client. Left out when not recorded", example = "1985-07-23")
    LocalDate birthday;

    @Schema(description = "Discount granted to the client, as a percentage", example = "10.00")
    BigDecimal discountPercent;

    @ArraySchema(arraySchema = @Schema(description = "Companies the client holds, ordered by name"),
            schema = @Schema(implementation = CompanyResponse.class))
    List<CompanyResponse> companies;

    @Schema(description = "What the client still owes: everything billed to all of their companies less "
            + "every payment received, across all financial years. Negative when they have paid more than "
            + "billed. Returned when fetching a client by id", example = "19630.00")
    BigDecimal totalPending;

    @Schema(description = "When the client was added", example = "2026-10-04T10:15:30Z")
    Instant createdAt;

    @Schema(description = "When the client was last updated", example = "2026-10-04T10:15:30Z")
    Instant updatedAt;

    /**
     * Maps a persisted entity onto its API representation.
     *
     * @param entity   the stored client
     * @param manager  the manager assigned to the client
     * @param openings the opening balances of the client's companies, by company
     * @return the response payload
     */
    public static ClientResponse from(Client entity, Manager manager, Map<UUID, OpeningBalance> openings) {
        return builderFrom(entity, manager, openings).build();
    }

    /**
     * Maps a persisted entity onto its API representation, together with what the client still owes.
     *
     * @param entity       the stored client
     * @param manager      the manager assigned to the client
     * @param openings     the opening balances of the client's companies, by company
     * @param totalPending what the client still owes, across all companies and financial years
     * @return the response payload
     */
    public static ClientResponse from(Client entity, Manager manager, Map<UUID, OpeningBalance> openings,
                                      BigDecimal totalPending) {
        return builderFrom(entity, manager, openings).totalPending(totalPending).build();
    }

    private static ClientResponseBuilder builderFrom(Client entity, Manager manager,
                                                     Map<UUID, OpeningBalance> openings) {
        return ClientResponse.builder()
                .id(entity.getId())
                .clientId(entity.getClientId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .managerId(entity.getManagerId())
                .managerName(managerName(manager))
                .birthday(entity.getBirthday())
                .discountPercent(entity.getDiscountPercent())
                .companies(companiesOf(entity, openings))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());
    }

    /**
     * Maps the companies of a client onto their API representation, ordered by name.
     *
     * @param entity   the stored client, with its companies loaded
     * @param openings the opening balances of the client's companies, by company
     * @return the companies, ordered by name
     */
    public static List<CompanyResponse> companiesOf(Client entity, Map<UUID, OpeningBalance> openings) {
        return entity.getCompanies().stream()
                .map(company -> CompanyResponse.from(company, openings.get(company.getId())))
                .sorted(BY_COMPANY_NAME)
                .toList();
    }

    /** How a manager's name is shown on every client payload. */
    static String managerName(Manager manager) {
        return manager.getFirstName() + " " + manager.getLastName();
    }
}
