package com.psc.cl.client.controller;

import com.psc.cl.client.dto.ClientBirthdayResponse;
import com.psc.cl.config.ApiPaths;
import com.psc.cl.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Contract for looking up clients' birthdays.
 */
@Tag(name = "Birthdays", description = "Clients' birthdays, to greet them on the day")
@RequestMapping(value = ClientBirthdayApi.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public interface ClientBirthdayApi {

    /** Base path shared by every Birthday endpoint. */
    String BASE_PATH = ApiPaths.V1 + "/birthdays";

    @Operation(
            summary = "List the clients with a birthday in a month",
            description = "Returns every client whose birthday falls in the given month, whatever the year "
                    + "they were born, ordered by day of the month, then first and last name. Clients without "
                    + "a birthday recorded are left out. The list is returned whole; it is not paged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The clients with a birthday in the month, empty when there are none",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(
                                    schema = @Schema(implementation = ClientBirthdayResponse.class)))),
            @ApiResponse(responseCode = "400",
                    description = "month is missing, not a whole number, or not between 1 and 12",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))})
    @GetMapping
    ResponseEntity<List<ClientBirthdayResponse>> listBirthdays(
            @Parameter(description = "Month of the year, 1 for January to 12 for December", required = true,
                    example = "7", schema = @Schema(type = "integer", minimum = "1", maximum = "12"))
            @RequestParam int month);
}
