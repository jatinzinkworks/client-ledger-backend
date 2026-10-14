package com.psc.cl.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * A client owing who could not be sent a WhatsApp payment reminder in a run across every client.
 */
@Value
@Builder
@Schema(name = "ClientWhatsAppFailure", description = "A client owing who could not be sent a WhatsApp reminder")
public class ClientWhatsAppFailure {

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Name of the client", example = "Rohan Mehta")
    String clientName;

    @Schema(description = "Why the reminder was not sent, such as a phone number WhatsApp cannot use, or "
            + "WhatsApp refusing the message", example = "The client's phone number cannot be used for WhatsApp")
    String error;
}
