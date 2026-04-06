package com.github.t1.openapi.ui.generator;

import io.swagger.v3.oas.models.PathItem.HttpMethod;

record Operation(HttpMethod method, io.swagger.v3.oas.models.Operation spec, ApiPath path) {}
