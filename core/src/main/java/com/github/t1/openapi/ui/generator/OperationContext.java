package com.github.t1.openapi.ui.generator;

import io.swagger.v3.oas.models.PathItem.HttpMethod;

record OperationContext(HttpMethod method, io.swagger.v3.oas.models.Operation operation, String fullPath) {}
