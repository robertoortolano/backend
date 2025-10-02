package com.example.demo.dto;

import com.example.demo.enums.FieldType;

import java.util.Set;

public record FieldConfigurationUpdateDto (

    String name,
    String description,
    String alias,
    Long fieldId,
    FieldType fieldType,
    Set<FieldOptionUpdateDto> options

) {}
