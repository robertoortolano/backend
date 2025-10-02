package com.example.demo.dto;

import com.example.demo.enums.ScopeType;
import com.example.demo.fieldtype.FieldTypeDescriptor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldConfigurationViewDto (
    Long id,
    String name,
    String description,
    Long fieldId,
    String fieldName,
    String alias,
    boolean defaultFieldConfiguration,
    FieldTypeDescriptor fieldType,
    ScopeType scope,
    Set<FieldOptionViewDto> options,
    List<SimpleFieldSetDto> usedInFieldSets
) {}
