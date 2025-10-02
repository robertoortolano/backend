package com.example.demo.dto;

import com.example.demo.fieldtype.FieldTypeDescriptor;

import java.util.Set;

public record FieldConfigurationDto (
    Long id,
    String name,
    String fieldName,
    String alias,
    FieldTypeDescriptor fieldType,
    Set<FieldOptionViewDto> options
) {}
