package com.example.demo.dto;

import com.example.demo.enums.FieldType;
import com.example.demo.security.ValidFieldOptions;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

@ValidFieldOptions(message = "Field options required")
public record FieldConfigurationCreateDto (

    @NotBlank(message = "Field Configuration name required")
    String name,
    String description,
    String alias,
    @NotNull(message = "Field id required")
    Long fieldId,
    @NotNull(message = "Field Type required")
    FieldType fieldType,
    Set<FieldOptionCreateDto> options

) {}
