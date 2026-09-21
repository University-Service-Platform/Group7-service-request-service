package com.usm.servicerequest.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes a live OpenAPI contract at /v3/api-docs and /swagger-ui.html -
 * guide §7: "publish OpenAPI specs for both services now, even in draft
 * form... publishing it before they build against it is what the project's
 * change-control rule actually asks for."
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI serviceRequestOpenApi() {
        final String bearerScheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("service-request-service API")
                        .description("USM-G7 Sprint 1 - submit, triage, reject/escalate, and confirm "
                                + "service requests. See the group's Sprint 1 Backend Developer Guide for "
                                + "the full spec (§3-§7).")
                        .version("0.1.0-SPRINT1"))
                .addSecurityItem(new SecurityRequirement().addList(bearerScheme))
                .components(new Components().addSecuritySchemes(bearerScheme,
                        new SecurityScheme()
                                .name(bearerScheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
