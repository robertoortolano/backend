package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record ItemTypeCreateDto (
    @NotBlank(message = "Item Type name required")
    String name
) {}
