package com.github.t1.openapi.ui.demo;

import jakarta.validation.constraints.NotBlank;

public record Owner(long id, @NotBlank String name, @NotBlank String email) {}
