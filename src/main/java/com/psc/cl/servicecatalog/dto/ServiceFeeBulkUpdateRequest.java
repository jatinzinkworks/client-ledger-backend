package com.psc.cl.servicecatalog.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload for changing the standard fees of several catalog services at once.
 *
 * <p>Each service may appear only once; the service layer rejects a request naming one twice,
 * since it would be unclear which fee was meant.
 *
 * @param updates the new fee of each service to change
 */
@Schema(name = "ServiceFeeBulkUpdateRequest", description = "New standard fees for several services")
public record ServiceFeeBulkUpdateRequest(

        @NotEmpty(message = "must contain at least one service")
        @Size(max = ServiceFeeBulkUpdateRequest.MAX_UPDATES, message = "must not exceed 200 services")
        @ArraySchema(arraySchema = @Schema(description = "The new fee of each service to change, each "
                + "service at most once", requiredMode = Schema.RequiredMode.REQUIRED),
                minItems = 1, maxItems = ServiceFeeBulkUpdateRequest.MAX_UPDATES)
        List<@NotNull(message = "must not be null") @Valid ServiceFeeUpdate> updates) {

    /** Most services one request may change. */
    public static final int MAX_UPDATES = 200;
}
