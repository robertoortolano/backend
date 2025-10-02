package com.example.demo.dto;

import java.util.List;

public record FieldDetailDto(
        Long id,
        String name,
        boolean defaultField,
        List<FieldConfigurationDto> fieldConfigurations,
        List<FieldSetViewDto> fieldSets
) {}
