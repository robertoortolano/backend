package com.example.demo.dto;

import com.example.demo.entity.*;
import com.example.demo.enums.ItemTypeCategory;
import com.example.demo.enums.ScopeType;

import java.util.Set;

public record ItemTypeConfigurationViewDto (
    Long id,
    ItemTypeViewDto itemType,
    ItemTypeCategory category,
    boolean defaultItemTypeConfiguration,
    ScopeType scope,
    Set<Role> workers,
    WorkflowViewDto workflow,
    FieldSetViewDto fieldSet
) {}
