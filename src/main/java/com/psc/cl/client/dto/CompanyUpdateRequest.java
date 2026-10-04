package com.psc.cl.client.dto;

import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.CompanyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A company supplied as part of a client update.
 *
 * <p>Carrying the identifier of one of the client's existing companies updates that company in
 * place, so it keeps its identifier. Leaving the identifier out adds a new company. Every other
 * field follows the same rules as on create.
 */
@Builder
@Schema(name = "CompanyUpdateRequest",
        description = "Company of the client: an existing one when it carries an id, otherwise a new one")
public record CompanyUpdateRequest(

        @Schema(description = "Identifier of an existing company of this client to update. Omit to "
                + "add a new company", example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
        UUID id,

        @NotBlank(message = "must not be blank")
        @Size(max = ClientCompany.MAX_COMPANY_NAME_LENGTH, message = "must not exceed 200 characters")
        @Schema(description = "Registered name of the company", example = "Mehta Textiles Pvt Ltd",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = ClientCompany.MAX_COMPANY_NAME_LENGTH)
        String companyName,

        @NotNull(message = "must be provided")
        @Schema(description = "Legal form of the company", example = "PRIVATE_LIMITED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        CompanyType companyType,

        @NotBlank(message = "must not be blank")
        @Size(max = ClientCompany.MAX_ADDRESS_LENGTH, message = "must not exceed 500 characters")
        @Schema(description = "Registered address of the company",
                example = "12 MG Road, Bengaluru, Karnataka 560001",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = ClientCompany.MAX_ADDRESS_LENGTH)
        String address,

        @DecimalMin(value = "0.00", message = "must not be negative")
        @DecimalMax(value = "100.00", message = "must not exceed 100")
        @Digits(integer = 3, fraction = 2, message = "must have at most 2 decimal places")
        @Schema(description = "Discount granted on this company's work, as a percentage. When "
                + "omitted, the company takes the client's discount", example = "5.00",
                minimum = "0", maximum = "100")
        BigDecimal discountPercent) implements CompanyDetails {
}
