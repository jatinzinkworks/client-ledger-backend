package com.psc.cl.client.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The next free client id: one above the highest held.
 *
 * @param clientId the client id to give the next client added
 */
@Schema(name = "NextClientIdResponse", description = "The next free client id")
public record NextClientIdResponse(

        @Schema(description = "One above the highest client id held, or PSC-00001 when there are no clients",
                example = "PSC-00042")
        String clientId) {
}
