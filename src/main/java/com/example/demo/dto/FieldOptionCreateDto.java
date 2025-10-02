package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FieldOptionCreateDto (
    @NotBlank(message = "Field Option label required")
    String label,
    @NotBlank(message = "Field Option value required")
    String value,
    @NotNull(message = "Field Option order required")
    int orderIndex

) {}
