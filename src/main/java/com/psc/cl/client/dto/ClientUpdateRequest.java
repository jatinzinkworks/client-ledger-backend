package com.psc.cl.client.dto;

import com.psc.cl.client.model.Client;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for replacing a client wholesale.
 *
 * <p>Client fields follow the same rules as on create, and nothing is left behind from the previous
 * version: an omitted discount resets to zero and an omitted birthday is cleared. The client's companies are not part of this payload
 * and are left as they are; add or remove them through their own endpoints.
 */
@Builder
@Schema(name = "ClientUpdateRequest", description = "New content for a client, excluding their companies")
public record ClientUpdateRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = Client.MAX_NAME_LENGTH, message = "must not exceed 100 characters")
        @Schema(description = "Given name of the client", example = "Rohan",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = Client.MAX_NAME_LENGTH)
        String firstName,

        @NotBlank(message = "must not be blank")
        @Size(max = Client.MAX_NAME_LENGTH, message = "must not exceed 100 characters")
        @Schema(description = "Family name of the client", example = "Mehta",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = Client.MAX_NAME_LENGTH)
        String lastName,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "^[+]?[0-9 ()-]{6,20}$",
                message = "must contain only digits, spaces, brackets, hyphens and an optional "
                        + "leading plus")
        @Size(max = Client.MAX_PHONE_LENGTH, message = "must not exceed 20 characters")
        @Schema(description = "Contact phone number", example = "+91 98200 12345",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = Client.MAX_PHONE_LENGTH)
        String phone,

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email address")
        @Size(max = Client.MAX_EMAIL_LENGTH, message = "must not exceed 254 characters")
        @Schema(description = "Contact email address", example = "rohan.mehta@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = Client.MAX_EMAIL_LENGTH)
        String email,

        @NotNull(message = "must be provided")
        @Schema(description = "Identifier of the manager from the Manager Catalog who looks after "
                + "the client", example = "e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID managerId,

        @PastOrPresent(message = "must not be in the future")
        @Schema(description = "Date of birth of the client; not after today. Optional; omitting it clears any "
                + "stored before", example = "1985-07-23")
        LocalDate birthday,

        @DecimalMin(value = "0.00", message = "must not be negative")
        @DecimalMax(value = "100.00", message = "must not exceed 100")
        @Digits(integer = 3, fraction = 2, message = "must have at most 2 decimal places")
        @Schema(description = "Discount granted to the client, as a percentage. Also applied to "
                + "every company that does not set its own", example = "10.00",
                defaultValue = "0.00", minimum = "0", maximum = "100")
        BigDecimal discountPercent) implements ClientDetails {
}
