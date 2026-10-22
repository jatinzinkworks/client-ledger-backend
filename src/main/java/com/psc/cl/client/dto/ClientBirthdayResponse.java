package com.psc.cl.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.client.model.Client;
import com.psc.cl.managercatalog.model.Manager;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A client's birthday, with what is needed to greet them and who looks after them.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ClientBirthdayResponse", description = "A client's birthday")
public class ClientBirthdayResponse {

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Given name of the client", example = "Rohan")
    String firstName;

    @Schema(description = "Family name of the client", example = "Mehta")
    String lastName;

    @Schema(description = "Date of birth of the client", example = "1985-07-23")
    LocalDate birthday;

    @Schema(description = "Day of the month the birthday falls on", example = "23")
    int dayOfMonth;

    @Schema(description = "Contact phone number", example = "+91 98200 12345")
    String phone;

    @Schema(description = "Contact email address", example = "rohan.mehta@example.com")
    String email;

    @Schema(description = "Identifier of the manager who looks after the client",
            example = "e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47")
    UUID managerId;

    @Schema(description = "Full name of the manager who looks after the client", example = "Pranay Singhal")
    String managerName;

    /**
     * Maps a client with a birthday onto its representation.
     *
     * @param entity  the stored client, whose birthday is set
     * @param manager the manager assigned to the client
     * @return the birthday payload
     */
    public static ClientBirthdayResponse from(Client entity, Manager manager) {
        return ClientBirthdayResponse.builder()
                .clientId(entity.getId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .birthday(entity.getBirthday())
                .dayOfMonth(entity.getBirthday().getDayOfMonth())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .managerId(entity.getManagerId())
                .managerName(ClientResponse.managerName(manager))
                .build();
    }
}
