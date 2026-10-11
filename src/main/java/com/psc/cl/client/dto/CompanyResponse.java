package com.psc.cl.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.psc.cl.client.model.ClientCompany;
import com.psc.cl.client.model.CompanyType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
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

    @Schema(description = "Number of services assigned to the company. Only returned when listing a "
            + "client's companies", example = "3")
    Long serviceCount;

    /**
     * Maps a persisted entity onto its API representation.
     *
     * @param entity the stored company
     * @return the response payload
     */
    public static CompanyResponse from(ClientCompany entity) {
        return builderFrom(entity).build();
    }

    /**
     * Maps a persisted entity onto its API representation, together with its service count.
     *
     * @param entity       the stored company
     * @param serviceCount the number of services assigned to the company
     * @return the response payload
     */
    public static CompanyResponse from(ClientCompany entity, long serviceCount) {
        return builderFrom(entity).serviceCount(serviceCount).build();
    }

    private static CompanyResponseBuilder builderFrom(ClientCompany entity) {
        return CompanyResponse.builder()
                .id(entity.getId())
                .companyName(entity.getCompanyName())
                .companyType(entity.getCompanyType())
                .address(entity.getAddress())
                .discountPercent(entity.effectiveDiscountPercent())
                .discountInherited(entity.isDiscountInherited());
    }
}
