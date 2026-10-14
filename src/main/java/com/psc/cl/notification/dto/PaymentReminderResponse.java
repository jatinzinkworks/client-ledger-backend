package com.psc.cl.notification.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * The outcome of sending payment reminders to every client with an amount pending.
 */
@Value
@Builder
@Schema(name = "PaymentReminderResponse",
        description = "The outcome of sending payment reminders to every client with an amount pending")
public class PaymentReminderResponse {

    @Schema(description = "Indian financial year whose figures the reminders detail: the current one. The amounts "
            + "asked for span every year", example = "2026-27")
    String financialYear;

    @Schema(description = "How many clients there are", example = "42")
    int clientCount;

    @Schema(description = "How many clients were sent a reminder", example = "17")
    int sentCount;

    @Schema(description = "How many clients were not reminded because nothing is pending across every year: "
            + "settled, in credit, or never billed", example = "25")
    int skippedCount;

    @ArraySchema(arraySchema = @Schema(description = "The clients reminded, by name, with what each owes"),
            schema = @Schema(implementation = ClientReminderResult.class))
    List<ClientReminderResult> sent;

    @ArraySchema(arraySchema = @Schema(description = "The clients owing who could not be reminded; empty when "
            + "every reminder was sent. The other clients are reminded regardless"),
            schema = @Schema(implementation = ClientEmailFailure.class))
    List<ClientEmailFailure> failures;
}
