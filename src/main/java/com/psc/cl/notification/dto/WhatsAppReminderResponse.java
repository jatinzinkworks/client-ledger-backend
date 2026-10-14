package com.psc.cl.notification.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The outcome of sending WhatsApp payment reminders to every client with an amount pending.
 */
@Value
@Builder
@Schema(name = "WhatsAppReminderResponse",
        description = "The outcome of sending WhatsApp payment reminders to every client with an amount pending")
public class WhatsAppReminderResponse {

    @Schema(description = "How many clients there are", example = "42")
    int clientCount;

    @Schema(description = "How many clients were sent a reminder", example = "17")
    int sentCount;

    @Schema(description = "How many clients were not messaged because they have not opted in to WhatsApp",
            example = "8")
    int notOptedInCount;

    @Schema(description = "How many opted-in clients were not reminded because nothing is pending across every "
            + "year: settled, in credit, or never billed", example = "17")
    int skippedCount;

    @ArraySchema(arraySchema = @Schema(description = "The clients reminded, by name, with what each owes"),
            schema = @Schema(implementation = ClientWhatsAppResult.class))
    List<ClientWhatsAppResult> sent;

    @ArraySchema(arraySchema = @Schema(description = "The clients owing who could not be reminded; empty when "
            + "every reminder was sent. The other clients are reminded regardless"),
            schema = @Schema(implementation = ClientWhatsAppFailure.class))
    List<ClientWhatsAppFailure> failures;
}
