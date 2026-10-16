package com.psc.cl.client.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/**
 * What a client import created: every client on the uploaded workbook, with their companies.
 */
@Value
@Builder
@Schema(name = "ClientImportResponse", description = "The clients and companies created by an import")
public class ClientImportResponse {

    @Schema(description = "How many clients were created", example = "2")
    int clientCount;

    @Schema(description = "How many companies were created, across every client", example = "3")
    int companyCount;

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
    }
}
