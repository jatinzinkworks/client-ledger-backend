package com.psc.cl.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A client sent a WhatsApp payment reminder in a run across every client.
 */
@Value
@Builder
@Schema(name = "ClientWhatsAppResult", description = "A client sent a WhatsApp payment reminder in a run")
public class ClientWhatsAppResult {

    @Schema(description = "Identifier of the client", example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35")
    UUID clientId;

    @Schema(description = "Name of the client", example = "Rohan Mehta")
    String clientName;

    @Schema(description = "What the client owes across every financial year and all of their companies: the "
            + "amount the reminder states", example = "24235.00")
    BigDecimal pending;

    @Schema(description = "Identifier WhatsApp gave the message, for tracing its delivery",
            example = "wamid.HBgMOTE5ODIwMDEyMzQ1FQIAERgSMzQ1RjA5QkE3RTY4QzlEMEFDAA==")
    String messageId;
}
