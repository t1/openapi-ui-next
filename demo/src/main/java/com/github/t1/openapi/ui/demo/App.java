package com.github.t1.openapi.ui.demo;

import jakarta.ws.rs.core.Application;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@OpenAPIDefinition(info = @Info(
        title = "Pet Store",
        summary = "Demo pet store for the OpenAPI UI generator",
        version = "1.0.0",
        description = "A pet store API with owners, pets, and veterinary visits for demonstrating the OpenAPI UI generator.",
        contact = @Contact(name = "OpenAPI UI", url = "https://github.com/t1/openapi-ui-next")))
public class App extends Application {}
