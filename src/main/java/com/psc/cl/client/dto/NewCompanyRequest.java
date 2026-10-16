package com.psc.cl.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.CompanyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A company supplied when a client is created: the fields of {@link CompanyRequest}, plus the
 * balance the company is taken on with.
 *
 * <p>Every field except the discount and the opening balance is mandatory. Values are trimmed before
 * storage, and a company without a discount of its own takes the client's. An opening balance of
 * zero, or none, records nothing; its date may be given only with a balance.
 */
@Builder(toBuilder = true)
@Schema(name = "NewCompanyRequest", description = "Company owned by, or acted for on behalf of, a client being "
        + "added, with the balance it is taken on with")
public record NewCompanyRequest(

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
        BigDecimal discountPercent,

        @Digits(integer = NewCompanyRequest.OPENING_BALANCE_INTEGER_DIGITS, fraction = 2,
                message = "must have at most 12 digits before the decimal point and 2 after it")
        @Schema(description = "The balance the company is taken on with, brought over from before: positive "
                + "when the company owes it, negative when it starts in credit, such as an advance. It is "
                + "recorded in the company's ledger as an opening balance, counted in what is pending but "
                + "not as billed. Omit, or send 0, for none", example = "25000.00")
        BigDecimal openingBalance,

        @Schema(description = "The date the opening balance stood at, which places it in a financial year; not "
                + "after today. Only with an openingBalance. Defaults to the day the client is added",
                example = "2026-03-31")
        LocalDate openingBalanceDate) implements CompanyDetails {

    /** Most digits before the decimal point of an opening balance, matching the ledger's amount column. */
    public static final int OPENING_BALANCE_INTEGER_DIGITS = 12;

    /**
     * Whether an opening balance is to be recorded: one was given, and it is not zero.
     *
     * @return true when the company has an opening balance to record
     */
    @JsonIgnore
    public boolean hasOpeningBalance() {
        return openingBalance != null && openingBalance.signum() != 0;
    }

    /**
     * A date is a date of a balance, so it may only come with one.
     *
     * @return true unless a date is given without a balance to date
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "must be given only with an openingBalance other than 0")
    public boolean isOpeningBalanceDated() {
        return openingBalanceDate == null || hasOpeningBalance();
    }
}
