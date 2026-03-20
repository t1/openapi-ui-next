package com.github.t1.openapi.ui.demo;

import jakarta.validation.constraints.NotBlank;

public record Visit(long id, long petId, @NotBlank String date, @NotBlank String reason) {}
