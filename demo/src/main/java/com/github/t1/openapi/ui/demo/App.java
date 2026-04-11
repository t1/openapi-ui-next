package com.github.t1.openapi.ui.demo;

import jakarta.ws.rs.core.Application;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@OpenAPIDefinition(
        info = @Info(
                title = "Pet Store",
                summary = "Demo pet store for the OpenAPI UI generator",
                version = "1.0.0",
                description = "A pet store API with owners, pets, and veterinary visits for demonstrating the OpenAPI UI generator.",
                contact = @Contact(name = "OpenAPI UI", url = "https://github.com/t1/openapi-ui-next")),
        security = @SecurityRequirement(name = "BearerAuth"))
@SecurityScheme(
        securitySchemeName = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        description = "Bearer token authentication")
@SecurityScheme(
        securitySchemeName = "ApiKeyAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        apiKeyName = "X-API-Key",
        description = "API Key authentication")
public class App extends Application {}
