package com.psc.cl.client.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * Records whether a client agrees to receive WhatsApp messages, without touching anything else of
 * theirs.
 *
 * @param whatsappOptIn true when the client has agreed, false when they have withdrawn or not given consent
 */
@Builder
@Schema(name = "WhatsAppOptInRequest", description = "Whether a client agrees to receive WhatsApp messages")
public record WhatsAppOptInRequest(
        @NotNull(message = "must be provided")
        @Schema(description = "True when the client has agreed to receive WhatsApp messages from the firm, such "
                + "as payment reminders; false when they have withdrawn or not given that consent",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean whatsappOptIn) {
}
