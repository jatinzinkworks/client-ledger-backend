package com.psc.cl.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI metadata for the service.
 *
 * <p>The {@code bearerAuth} scheme is declared here so the Swagger UI exposes an "Authorize"
 * dialog. It is not yet applied globally because no endpoint is protected at this stage —
 * secured endpoints should opt in with {@code @SecurityRequirement(name = "bearerAuth")}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Client Ledger API",
                version = "v1",
                description = "REST API for the PSC Client Ledger backend service.",
                contact = @Contact(name = "PSC Client Ledger Team")),
        servers = @Server(url = "/", description = "Default server"))
@SecurityScheme(
        name = "bearerAuth",
        description = "JWT bearer token. Supply the raw token — the 'Bearer ' prefix is added for you.",
        type = SecuritySchemeType.HTTP,
        in = SecuritySchemeIn.HEADER,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
