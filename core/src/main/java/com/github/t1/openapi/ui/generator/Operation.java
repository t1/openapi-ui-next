package com.github.t1.openapi.ui.generator;

import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.PathItem.HttpMethod;

record Operation(HttpMethod method, org.eclipse.microprofile.openapi.models.Operation spec, ApiPath path, Components components) {}
