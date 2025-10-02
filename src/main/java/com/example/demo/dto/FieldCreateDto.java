package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record FieldCreateDto (
    @NotBlank(message = "Field name required")
    String name

) {}
