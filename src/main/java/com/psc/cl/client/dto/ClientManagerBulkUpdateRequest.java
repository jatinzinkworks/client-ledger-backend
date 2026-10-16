package com.psc.cl.client.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

/**
 * Payload for assigning several clients to one manager at once.
 *
 * <p>A client named more than once is simply assigned once: every mention asks for the same manager,
 * so there is nothing ambiguous to reject.
 *
 * @param managerId the manager to assign every named client to
 * @param clientIds the clients to assign
 */
@Builder
@Schema(name = "ClientManagerBulkUpdateRequest", description = "Several clients to assign to one manager")
public record ClientManagerBulkUpdateRequest(

        @NotNull(message = "must be provided")
        @Schema(description = "Identifier of the manager from the Manager Catalog who is to look after "
                + "every client named", example = "e9b2c4d1-7f35-4a68-91c0-3d8a5e2f6b47",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID managerId,

        @NotEmpty(message = "must contain at least one client")
        @Size(max = ClientManagerBulkUpdateRequest.MAX_CLIENTS, message = "must not exceed 200 clients")
        @ArraySchema(arraySchema = @Schema(description = "Identifiers of the clients to assign to the "
                + "manager", requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(example = "7a3d9e10-5b2c-4f81-a6e4-2c8b1d0f9e35"),
                minItems = 1, maxItems = ClientManagerBulkUpdateRequest.MAX_CLIENTS)
        List<@NotNull(message = "must not be null") UUID> clientIds) {

    /** Most clients one request may reassign. */
    public static final int MAX_CLIENTS = 200;
}
