package com.github.t1.openapi.ui.generator;

import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.PathItem.HttpMethod;
import org.eclipse.microprofile.openapi.models.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.models.servers.Server;

import java.util.List;

record Operation(HttpMethod method, org.eclipse.microprofile.openapi.models.Operation spec, ApiPath path, PathItem pathItem, List<Server> globalServers, Components components, List<SecurityRequirement> globalSecurity) {}
