package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

public record FieldSetEntryCreateDto (
    @NotNull(message = "Field Configuration id required")
    Long fieldConfigurationId,
    @NotNull(message = "Field Configuration order required")
    int orderIndex

) {}

