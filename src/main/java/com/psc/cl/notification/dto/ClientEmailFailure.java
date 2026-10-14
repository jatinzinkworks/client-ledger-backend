package com.psc.cl.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * A client whose ledger summary could not be emailed in a run across every client.
 */
@Value
@Builder
@Schema(name = "ClientEmailFailure", description = "A client whose ledger summary could not be emailed in a run")
public class ClientEmailFailure {

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Name of the client", example = "Rohan Mehta")
    String clientName;

    @Schema(description = "Why the email was not sent; nothing was sent to the client",
            example = "The ledger summary email could not be sent: the mail server refused it or could not "
                    + "be reached")
    String error;
}
