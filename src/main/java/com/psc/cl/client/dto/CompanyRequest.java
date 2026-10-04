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

/**
 * A company supplied as part of a client payload.
 *
 * <p>Every field except the discount is mandatory. Values are trimmed before storage, and a
 * company without a discount of its own takes the client's.
 */
@Builder
@Schema(name = "CompanyRequest", description = "Company owned by, or acted for on behalf of, the client")
public record CompanyRequest(

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
