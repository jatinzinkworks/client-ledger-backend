package com.psc.cl.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.client.model.Client;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.managercatalog.model.Manager;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A client as shown in the client list: contact details and the names of their companies, without
 * the full company records.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ClientSummaryResponse", description = "A client as shown in the client list")
public class ClientSummaryResponse {

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

    @ArraySchema(arraySchema = @Schema(description = "Names of the companies the client holds, ordered by name"),
            schema = @Schema(example = "Mehta Textiles Pvt Ltd"))
    List<String> companyNames;

    @Schema(description = "What the client still owes: everything billed to all of their companies less "
            + "every payment received, across all financial years. Negative when they have paid more than "
            + "billed", example = "19630.00")
    BigDecimal totalPending;

    /**
     * Maps a persisted entity onto its list representation.
     *
     * @param entity       the stored client, with its companies loaded
     * @param manager      the manager assigned to the client
     * @param totalPending what the client still owes, across all companies and financial years
     * @return the summary payload
     */
    public static ClientSummaryResponse from(Client entity, Manager manager, BigDecimal totalPending) {
        return ClientSummaryResponse.builder()
                .id(entity.getId())
                .clientId(entity.getClientId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .managerId(entity.getManagerId())
                .managerName(ClientResponse.managerName(manager))
                .birthday(entity.getBirthday())
                .discountPercent(entity.getDiscountPercent())
                .companyNames(entity.getCompanies().stream()
                        .map(ClientCompany::getCompanyName)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList())
                .totalPending(totalPending)
                .build();
    }
}
