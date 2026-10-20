package com.psc.cl.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What a client import created: every client on the uploaded workbook, with their companies and the
 * opening balances those companies were taken on with.
 */
@Value
@Builder
@Schema(name = "ClientImportResponse", description = "The clients and companies created by an import")
public class ClientImportResponse {

    @Schema(description = "How many clients were created", example = "2")
    int clientCount;

    @Schema(description = "How many companies were created, across every client", example = "3")
    int companyCount;

    @Schema(description = "How many of the companies created were taken on with an opening balance, across "
            + "every client", example = "2")
    int openingBalanceCount;

    @Schema(description = "The opening balances recorded, added up across every client: positive when owed "
            + "on the whole, negative when in credit; 0.00 when none were", example = "20000.00")
    BigDecimal openingBalanceTotal;

    @ArraySchema(arraySchema = @Schema(description = "Each client created, in the order of the Clients sheet"))
    List<ImportedClient> clients;

    /**
     * One client created by an import.
     */
    @Value
    @Builder
    @Schema(name = "ImportedClient", description = "A client created by an import")
    public static class ImportedClient {

        @Schema(description = "The client's reference on the Clients sheet", example = "C1")
        String clientRef;

        @Schema(description = "Identifier given to the new client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
        UUID clientId;

        @Schema(description = "Full name of the client", example = "Rohan Mehta")
        String clientName;

        @Schema(description = "How many companies were created for the client", example = "2")
        int companyCount;

        @Schema(description = "How many of the client's companies were taken on with an opening balance",
                example = "1")
        int openingBalanceCount;

        @Schema(description = "The opening balances recorded for the client's companies, added up: positive "
                + "when owed on the whole, negative when in credit; 0.00 when none were", example = "25000.00")
        BigDecimal openingBalanceTotal;

        @ArraySchema(arraySchema = @Schema(description = "Each company created for the client, by name, with "
                + "the opening balance recorded for it"))
        List<ImportedCompany> companies;
    }

    /**
     * One company created by an import, with the opening balance recorded for it.
     */
    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "ImportedCompany", description = "A company created by an import")
    public static class ImportedCompany {

        @Schema(description = "Identifier given to the new company", example = "4c1f8a52-2d6b-4e93-b7a0-9e5d3c1f6a28")
        UUID companyId;

        @Schema(description = "Registered name of the company", example = "Mehta Textiles Pvt Ltd")
        String companyName;

        @Schema(description = "The opening balance recorded for the company: positive when owed, negative when "
                + "in credit. Left out when the company brought none", example = "25000.00")
        BigDecimal openingBalanceAmount;

        @Schema(description = "The date the opening balance stood at. Left out when the company brought none",
                example = "2026-03-31")
        LocalDate openingBalanceDate;
    }
}
