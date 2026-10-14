package com.psc.cl.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * A client emailed their ledger summary in a run across every client.
 */
@Value
@Builder
@Schema(name = "ClientEmailResult", description = "A client emailed their ledger summary in a run")
public class ClientEmailResult {

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Name of the client", example = "Rohan Mehta")
    String clientName;
}
