package com.psc.cl.client.dto;

import com.psc.cl.client.model.Client;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Payload for creating a client.
 *
 * <p>Every field except the discount is mandatory, and at least one company must be supplied.
 * Values are trimmed before storage, and the assigned manager must exist in the Manager Catalog.
 * The discount defaults to zero, and applies to every company that does not set its own.
 */
@Builder
@Schema(name = "ClientRequest", description = "Client to add, with the companies they hold")
public record ClientRequest(

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

        @DecimalMin(value = "0.00", message = "must not be negative")
        @DecimalMax(value = "100.00", message = "must not exceed 100")
        @Digits(integer = 3, fraction = 2, message = "must have at most 2 decimal places")
        @Schema(description = "Discount granted to the client, as a percentage. Also applied to "
                + "every company that does not set its own", example = "10.00",
                defaultValue = "0.00", minimum = "0", maximum = "100")
        BigDecimal discountPercent,

        @Schema(description = "Whether the client has agreed to receive WhatsApp messages from the firm, such as "
                + "payment reminders. WhatsApp requires this consent before any message is sent. Omit for false. "
                + "Change it later with PATCH /clients/{id}/whatsapp-opt-in",
                example = "true", defaultValue = "false")
        Boolean whatsappOptIn,

        @NotEmpty(message = "must contain at least one company")
        @Size(max = ClientRequest.MAX_COMPANIES, message = "must not contain more than 50 companies")
        @ArraySchema(
                arraySchema = @Schema(description = "Companies the client owns or acts for",
                        requiredMode = Schema.RequiredMode.REQUIRED),
                minItems = 1, maxItems = ClientRequest.MAX_COMPANIES,
                schema = @Schema(implementation = CompanyRequest.class))
        List<@NotNull(message = "must not be null") @Valid CompanyRequest> companies) implements ClientDetails {

    /** Most companies accepted for one client in a single request. */
    public static final int MAX_COMPANIES = 50;
}
