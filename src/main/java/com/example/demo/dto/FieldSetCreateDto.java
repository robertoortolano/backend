package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FieldSetCreateDto (
    @NotBlank(message = "Field Set name required")
    String name,
    String description,
    @Valid
    @NotEmpty(message = "Field Set entries required")
    @NotNull(message = "Field Set entries required")
    List<FieldSetEntryCreateDto> entries

) {}
