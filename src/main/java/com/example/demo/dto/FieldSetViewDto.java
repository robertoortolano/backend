package com.example.demo.dto;

import com.example.demo.enums.ScopeType;

import java.util.List;

public record FieldSetViewDto (
    Long id,
    String name,
    String description,
    ScopeType scope,
    boolean defaultFieldSet,
    List<FieldSetEntryViewDto> fieldSetEntries
) {}

