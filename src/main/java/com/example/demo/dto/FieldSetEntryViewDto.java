package com.example.demo.dto;

public record FieldSetEntryViewDto(
        Long id,
        FieldConfigurationDto fieldConfiguration,
        int orderIndex
        //boolean required,
        //boolean visible
) {}

