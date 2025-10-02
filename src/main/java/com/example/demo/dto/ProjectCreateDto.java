package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record ProjectCreateDto (
    @NotBlank(message = "Project key required")
    String key,
    @NotBlank(message = "Project name required")
    String name,
    String description

) {}
