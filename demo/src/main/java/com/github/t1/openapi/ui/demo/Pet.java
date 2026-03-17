package com.github.t1.openapi.ui.demo;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record Pet(
        @Schema(examples = "1") long id,
        @Schema(examples = "Max") String name,
        @Schema(examples = "available", enumeration = {"available", "adopted", "pending"}) String status,
        @Schema(examples = "42") long ownerId) {}
