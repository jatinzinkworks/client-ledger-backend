package com.psc.cl.globalsettings.dto;

import com.psc.cl.globalsettings.model.FirmDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Payload for creating or updating the firm details printed on invoices and Excel exports.
 *
 * <p>The firm name, GSTIN and address are mandatory because every invoice carries them. The
 * registration number, email and phone may be omitted by a firm that does not hold them.
 * Values are trimmed before storage, and the GSTIN is stored upper case.
 */
@Builder
@Schema(name = "FirmDetailsRequest", description = "Firm details to store under Global Settings")
public record FirmDetailsRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = FirmDetails.MAX_FIRM_NAME_LENGTH, message = "must not exceed 200 characters")
        @Schema(description = "Registered name of the firm, as printed on invoices",
                example = "Pranay Singhal & Company", requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = FirmDetails.MAX_FIRM_NAME_LENGTH)
        String firmName,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][1-9A-Za-z][Zz][0-9A-Za-z]$",
                message = "must be a valid 15 character GSTIN")
        @Schema(description = "Goods and Services Tax Identification Number",
                example = "27AAKFP4471M1ZS", requiredMode = Schema.RequiredMode.REQUIRED)
        String gstin,

        @Size(max = FirmDetails.MAX_REGISTRATION_NO_LENGTH, message = "must not exceed 50 characters")
        @Schema(description = "Firm registration number issued by the regulating body",
                example = "FRN 0148290W", maxLength = FirmDetails.MAX_REGISTRATION_NO_LENGTH)
        String firmRegistrationNo,

        @Email(message = "must be a valid email address")
        @Size(max = FirmDetails.MAX_EMAIL_LENGTH, message = "must not exceed 254 characters")
        @Schema(description = "Contact email address printed on invoices",
                example = "accounts@pranaysinghal.in", maxLength = FirmDetails.MAX_EMAIL_LENGTH)
        String email,

        @Pattern(regexp = "^[+]?[0-9 ()-]{6,30}$",
                message = "must contain only digits, spaces, brackets, hyphens and an optional "
                        + "leading plus")
        @Size(max = FirmDetails.MAX_PHONE_LENGTH, message = "must not exceed 30 characters")
        @Schema(description = "Contact phone number printed on invoices", example = "+91 22 4012 8890",
                maxLength = FirmDetails.MAX_PHONE_LENGTH)
        String phone,

        @NotBlank(message = "must not be blank")
        @Size(max = FirmDetails.MAX_ADDRESS_LENGTH, message = "must not exceed 500 characters")
        @Schema(description = "Postal address printed on invoices",
                example = "302, Hubtown Solaris, N.S. Phadke Marg, Andheri East, Mumbai 400069",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = FirmDetails.MAX_ADDRESS_LENGTH)
        String address) {
}
