package com.github.t1.openapi.ui.demo;

import jakarta.ws.rs.core.Application;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@OpenAPIDefinition(info = @Info(
        title = "Pet Store",
        version = "1.0.0",
        description = "A simple pet store API for demonstrating the OpenAPI UI generator."))
public class App extends Application {}
