package com.example.demo.dto;

import com.example.demo.entity.Field;
import com.example.demo.entity.Tenant;
import com.example.demo.enums.FieldType;
import com.example.demo.enums.ScopeType;

import java.util.List;

public record FieldConfigurationCreateParams(
        String name,
        Field field,
        String description,
        FieldType fieldType,
        List<String> optionLabels,
        ScopeType scopeType,
        boolean isDefault,
        Tenant tenant
) {}

