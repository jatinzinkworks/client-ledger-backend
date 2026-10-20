package com.psc.cl.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.CompanyType;
import com.psc.cl.ledger.dto.CompanyBalance;
import com.psc.cl.ledger.dto.OpeningBalance;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A company as held against a client.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "CompanyResponse", description = "A company held against a client")
public class CompanyResponse {

    @Schema(description = "Identifier of the company", example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
    UUID id;

    @Schema(description = "Registered name of the company", example = "Mehta Textiles Pvt Ltd")
    String companyName;

    @Schema(description = "Legal form of the company", example = "PRIVATE_LIMITED")
    CompanyType companyType;

    @Schema(description = "Registered address of the company",
            example = "12 MG Road, Bengaluru, Karnataka 560001")
    String address;

    @Schema(description = "Discount granted on this company's work, as a percentage: the company's "
            + "own, or the client's current discount when the company has none of its own", example = "5.00")
    BigDecimal discountPercent;

    @Schema(description = "True when the company has no discount of its own, so discountPercent is "
            + "the client's and follows any change to it", example = "false")
    Boolean discountInherited;

    @Schema(description = "The balance the company was taken on with, brought over from before: positive when "
            + "owed, negative when in credit. Left out when the company has none. Unlike openingBalance, "
            + "returned whatever its financial year", example = "25000.00")
    BigDecimal openingBalanceAmount;

    @Schema(description = "The date the opening balance stood at, which places it in a financial year. Left out "
            + "when the company has none", example = "2026-03-31")
    LocalDate openingBalanceDate;

    @Schema(description = "Number of services assigned to the company. Only returned when listing a "
            + "client's companies", example = "3")
    Long serviceCount;

    @Schema(description = "Financial year the billing figures below cover. Only returned when listing a "
            + "client's companies for a financial year", example = "2026-27")
    String financialYear;

    @Schema(description = "Sum of the service charges falling due in the financial year: taxable amounts "
            + "plus GST. Only returned when listing for a financial year", example = "39235.00")
    BigDecimal totalBilled;

    @Schema(description = "The opening balance the company was taken on with, when dated in the financial "
            + "year: positive when owed, negative when in credit; zero otherwise. Only returned when listing "
            + "for a financial year; see openingBalanceAmount for the balance whatever its year", example = "0.00")
    BigDecimal openingBalance;

    @Schema(description = "Sum of the payments received in the financial year, as a positive number. Only "
            + "returned when listing for a financial year", example = "15605.00")
    BigDecimal totalPaymentReceived;

    @Schema(description = "Billed plus opening balance less received in the financial year; negative when "
            + "more was received. Only returned when listing for a financial year", example = "23630.00")
    BigDecimal pending;

    /**
     * Maps a persisted entity onto its API representation.
     *
     * @param entity  the stored company
     * @param opening the company's opening balance, or null when it has none
     * @return the response payload
     */
    public static CompanyResponse from(ClientCompany entity, OpeningBalance opening) {
        return builderFrom(entity, opening).build();
    }

    /**
     * Maps a persisted entity onto its API representation, together with its service count.
     *
     * @param entity       the stored company
     * @param serviceCount the number of services assigned to the company
     * @param opening      the company's opening balance, or null when it has none
     * @return the response payload
     */
    public static CompanyResponse from(ClientCompany entity, long serviceCount, OpeningBalance opening) {
        return builderFrom(entity, opening).serviceCount(serviceCount).build();
    }

    /**
     * Maps a persisted entity onto its API representation, together with its service count and
     * what it was billed and paid in a financial year.
     *
     * @param entity        the stored company
     * @param serviceCount  the number of services assigned to the company
     * @param financialYear the financial year the balance covers, such as 2026-27
     * @param balance       what the company was billed and paid in that year
     * @param opening       the company's opening balance, or null when it has none
     * @return the response payload
     */
    public static CompanyResponse from(ClientCompany entity, long serviceCount, String financialYear,
                                       CompanyBalance balance, OpeningBalance opening) {
        return builderFrom(entity, opening)
                .serviceCount(serviceCount)
                .financialYear(financialYear)
                .totalBilled(balance.totalBilled())
                .openingBalance(balance.openingBalance())
                .totalPaymentReceived(balance.totalPaymentReceived())
                .pending(balance.pending())
                .build();
    }

    private static CompanyResponseBuilder builderFrom(ClientCompany entity, OpeningBalance opening) {
        return CompanyResponse.builder()
                .id(entity.getId())
                .companyName(entity.getCompanyName())
                .companyType(entity.getCompanyType())
                .address(entity.getAddress())
                .discountPercent(entity.effectiveDiscountPercent())
                .discountInherited(entity.isDiscountInherited())
                .openingBalanceAmount(opening == null ? null : opening.amount())
                .openingBalanceDate(opening == null ? null : opening.date());
    }
}
