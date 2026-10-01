package com.psc.cl.managercatalog.dto;

import com.psc.cl.managercatalog.model.Manager;
import com.psc.cl.managercatalog.model.ManagerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Payload for creating or updating a manager.
 *
 * <p>Every field is mandatory. Values are trimmed before storage, and the email address must be
 * unique across the catalog, ignoring case.
 */
@Builder
@Schema(name = "ManagerRequest", description = "Manager to store in the catalog")
public record ManagerRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = Manager.MAX_NAME_LENGTH, message = "must not exceed 100 characters")
        @Schema(description = "Given name of the manager", example = "Pranay",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = Manager.MAX_NAME_LENGTH)
        String firstName,

        @NotBlank(message = "must not be blank")
        @Size(max = Manager.MAX_NAME_LENGTH, message = "must not exceed 100 characters")
        @Schema(description = "Family name of the manager", example = "Singhal",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = Manager.MAX_NAME_LENGTH)
        String lastName,

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email address")
        @Size(max = Manager.MAX_EMAIL_LENGTH, message = "must not exceed 254 characters")
        @Schema(description = "Work email address, unique across the catalog",
                example = "pranay.singhal@pranaysinghal.in",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = Manager.MAX_EMAIL_LENGTH)
        String email,

        @NotNull(message = "must be provided")
        @Schema(description = "Seniority the manager holds", example = "SENIOR_MANAGER",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ManagerRole role,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "^[+]?[0-9 ()-]{6,20}$",
                message = "must contain only digits, spaces, brackets, hyphens and an optional "
                        + "leading plus")
        @Size(max = Manager.MAX_MOBILE_NUMBER_LENGTH, message = "must not exceed 20 characters")
        @Schema(description = "Mobile contact number", example = "+91 98765 43210",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = Manager.MAX_MOBILE_NUMBER_LENGTH)
        String mobileNumber) {
}
